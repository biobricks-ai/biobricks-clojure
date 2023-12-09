(ns ^:dev/always biobricks.web-ui.api
  (:require [biobricks.web-ui.boot :as boot]))

(defonce reactor nil)

(defn ^:dev/after-load ^:export start!
  []
  (assert (nil? reactor) "reactor already running")
  (set! reactor
    (boot/client #(js/console.log "Reactor success:" %)
      #(js/console.error "Reactor failure:" %))))

(defn ^:dev/before-load stop!
  []
  (when reactor (reactor)) ; teardown
  (set! reactor nil))
