(ns biobricks.web-ui.impl.webhook 
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [datalevin.core :as d]))

(defn github-push [{:keys [body instance]}]
  (let [{:keys [datalevin-conn]} instance
        {:keys [repository]} (-> body io/reader (json/read :key-fn keyword))
        {:keys [id pushed_at]} repository]
    (d/transact! datalevin-conn
      [{:git-repo/github-id id
        :git-repo/updated-at (java.util.Date. (* 1000 pushed_at))}])
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body "{\"success\":true}"}))
