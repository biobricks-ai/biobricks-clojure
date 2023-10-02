(ns biobricks.web-ui.app
  (:require [contrib.str :refer [empty->nil]]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

(e/defn App []
  (e/client
   (dom/div
    (dom/text ""))))
