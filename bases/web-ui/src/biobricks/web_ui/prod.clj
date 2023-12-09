(ns biobricks.web-ui.prod
  (:gen-class)
  (:require [biobricks.electric-jetty.ifc :as electric-jetty]
            [biobricks.web-ui.api.ring :as ring]
            [biobricks.web-ui.boot :as boot]
            [biobricks.web-ui.system :as system]
            clojure.string))

(def electric-server-config
  {:extra-middleware [ring/wrap-routes]
   :host "0.0.0.0"
   :port 4070
   :resources-path "public"})

(defn -main
  [& _args] ; run with `clj -M -m prod`
  (when (clojure.string/blank? (System/getProperty
                                 "HYPERFIDDLE_ELECTRIC_SERVER_VERSION"))
    (throw
      (ex-info
        "HYPERFIDDLE_ELECTRIC_SERVER_VERSION jvm property must be set in prod"
        {})))
  (let [system (system/start-system!)
        instance (-> system :donut.system/instances :web-ui :app)]
    (-> electric-server-config
      (assoc :extra-middleware [ring/wrap-routes
                                #(ring/wrap-instance % instance)])
      (->> (electric-jetty/start-server! boot/with-ring-request)))))

; On CLJS side we reuse api.cljs for prod entrypoint
