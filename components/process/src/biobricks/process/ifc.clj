(ns biobricks.process.ifc
  (:require [babashka.process :as p]))

(def default-opts {:err :inherit, :in nil, :out :inherit})

(defn process [opts & args] (apply p/process (merge default-opts opts) args))

(defn maybe-deref [x] (if (instance? clojure.lang.IDeref x) @x x))

(defn throw-on-error
  [process]
  (let [{:as proc-map, :keys [exit]} (maybe-deref process)]
    (if (zero? exit)
      proc-map
      (throw (ex-info (format "Unexpected exit code: %s" exit)
                      {:process proc-map})))))
