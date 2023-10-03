(ns biobricks.web-ui.app
  (:require #?(:clj [babashka.fs :as fs])
            #?(:clj [biobricks.brick-repo.ifc :as brick-repo])
            #?(:clj [biobricks.github.ifc :as github])
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
                 is-brick? (brick-repo/brick-dir? dir)
                 brick-info {:dir dir
                             :is-brick? is-brick?}
                 brick-info (if-not is-brick?
                              brick-info
                              (assoc brick-info
                                     :brick-health-git (brick-repo/brick-health-git dir)))]
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
    :keys [description html_url name]
    {:keys [brick-health-git]} :brick-info}]
  (dom/div
   (dom/props {:class "repo-card"})
   (dom/h3
    (dom/a
     (dom/props {:href html_url})
     (dom/text name)))
   (dom/div
    (cond
      (every? true? (vals brick-health-git))
      (dom/text "✓ Healthy")

      (seq brick-health-git)
      (let [fails (me/filter-vals (comp not true?) brick-health-git)]
        (dom/details
         (dom/summary
          (dom/text (count fails) " checks failed"))
         (dom/ul
          (e/for [[_ v] fails]
            (dom/li (dom/text v))))))

      :else
      (dom/text "Waiting on health check")))
   (dom/p
    (dom/text description))
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
