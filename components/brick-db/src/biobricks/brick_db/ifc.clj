(ns biobricks.brick-db.ifc
  (:require [babashka.fs :as fs]
            [biobricks.brick-repo.ifc :as brick-repo]
            [biobricks.github.ifc :as github]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datalevin.core :as dtlv]
            [donut.system :as-alias ds]
            [medley.core :as me])
  (:import [java.io PushbackReader]))

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
  [{:keys [datalevin-conn github-org-name]}]
  (doseq [{:keys [clone_url created_at description full_name html_url id
                  updated_at]}
            (github/list-org-repos github-org-name)]
    (dtlv/transact! datalevin-conn
                    [(->> #:git-repo{:checked-at (java.util.Date.),
                                     :clone-url clone_url,
                                     :created-at (github/parse-date created_at),
                                     :description description,
                                     :full-name full_name,
                                     :github-id id,
                                     :html-url html_url,
                                     :updated-at (github/parse-date updated_at)}
                          (me/remove-vals nil?))])))

(defn github-poller
  [{:as instance, :keys [github-poll-interval-ms]}]
  (doto (Thread. #(run-every-ms github-poll-interval-ms
                                (fn [] (check-github-repos instance))
                                prn))
    (.setDaemon true)
    .start))

(defonce brick-lock (Object.))

(defn check-bricks
  [{:keys [bricks-path datalevin-conn]}]
  (let
    [git-repos ;; Split into 2 queries due to differing free vars
       (->>
         (dtlv/q
           '[:find (pull ?e [:db/id :git-repo/clone-url :git-repo/full-name])
             :where [?e :git-repo/github-id]
             (or
              [(missing? $ ?e :git-repo/is-biobrick?)]
              (and
               [?e :git-repo/is-biobrick? true]
               [(missing? $ ?e :biobrick/checked-at)]))]
           (dtlv/db datalevin-conn))
         (concat (dtlv/q
                   '[:find
                     (pull ?e [:db/id :git-repo/clone-url :git-repo/full-name])
                     :where [?e :git-repo/is-biobrick? true]
                     [?e :biobrick/checked-at ?checked-at]
                     [?e :git-repo/updated-at ?updated-at]
                     [(.after ?updated-at ?checked-at)]]
                   (dtlv/db datalevin-conn))))]
    (doseq [[{:db/keys [id], :git-repo/keys [clone-url full-name]}] git-repos]
      (locking brick-lock
        (let [path (apply fs/path bricks-path (str/split full-name #"\/"))
              dir (if (fs/exists? path)
                    path
                    (do (fs/create-dirs (fs/parent path))
                        (brick-repo/clone (fs/parent path) clone-url)))
              {:keys [data-bytes file-extensions health-git is-brick?]}
                #__
                (brick-repo/brick-info dir)]
          (if-not is-brick?
            (->> [{:db/id id, :git-repo/is-biobrick? false}]
                 (dtlv/transact! datalevin-conn))
            (->> [(me/remove-vals
                    nil?
                    {:db/id id,
                     :biobrick/checked-at (java.util.Date.),
                     :biobrick/data-bytes data-bytes,
                     :biobrick/health-check-data (pr-str health-git),
                     :biobrick/health-check-failures (->> health-git
                                                          vals
                                                          (remove true?)
                                                          count),
                     :git-repo/is-biobrick? true})]
                 (concat (for [ext file-extensions]
                           {:db/id id, :biobrick/file-extension ext}))
                 (dtlv/transact! datalevin-conn))))))))

(comment
  (def instance
    (-> @biobricks.web-ui.api/system
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

(defn component
  [&
   {:keys [bricks-path datalevin-conn brick-poll-interval-ms github-org-name
           github-poll-interval-ms]}]
  {::ds/config {:brick-poll-interval-ms brick-poll-interval-ms,
                :bricks-path bricks-path,
                :datalevin-conn datalevin-conn,
                :github-org-name github-org-name,
                :github-poll-interval-ms github-poll-interval-ms},
   ::ds/start (fn [{::ds/keys [config instance]}]
                (if (:datalevin-conn instance)
                  instance
                  (-> config
                      (assoc :brick-poller (brick-poller config)
                             :github-poller (github-poller config))))),
   ::ds/stop (fn [{::ds/keys [instance],
                   {:keys [datalevin-conn github-poller]} ::ds/instance}]
               (if-not datalevin-conn
                 instance
                 (do (.interrupt brick-poller)
                     (.interrupt github-poller)
                     (.join brick-poller)
                     (.join github-poller) ;; Wait for the thread to stop
                     (dissoc instance
                       :brick-poll-interval-ms
                       :bricks-path
                       :datalevin-conn
                       :github-org-name
                       :github-poll-interval-ms
                       :github-poller))))})
