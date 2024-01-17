(ns biobricks.brick-db.ifc
  (:require [babashka.fs :as fs]
            [biobricks.brick-repo.ifc :as brick-repo]
            [biobricks.github.ifc :as github]
            [biobricks.log.ifc :as log]
            [biobricks.process.ifc :as p]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datalevin.core :as dtlv]
            [donut.system :as-alias ds]
            [hato.client :as hc]
            [medley.core :as me])
  (:import [java.io PushbackReader]))

(defonce brick-locks (atom {}))

(defn get-lock! [name]
  (or (get @brick-locks name)
    (-> (swap! brick-locks assoc name #(or % (Object.)))
      (get name))))

(defn datalevin-schema
  []
  (-> "brick-db/datalevin-schema.edn"
    io/resource
    io/reader
    PushbackReader.
    edn/read))

(defn- run-every-ms
  [interval-ms f ex-handler]
  (loop [start (System/nanoTime)] ;; Use a monotonically increasing timer
    (try (f) (catch Exception e (ex-handler e)))
    (let [end (System/nanoTime)
          runtime-ms (quot (- end start) 1000000)
          sleep-for (- interval-ms runtime-ms)]
      (cond (.isInterrupted (Thread/currentThread)) nil
        (pos? sleep-for)
              ;; Minimize drift by re-calculating what start time should be.
              ;; Otherwise variations in actual sleep times will cause drift.
        (when (try (Thread/sleep sleep-for)
                :recur
                (catch InterruptedException _))
          (recur (+ start (* 1000000 interval-ms))))
            ;; If the task took longer than the interval, we just run it
            ;; right away.
        :else (recur end)))))

(defn check-github-repos
  [{:keys [datalevin-conn github-org-name github-token]}]
  (doseq [{:keys [archived clone_url created_at description
                  full_name html_url id]}
          (github/list-org-repos {:token github-token} github-org-name)]
    (dtlv/transact! datalevin-conn
      [(->> #:git-repo{:archived? (boolean archived)
                       :checked-at (java.util.Date.),
                       :clone-url clone_url,
                       :created-at (github/parse-date created_at),
                       :description description,
                       :full-name full_name,
                       :github-id id,
                       :html-url html_url}
         (me/remove-vals nil?))])))

(defn github-poller
  [{:as instance, :keys [github-poll-interval-ms]}]
  (doto (Thread. #(run-every-ms github-poll-interval-ms
                    (fn [] (check-github-repos instance))
                    prn))
    (.setDaemon true)
    .start))

(defn get-disk-free-space
  [path]
  (-> (java.nio.file.Files/getFileStore path)
    .getUsableSpace))

(defonce brick-data-lock (Object.))

