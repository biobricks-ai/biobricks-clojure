(ns biobricks.web-ui.api
  (:require [hashp.core]
            [clojure.java.io :as io]
            [shadow.cljs.devtools.config :as shadow-config]))

;; Adapted from shadow.cljs.devtools.config/load-cljs-edn
(defn load-cljs-edn []
  (let [file (io/file "bases/web-ui/shadow-cljs.edn")]
    (if-not (.exists file)
      shadow-config/default-config
      (-> (shadow-config/read-config file)
          (shadow-config/normalize)
          (->> (merge shadow-config/default-config))
          (update :builds #(merge shadow-config/default-builds %))
          (assoc :user-config (shadow-config/load-user-config))))))

; lazy load dev stuff - for faster REPL startup and cleaner dev classpath
(def start-electric-server! (delay @(requiring-resolve 'biobricks.electric-jetty.ifc/start-server!)))
(def shadow-start! (delay @(requiring-resolve 'shadow.cljs.devtools.server/start!)))
(def shadow-watch (delay @(requiring-resolve 'shadow.cljs.devtools.api/watch)))

(def electric-server-config
  {:host "0.0.0.0", :port 8080, :resources-path "public"})

(defn main [& _args]
  (println "Starting Electric compiler and server...")
  (@shadow-start! (load-cljs-edn)) ; serves index.html as well
  (@shadow-watch :dev) ; depends on shadow server
  ; Shadow loads the app here, such that it shares memory with server.
  (def server (@start-electric-server! electric-server-config))
  (comment (.stop server)))

; Server-side Electric userland code is lazy loaded by the shadow build.
; WARNING: make sure your REPL and shadow-cljs are sharing the same JVM!

(comment
  (main) ; Electric Clojure(JVM) REPL entrypoint
  (hyperfiddle.rcf/enable!) ; turn on RCF after all transitive deps have loaded
  (shadow.cljs.devtools.api/repl :dev) ; shadow server hosts the cljs repl
  ; connect a second REPL instance to it
  ; (DO NOT REUSE JVM REPL it will fail weirdly)
  (type 1))
