(ns biobricks.log.ifc
  "Logging output.

  We have had issues with logback previously. Using the macros here
  makes it easy to change the implementation if needed."
  (:require [clojure.tools.logging.readable :as logr]))

(defmacro debug
  [& args]
  `(logr/debug ~@args))

(defmacro debugf
  [& args]
  `(logr/debugf ~@args))

(defmacro error
  [& args]
  `(logr/error ~@args))

(defmacro errorf
  [& args]
  `(logr/errorf ~@args))

(defmacro info
  [& args]
  `(logr/info ~@args))

(defmacro infof
  [& args]
  `(logr/infof ~@args))

(defmacro trace
  [& args]
  `(logr/trace ~@args))

(defmacro tracef
  [& args]
  `(logr/tracef ~@args))

(defmacro warn
  [& args]
  `(logr/warn ~@args))

(defmacro warnf
  [& args]
  `(logr/warnf ~@args))
