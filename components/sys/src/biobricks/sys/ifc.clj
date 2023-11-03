(ns biobricks.sys.ifc
  (:require [clojure.string :as str]
            [donut.system :as ds]))

;; From
;; https://github.com/john-shaffer/salmon/blob/30143bc5f7ac5659f22c4905c2d9fe727dbc6d64/src/salmon/signal.clj

(defn- first-line
  [s]
  (if (string? s)
    (some-> s
      not-empty
      (str/split #"\n")
      first)
    (pr-str s)))

(defn signal!
  "Sends the signal to the system. Throws a
   clojure.lang.ExceptionInfo` if there are any messages on the error or
   validation channels. Otherwise, returns the result of
   `(donut.system/signal system signal)`."
  [system signal-name]
  (let [{out ::ds/out, :as system} (ds/signal system signal-name)
        {:keys [error validation]} out]
    (cond (seq error) (throw (ex-info (str "Error during "
                                        signal-name
                                        (some->> error
                                          first
                                          val
                                          first
                                          val
                                          :message
                                          first-line
                                          (str ": ")))
                               out))
      (seq validation) (throw (ex-info (str "Validation failed during "
                                         signal-name
                                         (some->> validation
                                           first
                                           val
                                           first
                                           val
                                           :message
                                           first-line
                                           (str ": ")))
                                out))
      :else system)))

(defn start!
  "Calls `(signal! system :donut.system/start)`."
  [system]
  (signal! system :donut.system/start))

(defn stop!
  "Calls `(signal! system :donut.system/stop)`."
  [system]
  (signal! system :donut.system/stop))

(defn config-component
  "Returns a component whose instance is the same as its config,
   but with refs resolved. The instance is nil when stopped."
  [config]
  {::ds/config config,
   ::ds/start (fn [{::ds/keys [config]}] config),
   ::ds/stop (fn [_] nil)})

(defn thunk-component
  "Returns a component that calls a thunk on start and sets the return
   value as the instance.

   Useful for loading configuration data."
  [f]
  {::ds/config {:f f},
   ::ds/start (fn [{::ds/keys [instance], {:keys [f]} ::ds/config}]
                (or instance (f))),
   ::ds/stop (fn [_] nil)})
