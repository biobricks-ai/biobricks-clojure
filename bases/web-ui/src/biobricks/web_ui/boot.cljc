(ns biobricks.web-ui.boot
  (:require [biobricks.web-ui.app :as app]
            [hyperfiddle.electric :as e]))

#?(:clj (defn with-ring-request [_ring-req]
          (e/boot-server {} app/App)))

#?(:cljs (def client
           (e/boot-client {} app/App)))
