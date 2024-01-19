(ns biobricks.web-ui.system
  (:require [biobricks.brick-db.ifc :as brick-db]
            [biobricks.cfg.ifc :as cfg]
            [biobricks.datalevin.ifc :as datalevin]
            [biobricks.github.ifc :as github]
            [biobricks.nrepl.ifc :as nrepl]
            [biobricks.sys.ifc :as sys]
            [clojure.stacktrace :as st]
            [donut.system :as ds]))

(defn uncaught-exception-handler
  []
  (proxy [Thread$UncaughtExceptionHandler] []
    (uncaughtException [^Thread _thread ^Throwable e]
      (print "Uncaught exception ")
      (st/print-throwable e)
      (st/print-cause-trace e))))

(Thread/setDefaultUncaughtExceptionHandler (uncaught-exception-handler))

(defonce system (atom nil))

(defn system-def
  []
  {::ds/defs
   {:brick-data
    {:brick-db (brick-db/component (ds/local-ref [:brick-db-config]))
     :brick-db-config (cfg/config-merger
                        [(ds/ref [:config :final :brick-db])
                         {:github-token (github/get-token-from-env)
                          :datalevin-conn (ds/local-ref [:local-datalevin :conn])}])
     :datalevin-schema (sys/thunk-component brick-db/datalevin-schema),
     :local-datalevin (datalevin/local-db-component
                        {:dir "datalevin",
                         :schema (ds/local-ref [:datalevin-schema])})},
    :config
    {:base (cfg/config {:resource-name "web-ui-config.edn"})
     :default (cfg/config {:resource-name "default-web-ui-config.edn"})
     :final (cfg/config-merger
              [(ds/local-ref [:default])
               (ds/local-ref [:base])])}
    :nrepl
    {:server (nrepl/server (ds/ref [:config :final :nrepl]))}
    :web-ui {:app (sys/config-component
                    {:brick-db (ds/ref [:brick-data :brick-db]),
                     :datalevin-conn (ds/ref [:brick-data :local-datalevin
                                              :conn]),
                     :debug? false})}}})

;; Technically blocking during swap! can be bad, but in this particular
;; case it's never a problem. Using swap! here enforces a
;; serialization of state operations.

(defn start-system! [] (swap! system #(sys/start! (or % (system-def)))))

(defn stop-system! [] (swap! system sys/stop!))

(defn restart-system!
  []
  (swap! system #(do (when % (sys/stop! %)) (sys/start! (system-def)))))
