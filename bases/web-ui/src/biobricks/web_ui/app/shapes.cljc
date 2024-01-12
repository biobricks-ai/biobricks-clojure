(ns biobricks.web-ui.app.shapes
  (:require [clojure.string :as str]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-svg :as svg]
            [reitit.core :as rr]))

(e/defn StatusCircle
  "Shows a green circle for true status, rose for false,
   and gray for nil."
  [status]
  (e/client
    (dom/div
      (dom/props
        {:class (case status
                  nil "flex-none rounded-full p-1 text-gray-500 bg-gray-100/10"
                  true "flex-none rounded-full p-1 text-green-400 bg-green-400/10"
                  false
                  "flex-none rounded-full p-1 text-rose-400 bg-rose-400/10")})
      (dom/div (dom/props {:class "h-2 w-2 rounded-full bg-current"})))))

(e/defn DotDivider
  []
  (e/client
    (svg/svg (dom/props {:viewBox "0 0 2 2",
                         :class "h-0.5 w-0.5 flex-none fill-gray-300"})
      (svg/circle (dom/props {:cx "1", :cy "1", :r "1"})))))

(e/defn RoundedBadge
  [colors label]
  (e/client
    (dom/div
      (dom/props
        {:class
         (str
           "rounded-full flex-none py-1 px-2 text-xs font-medium ring-1 ring-inset "
           colors)})
      (dom/text label))))

(e/defn FileExtensionBadge
  [ext]
  (case ext
    "hdt" (RoundedBadge. "text-amber-400 bg-amber-400/10 ring-amber-400/30"
            "HDT")
    "parquet" (RoundedBadge.
                "text-indigo-400 bg-indigo-400/10 ring-indigo-400/30"
                "Parquet")
    "sqlite" (RoundedBadge.
               "text-cyan-400 bg-cyan-400/10 ring-cyan-400/30"
               "SQLite")
    (RoundedBadge. "text-gray-400 bg-gray-400/10 ring-gray-400/30" ext)))

(e/defn ChevronBase
  [style on-click]
  (e/client
    (svg/svg
      (dom/props {:class "h-5 w-5 flex-none text-gray-400",
                  :viewBox "0 0 20 20",
                  :fill "currentColor",
                  :aria-hidden "true",
                  :style (merge {:cursor "pointer"} style)})
      (when on-click (dom/on "click" on-click))
      (svg/path
        (dom/props
          {:fill-rule "evenodd",
           :d
           "M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z",
           :clip-rule "evenodd"})))))

(e/defn ChevronRight [on-click] (ChevronBase. nil on-click))

(e/defn XRed
  []
  (e/client
    (svg/svg
      (dom/props
        {:viewBox "0 0 14 14",
         :class
         "align-middle inline-block h-5 w-5 stroke-red-700/75 group-hover:stroke-red-700/100"})
      (svg/path (dom/props {:d "M4 4l6 6m0-6l-6 6", :stroke-width "1.5"})))))


(e/defn BrickBadge
  [router full-name]
  (e/client
    (let [!copied? (atom false)
          copied? (e/watch !copied?)
          [org-name brick-name] (e/server (str/split full-name
                                            (re-pattern "\\/")))
          brick-path (-> router
                       (rr/match-by-name :biobrick {:brick-name brick-name :org-name org-name})
                       rr/match->path)
          svg-path (-> router
                     (rr/match-by-name :brick-badge-health {:brick-name brick-name :org-name org-name})
                     rr/match->path)]
      (dom/div
        (dom/props {:class "lg:px-8 sm:px-6 px-4"})
        (dom/img (dom/props {:src svg-path}))
        (dom/input
          (dom/props {:class "mt-4"
                      :disabled true
                      :id "badge-health-markdown"
                      :value (str "[![BioBricks](https://status.biobricks.ai" svg-path
                               ")](https://status.biobricks.ai" brick-path ")")}))
        (dom/button
          (dom/props {:class "rounded bg-white/10 px-2 py-1 text-sm font-semibold text-white shadow-sm hover:bg-white/20"
                      :type "button"})
          (dom/on "click"
            (e/fn [_]
              (let [input (js/document.getElementById "badge-health-markdown")
                    s (.-value input)]
                (.select input)
                (.setSelectionRange input 0 (count s))
                (js/navigator.clipboard.writeText s)
                (reset! !copied? true))))
          (dom/text (if copied? "Copied!" "Copy Markdown")))))))
