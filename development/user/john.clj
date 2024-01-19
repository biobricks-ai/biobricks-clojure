(ns user.john
  (:require [biobricks.brick-db.ifc :as brick-db]
            biobricks.web-ui.system
            [clojure.pprint :as pp]
            [datalevin.core :as dtlv]))

; Test GitHub push webhook
; curl -X POST http://localhost:4071/webhook/github/push      -H "Content-Type: application/json"      -d '{"repository":{"id":545044892,"pushed_at":1805529242}}'

(comment
  (do
    (def web-ui-app (-> @biobricks.web-ui.system/system
                      :donut.system/instances
                      :web-ui :app))
    (def brick-db (-> @biobricks.web-ui.system/system
                    :donut.system/instances
                    :brick-data :brick-db))
    (def datalevin-conn (:datalevin-conn web-ui-app)))
  (brick-db/check-github-repos brick-db)
  (brick-db/check-bricks brick-db)
  (brick-db/check-brick-by-id brick-db 59)

  ; Pull brick data
  (dtlv/q
    '[:find
      (pull ?e [*])
      :where [?e :git-repo/full-name "biobricks-ai/chemharmony"]]
    (dtlv/db datalevin-conn))
  ; Pull brick files
  (->> (dtlv/q
         '[:find
           (pull ?e [*])
           :where [?brick :git-repo/full-name "biobricks-ai/chemharmony"]
           [?e :biobrick-file/biobrick ?brick]]
         (dtlv/db datalevin-conn))
    flatten
    (map (juxt :biobrick-file/path :biobrick-file/git-sha))
    sort
    pp/pprint))

