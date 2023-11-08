(ns biobricks.web-ui.impl.badge
  (:require [clojure.java.io :as io]
            [datalevin.core :as d]))

(defn health [{:keys [instance match]}]
  (let [{:keys [brick-name org-name]} (:path-params match)
        db (d/db (-> instance :datalevin-conn))
        brick (-> (d/q '[:find (pull ?e [:db/id :biobrick/health-check-failures])
                         :in $ ?name
                         :where [?e :git-repo/full-name ?name]]
                    db
                    (str org-name "/" brick-name))
                ffirst)
        healthy? (some-> brick :biobrick/health-check-failures zero?)
        files (when brick
                (->> (d/q '[:find (pull ?e [*])
                            :in $ ?id
                            :where [?e :biobrick-file/biobrick ?id]]
                       db
                       (:db/id brick))
                  (map first)))
        healthy? (and healthy? (not (some :biobrick-file/missing? files)))
        svg-file (if healthy?
                   "public/img/biobricks-healthy.svg"
                   "public/img/biobricks-unhealthy.svg")]
    (when brick
      {:status 200
       :headers {"Content-Type" "image/svg+xml"}
       :body (-> svg-file io/resource io/file)})))
