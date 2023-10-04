(ns biobricks.datalevin.ifc
  (:require [datalevin.core :as dtlv]
            [donut.system :as-alias ds]))

(defn local-db-component
  "Returns a `donut.system` component that opens a database in a directory.

   `opts` can contain `:dir`, `:schema`, and `:conn-opts`.
   These are passed to `datalevin.core/create-conn`
   `:dir` is required."
  [opts]
  {::ds/config opts
   ::ds/start
   (fn [{::ds/keys [instance]
         {:keys [conn-opts dir schema]} ::ds/config
         {:keys [conn]} ::ds/instance}]
     (if conn
       instance
       (assoc instance :conn (dtlv/get-conn dir schema conn-opts))))
   ::ds/stop
   (fn [{::ds/keys [instance] {:keys [conn]} ::ds/instance}]
     (if-not conn
       instance
       (do
         (when-not (dtlv/closed? conn)
           (dtlv/close conn))
         (dissoc instance :conn))))})
