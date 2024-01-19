(ns biobricks.nrepl.ifc
  (:require [cider.nrepl.middleware]
            [clojure.tools.logging :as log]
            [donut.system :as-alias ds]
            nrepl.server
            refactor-nrepl.middleware)
  (:import (java.net ServerSocket)))

(def ^:private nrepl-handler
  (apply nrepl.server/default-handler
    (conj cider.nrepl.middleware/cider-middleware
      #'refactor-nrepl.middleware/wrap-refactor)))

(def ^:private default-config
  {:handler nrepl-handler
   :host "localhost"
   :port 0})

(defn- start! [{::ds/keys [config instance]}]
  (or instance
    (let [{:keys [handler host port]} (merge default-config config)
          server (nrepl.server/start-server
                   :bind host
                   :handler handler
                   :port port)
          bound-port (.getLocalPort ^ServerSocket (:server-socket server))]
      (log/info "Started nREPL server on port" bound-port)
      {:port bound-port :server server})))

(defn- stop! [{::ds/keys [instance]}]
  (when instance
    (log/info "Stopped nREPL server")
    (nrepl.server/stop-server (:server instance))
    nil))

(defn server [config]
  {::ds/config config
   ::ds/start start!
   ::ds/stop stop!})
