(ns biobricks.web-ui.api.ring
  (:require [biobricks.web-ui.api.routes :as routes]
            [biobricks.web-ui.impl.badge :as badge]
            [biobricks.web-ui.impl.webhook :as webhook]
            [clojure.stacktrace :as st]
            [reitit.core :as rr]
            [reitit.ring :as rring]))

; #' syntax allows redefinitions in dev to be picked up instantly
(def handlers
  {:brick-badge-health #'badge/health
   :webhook-github-push #'webhook/github-push})

(defn wrap-routes [f]
  (let [router (rring/router routes/routes)]
    (fn [request]
      (try
        (let [match (rr/match-by-path router (:uri request))
              handler (-> match :data :name handlers)
              request (assoc request :match match)]
          (or
            (when handler (handler request))
            (f request)))
        (catch Throwable e
          (print "Error in ring handler")
          (st/print-throwable e)
          (st/print-cause-trace e)
          (throw e))))))

(defn wrap-instance [f instance]
  (fn [request]
    (f (assoc request :instance instance))))
