(ns biobricks.datalevin.ifc-test
  (:require [babashka.fs :as fs]
            [biobricks.datalevin.ifc :as datalevin]
            [biobricks.sys.ifc :as sys]
            [clojure.test :as test :refer [deftest is]]
            [datalevin.core :as dtlv]
            [donut.system :as-alias ds]
            [medley.core :as me]))

(deftest test-local-db-component
  (fs/with-temp-dir [dir {:prefix "biobricks-test"}]
    (let [schema {:aka {:db/cardinality :db.cardinality/many},
                  :name {:db/valueType :db.type/string,
                         :db/unique :db.unique/identity}}
          local-db (datalevin/local-db-component
                     {:dir (str dir), :schema schema})
          sys (-> {::ds/defs {:datalevin {:local-db local-db}}}
                sys/start!)
          conn (-> sys
                 ::ds/instances
                 :datalevin
                 :local-db
                 :conn)]
      (try (is (dtlv/conn? conn)
             "Component creates a connection to local db")
        (is (= schema
              (-> (dtlv/schema conn)
                (select-keys [:aka :name])
                (->> (me/map-vals #(dissoc % :db/aid)))))
          "Local db has the provided schema")
        (finally (sys/stop! sys))))))
