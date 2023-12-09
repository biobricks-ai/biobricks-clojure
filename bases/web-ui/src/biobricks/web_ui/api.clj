(ns biobricks.web-ui.api
  (:require hashp.core
            [biobricks.electric-jetty.ifc :as electric-jetty]
            [biobricks.web-ui.api.ring :as ring]
            [biobricks.web-ui.boot :as boot]
            [biobricks.web-ui.system :as system]
            [clojure.java.io :as io]
            [shadow.cljs.devtools.config :as shadow-config]))

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
  {:host "0.0.0.0"
   :port 4071
   :resources-path "public"})

(defn main
  [& _args]
  (println "Starting Electric compiler and server...")
  (@shadow-start! (load-cljs-edn)) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  (let [system (system/start-system!)
        instance (-> system :donut.system/instances :web-ui :app)]
  ; Shadow loads the app here, such that it shares memory with server.
    (def server (-> electric-server-config
                  (assoc :extra-middleware [ring/wrap-routes
                                            #(ring/wrap-instance % instance)])
                  (->> (electric-jetty/start-server! boot/with-ring-request)))))
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
