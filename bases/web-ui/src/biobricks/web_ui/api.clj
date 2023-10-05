(ns biobricks.web-ui.api
  (:require [hashp.core]
            [biobricks.brick-db.ifc :as brick-db]
            [biobricks.datalevin.ifc :as datalevin]
            [biobricks.electric-jetty.ifc :as electric-jetty]
            [biobricks.sys.ifc :as sys]
            [clojure.java.io :as io]
            [donut.system :as ds]
            [shadow.cljs.devtools.config :as shadow-config]))

(defonce system (atom nil))

(defn system-def
  []
  {::ds/defs
     {:brick-data
        {:brick-db (brick-db/component
                     {:datalevin-conn (ds/local-ref [:local-datalevin :conn]),
                      :github-org-name "biobricks-ai",
                      :github-poll-interval-ms (* 1000 60 5)}),
         :datalevin-schema (sys/thunk-component brick-db/datalevin-schema),
         :local-datalevin (datalevin/local-db-component
                            {:dir "datalevin",
                             :schema (ds/local-ref [:datalevin-schema])})},
      :web-ui {:app (sys/config-component
                      {:brick-db (ds/ref [:brick-data :brick-db]),
                       :datalevin-conn (ds/ref [:brick-data :local-datalevin
                                                :conn])})}}})

;; Technically blocking during swap! can be bad, but in this particular
;; case it's never a problem. Using swap! here enforces a
;; serialization of state operations.

(defn start-system! [] (swap! system #(sys/start! (or % (system-def)))))

(defn stop-system! [] (swap! system sys/stop!))

(defn restart-system!
  []
  (swap! system #(do (when % (sys/stop! %)) (sys/start! (system-def)))))

;; Adapted from shadow.cljs.devtools.config/load-cljs-edn
(defn load-cljs-edn
  []
  (let [file (io/file "bases/web-ui/shadow-cljs.edn")]
    (if-not (.exists file)
      shadow-config/default-config
      (-> (shadow-config/read-config file)
          (shadow-config/normalize)
          (->> (merge shadow-config/default-config))
          (update :builds #(merge shadow-config/default-builds %))
          (assoc :user-config (shadow-config/load-user-config))))))

; lazy load dev stuff - for faster REPL startup and cleaner dev classpath
(def shadow-start!
  (delay @(requiring-resolve 'shadow.cljs.devtools.server/start!)))
(def shadow-watch (delay @(requiring-resolve 'shadow.cljs.devtools.api/watch)))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(defn main
  [& _args]
  (println "Starting Electric compiler and server...")
  (@shadow-start! (load-cljs-edn)) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  (start-system!)
  ; Shadow loads the app here, such that it shares memory with server.
  (def server (electric-jetty/start-server! electric-server-config))
  (comment
    (.stop server)))

; Server-side Electric userland code is lazy loaded by the shadow build.
; WARNING: make sure your REPL and shadow-cljs are sharing the same JVM!

(comment
  (main) ; Electric Clojure(JVM) REPL entrypoint
  (hyperfiddle.rcf/enable!) ; turn on RCF after all transitive deps have loaded
  (shadow.cljs.devtools.api/repl :dev) ; shadow server hosts the cljs repl
  ; connect a second REPL instance to it
  ; (DO NOT REUSE JVM REPL it will fail weirdly)
  (type 1))
