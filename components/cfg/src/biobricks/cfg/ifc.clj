(ns biobricks.cfg.ifc
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [donut.system :as-alias ds])
  (:import [java.io PushbackReader]))

(defn deep-merge [& args]
  (if (every? #(or (map? %) (nil? %)) args)
    (apply merge-with deep-merge args)
    (last args)))

(defn get-config [filename]
  (if-let [resource (io/resource filename)]
    (with-open [reader (-> resource io/reader PushbackReader.)]
      (try
        (edn/read reader)
        (catch Exception e
          (throw (ex-info (str "Error parsing EDN in config file \"" filename
                         \"": " (ex-message e))
                    {:filename filename}
                    e)))))
    (throw (ex-info (str "Config file not found: \"" filename "\"")
              {:filename filename}))))

(defn- start [{::ds/keys [config instance]}]
  (or instance
    (get-config (:resource-name config))))

(defn- stop [_]
  nil)

(defn config [& {:as config}]
  {::ds/config config
   ::ds/start start
   ::ds/stop stop})

(defn ensure-merged-configs [config]
  (if (map? config)
    config
    (apply deep-merge config)))

(defn config-merger [configs]
  {::ds/config configs
   ::ds/start
   (fn [{::ds/keys [config instance]}]
     (or instance (ensure-merged-configs config)))
   ::ds/stop (constantly nil)})
