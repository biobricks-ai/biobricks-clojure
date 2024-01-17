(ns biobricks.web-ui.impl.webhook 
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [datalevin.core :as d]))

(defn github-push [{:keys [body instance]}]
  (let [{:keys [datalevin-conn]} instance
        {:keys [pushed_at repository]} (-> body io/reader (json/read :key-fn keyword))]
    (d/transact! datalevin-conn
      [{:git-repo/github-id (:id repository)
        :git-repo/updated-at (java.util.Date. (* 1000 pushed_at))}])
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body "{\"success\":true}"}))
