(ns biobricks.web-ui.prod
  (:gen-class)
  (:require [biobricks.electric-jetty.ifc :as electric-jetty]
            biobricks.web-ui.app
            [biobricks.web-ui.system :as system]
            clojure.string))

(def electric-server-config
  {:host "0.0.0.0", :port 4070, :resources-path "public"})

(defn -main
  [& args] ; run with `clj -M -m prod`
  (when (clojure.string/blank? (System/getProperty
                                 "HYPERFIDDLE_ELECTRIC_SERVER_VERSION"))
    (throw
      (ex-info
        "HYPERFIDDLE_ELECTRIC_SERVER_VERSION jvm property must be set in prod"
        {})))
  (system/start-system!)
  (electric-jetty/start-server! electric-server-config))

; On CLJS side we reuse api.cljs for prod entrypoint