(defn get-biobrick-file-datoms*
  [dir brick-id brick-config file-specs]
  (let [git-sha (brick-repo/git-sha dir)]
    (->> (for [{:as file-spec, :keys [hash md5 path size]} file-specs
               :let [dvc-url (brick-repo/download-url brick-config
                               md5
                               :old-cache-location?
                               (not hash))
                     dir? (str/ends-with? md5 ".dir")
                     biobrick-file {:biobrick-file/biobrick brick-id,
                                    :biobrick-file/biobrick+dvc-url [brick-id
                                                                     dvc-url],
                                    :biobrick-file/directory? dir?,
                                    :biobrick-file/dvc-url dvc-url,
                                    :biobrick-file/extension (fs/extension path),
                                    :biobrick-file/git-sha git-sha
                                    :biobrick-file/path path,
                                    :biobrick-file/size size}]]
           (try (hc/head dvc-url)
             (if dir?
               (get-biobrick-file-datoms*
                 dir
                 brick-id
                 brick-config
                 (brick-repo/resolve-dirs brick-config [file-spec]))
               [(assoc biobrick-file :biobrick-file/missing? false)])
             (catch Exception e
               (if (= "status: 404" (ex-message e))
                 [(assoc biobrick-file :biobrick-file/missing? true)]
                 (throw e)))))
      (apply concat)
      (keep #(when (seq %) (me/remove-vals nil? %))))))

(defn get-biobrick-file-datoms
  [dir brick-id]
  (get-biobrick-file-datoms* dir brick-id
    (brick-repo/brick-config dir)
    (brick-repo/brick-data-file-specs dir)))

(defn check-brick-data
  [{:keys [datalevin-conn maintain-disk-free-bytes pull-dvc-data?]} dir id]
  (let [file-datoms (get-biobrick-file-datoms dir id)]
    (when (seq file-datoms)
      (dtlv/transact! datalevin-conn file-datoms)))
  (when pull-dvc-data?
    (locking brick-data-lock
      (when (< maintain-disk-free-bytes
              (- (get-disk-free-space dir) (brick-repo/pull-data-bytes dir)))
        (-> @(p/process {:dir (fs/file dir), :err :string, :out :string}
               "dvc" "pull"
               "-j" "4")
          p/throw-on-error)
        (dtlv/transact! datalevin-conn
          [{:db/id id, :biobrick/data-pulled? true}])))))

(defn check-brick
  [{:as instance, :keys [bricks-path datalevin-conn]}
   {:db/keys [id], :git-repo/keys [clone-url full-name]}]
  (log/info "Checking brick" full-name)
  (locking (get-lock! full-name)
    (log/info "Acquired lock for" full-name)
    (let [path (apply fs/path bricks-path (str/split full-name #"\/"))
          dir (if (fs/exists? path)
                (let [branch (-> @(p/process {:dir (fs/file path) :err :string :out :string}
                                    "git" "rev-parse" "--abbrev-ref" "HEAD")
                               :out
                               str/trim)]
                  ; This avoids problems if the repo was force-pushed to since
                  ; the last pull.
                  @(p/process {:dir (fs/file path), :err :string, :out :string}
                     "git" "reset" "--hard" (str "origin/" branch))
                  path)
                (do (fs/create-dirs (fs/parent path))
                  (brick-repo/clone (fs/parent path) clone-url)))
          git-sha (brick-repo/git-sha dir)
          commit-time (brick-repo/git-unix-commit-time dir git-sha)
          {:keys [data-bytes health-git is-brick?]} (brick-repo/brick-info dir)]
      (if-not is-brick?
        (->> [{:db/id id, :git-repo/is-biobrick? false}]
          (dtlv/transact! datalevin-conn))
        (do (->> [(me/remove-vals
                    nil?
                    {:db/id id,
                     :biobrick/checked-at (java.util.Date.),
                     :biobrick/data-bytes data-bytes,
                     :biobrick/health-check-data (pr-str health-git),
                     :biobrick/health-check-failures (->> health-git
                                                       vals
                                                       (remove true?)
                                                       count),
                     :git-repo/git-sha-latest git-sha
                     :git-repo/is-biobrick? true
                     :git-repo/updated-at (java.util.Date. (* commit-time 1000))})]
              (dtlv/transact! datalevin-conn))
          (future (check-brick-data instance dir id)))))))

(defn check-brick-by-id
  [{:as instance, :keys [datalevin-conn]} id]
  (check-brick instance
    (dtlv/pull (dtlv/db datalevin-conn)
      [:db/id :git-repo/clone-url :git-repo/full-name]
      id)))

(defn check-bricks
  [{:as instance, :keys [datalevin-conn]}]
  (let
    [git-repos ;; Split into 2 queries due to differing free vars
     (->>
       (dtlv/q
         '[:find (pull ?e [:db/id :git-repo/clone-url :git-repo/full-name])
           :where [?e :git-repo/github-id]
           (or
             [(missing? $ ?e :biobrick/checked-at)]
             [(missing? $ ?e :biobrick/data-pulled?)]
             [?e :biobrick/data-pulled? false])]
         (dtlv/db datalevin-conn))
       (concat (dtlv/q
                 '[:find
                   (pull ?e [:db/id :git-repo/clone-url :git-repo/full-name])
                   :where [?e :biobrick/checked-at ?checked-at]
                   [?e :git-repo/updated-at ?updated-at]
                   [(.after ?updated-at ?checked-at)]]
                 (dtlv/db datalevin-conn))))]
    (doseq [[git-repo] git-repos]
      ; Limit operations to every 15 seconds to avoid hitting rate limits
      (let [start (System/nanoTime)]
        (try
          (check-brick instance git-repo)
          (catch Exception e
            (log/error e "Error checking brick" (:get-repo/full-name git-repo))))
        (let [sleep-ms (- 15000 (quot (- (System/nanoTime) start) 1000000))]
          (when (< 100 sleep-ms)
            (Thread/sleep sleep-ms)))))))

(comment
  (def instance
    (-> @biobricks.web-ui.system/system
      :donut.system/instances
      :brick-data
      :brick-db))
  (check-github-repos instance)
  (check-bricks instance))

(defn brick-poller
  [{:as instance, :keys [brick-poll-interval-ms]}]
  (doto (Thread. #(run-every-ms brick-poll-interval-ms
                    (fn [] (check-bricks instance))
                    prn))
    (.setDaemon true)
    .start))

(defn check-file
  [{:keys [datalevin-conn]}
   {:db/keys [id], :biobrick-file/keys [dvc-url path]}]
  (log/infof "Fetching size of %s %s" dvc-url path)
  (when-let [size (some-> (hc/head dvc-url)
                    :headers
                    (get "content-length")
                    parse-long)]
    (log/infof "Fetched size of %s (%s)" dvc-url size)
    (dtlv/transact! datalevin-conn
      [{:db/id id :biobrick-file/size size}])))

(defn check-files
  [{:as instance, :keys [datalevin-conn]}]
  (let
    [files (dtlv/q
             '[:find (pull ?e [:db/id :biobrick-file/dvc-url :biobrick-file/path])
               :where [?e :biobrick-file/biobrick]
               [(missing? $ ?e :biobrick-file/size)]
               (or
                 [(missing? $ ?e :biobrick-file/missing?)]
                 [?e :biobrick-file/missing? false])]
             (dtlv/db datalevin-conn))]
    (doseq [[file] files] (check-file instance file))))

(defn file-poller
  [{:as instance, :keys [file-poll-interval-ms]}]
  (doto (Thread. #(run-every-ms file-poll-interval-ms
                    (fn [] (check-files instance))
                    prn))
    (.setDaemon true)
    .start))

(defn component
  [config]
  {::ds/config config,
   ::ds/start (fn [{::ds/keys [config instance]}]
                (or instance
                  (-> config
                    (assoc :brick-poller (brick-poller config)
                      :file-poller (file-poller config)
                      :github-poller (github-poller config))))),
   ::ds/stop (fn [{::ds/keys [instance],
                   {:keys [brick-poller github-poller]} ::ds/instance}]
               (when instance
                 (.interrupt brick-poller)
                 (.interrupt github-poller)
                 (.join brick-poller)
                 (.join github-poller) ;; Wait for the thread to stop
                 nil))})
