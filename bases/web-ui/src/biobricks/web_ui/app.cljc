(ns biobricks.web-ui.app
  (:require #?(:clj [babashka.fs :as fs])
            #?(:clj [biobricks.brick-repo.ifc :as brick-repo])
            #?(:clj [biobricks.github.ifc :as github])
            #?(:clj [clj-commons.humanize :as humanize])
            [clojure.string :as str]
            [contrib.str :refer [empty->nil pprint-str]]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [medley.core :as me]))

#?(:clj (defonce !repos (atom (reduce #(assoc % (:url %2) %2) {} (github/list-org-repos "biobricks-ai")))))
(e/def repos (e/server (e/watch !repos)))

#?(:clj
   (do
     (defonce brick-lock (Object.))
     (defn pull-repos []
       (doseq [{:keys [name clone_url url]} (vals @!repos)]
         (locking brick-lock
           (let [path (fs/path "bricks" name)
                 dir (if (fs/exists? path)
                       path
                       (brick-repo/clone "bricks" clone_url))
                 brick-info (brick-repo/brick-info dir)]
             (swap! !repos assoc-in [url :brick-info] brick-info)))))
     #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
     (defonce repo-puller
       (doto (Thread. pull-repos)
         (.setDaemon true)
         .start))))

(e/defn ElementData [data]
  (dom/details
   (dom/summary (dom/text "clj"))
   (dom/text (pprint-str data))))

(e/defn Repo
  [{:as repo
    :keys [description html_url name updated_at]
    {:keys [data-bytes file-extensions health-git]} :brick-info}]
  (dom/div
   (dom/props {:class "repo-card"})
   (dom/h3
    (dom/a
     (dom/props {:href html_url})
     (dom/text name)))
   (when (some-> data-bytes pos?)
     (dom/div
      (dom/text (e/server (humanize/filesize data-bytes)))))
   (dom/div
    (cond
      (empty? health-git)
      (dom/text "Waiting on health check")

      (every? true? (vals health-git))
      (dom/text "✓ Healthy")

      :else
      (let [fails (me/filter-vals (comp not true?) health-git)]
        (dom/details
         (dom/summary
          (dom/text "✗ " (count fails) " checks failed"))
         (dom/ul
          (e/for [[_ v] fails]
            (dom/li (dom/text v))))))))
   (dom/p
    (dom/text description))
   (dom/p
    (dom/text
     "Updated "
     (e/server (humanize/datetime (github/parse-datetime updated_at)))))
   (dom/div
    (dom/props {:class "repo-card-badges"})
    (e/for [ext (sort file-extensions)]
      (dom/div
       (dom/props {:class "repo-card-badge"})
       (dom/text ext))))
   (ElementData. repo)))

(e/defn Repos [repos]
  (dom/div
   (e/for [repo repos]
     (Repo. repo))))

(e/defn App []
  (e/server
   (let [repos (->> repos vals
                    (sort-by :name)
                    (filter (comp :is-brick? :brick-info)))]
     (e/client
      (dom/link
       (dom/props {:rel "stylesheet" :href "/css/app.css"}))
      (dom/div
       (Repos. repos))))))
