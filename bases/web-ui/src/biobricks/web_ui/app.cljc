(ns biobricks.web-ui.app
  (:require #?(:clj [biobricks.github.interface :as github])
            [clojure.string :as str]
            [contrib.str :refer [empty->nil pprint-str]]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

#?(:clj (defonce !repos (atom (->> (github/list-org-repos "biobricks-ai")
                                   (sort-by :name)))))
(e/def repos (e/server (e/watch !repos)))

(e/defn ElementData [data]
  (dom/details
   (dom/summary (dom/text "clj"))
   (dom/text (pprint-str data))))

(e/defn Repo
  [{:as repo
    :keys [description html_url name]}]
  (dom/div
   (dom/props {:class "repo-card"})
   (dom/h3
    (dom/a
     (dom/props {:href html_url})
     (dom/text name)))
   (dom/p
    (dom/text description))
   (ElementData. repo)))

(e/defn Repos [repos]
  (dom/div
   (e/for [repo repos]
     (Repo. repo))))

(e/defn App []
  (e/client
   (dom/link
    (dom/props {:rel "stylesheet" :href "/css/app.css"}))
   (dom/div
    (Repos. repos))))
