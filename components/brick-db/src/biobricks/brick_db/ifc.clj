(ns biobricks.brick-db.ifc
  (:require [babashka.fs :as fs]
            [biobricks.brick-repo.ifc :as brick-repo]
            [biobricks.github.ifc :as github]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datalevin.core :as dtlv]
            [donut.system :as-alias ds])
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
    (prn :start start)
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
  (doseq [{:keys [created_at full_name html_url id updated_at]}
            (github/list-org-repos github-org-name)]
    (dtlv/transact! datalevin-conn
                    [#:git-repo{:checked-at (java.util.Date.),
                                :created-at (github/parse-date created_at),
                                :full-name full_name,
                                :github-id id,
                                :html-url html_url,
                                :updated-at (github/parse-date updated_at)}])))

(defn github-poller
  [{:as instance, :keys [github-poll-interval-ms]}]
  (doto (Thread. #(run-every-ms github-poll-interval-ms
                                (fn [] (check-github-repos instance))
                                prn))
    (.setDaemon true)
    .start))

(defn component
  [& {:keys [datalevin-conn github-org-name github-poll-interval-ms]}]
  {::ds/config {:datalevin-conn datalevin-conn,
                :github-org-name github-org-name,
                :github-poll-interval-ms github-poll-interval-ms},
   ::ds/start (fn [{::ds/keys [config instance]}]
                (if (:datalevin-conn instance)
                  instance
                  (-> config
                      (assoc :github-poller (github-poller config))))),
   ::ds/stop (fn [{::ds/keys [instance],
                   {:keys [datalevin-conn github-poller]} ::ds/instance}]
               (if-not datalevin-conn
                 instance
                 (do (.interrupt github-poller)
                     (.join github-poller) ;; Wait for the thread to stop
                     (dissoc instance
                       :datalevin-conn
                       :github-org-name
                       :github-poll-interval-ms
                       :github-poller))))})
