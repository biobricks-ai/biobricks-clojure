(ns biobricks.web-ui.app
  (:require #?(:clj [babashka.fs :as fs])
            #?(:clj [biobricks.brick-db.ifc :as brick-db])
            #?(:clj [biobricks.brick-repo.ifc :as brick-repo])
            #?(:clj [biobricks.github.ifc :as github])
            #?(:clj [clj-commons.humanize :as humanize])
            [clojure.string :as str]
            [contrib.str :refer [empty->nil pprint-str]]
            #?(:clj [datalevin.core :as dtlv])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [medley.core :as me]))

(e/def system (e/server (e/watch @(resolve 'biobricks.web-ui.api/system))))
;; Used to trigger queries on db change
(e/def datalevin-db (e/server (e/watch (-> system :donut.system/instances :web-ui :app :datalevin-conn))))

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

#?(:cljs (defonce !ui-settings
           (atom {:filter-opts #{"healthy" "unhealthy"}
                  :page 1
                  :sort-by-opt "recently-updated"})))
(e/def ui-settings (e/client (e/watch !ui-settings)))

(e/defn ElementData [label s]
  (dom/details
   (dom/summary (dom/text label))
   (dom/pre (dom/text s))))

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
     (e/server (humanize/datetime (github/parse-localdatetime updated_at)))))
   (dom/div
    (dom/props {:class "repo-card-badges"})
    (e/for [ext (sort file-extensions)]
      (dom/div
       (dom/props {:class "repo-card-badge"})
       (dom/text ext))))
   (ElementData. "repo" (pprint-str repo))))

(e/defn Repos [repos]
  (dom/div
   (e/for [repo repos]
     (Repo. repo))))

(defn healthy? [repo]
  (let [{:keys [health-git]} (:brick-info repo)]
    (and (seq health-git)
         (every? true? (vals health-git)))))

(def filter-options
  {"healthy" ["Healthy" healthy?]
   "unhealthy" ["Unhealthy" (complement healthy?)]})

(def sort-options
  {"size" ["Size" #(- (get-in % [:brick-info :data-bytes]))]
   "name" ["Name" :name]
   "recently-updated" ["Recently Updated" :updated_at reverse]})

(defn update-ui-settings! [m f & args]
  (as-> (assoc m :page 1) $
    (apply f $ args)))

(e/defn SortFilterControls [{:keys [sort-by-opt]}]
  (dom/select
   (dom/on
    "change"
    (e/fn [e]
      (e/client
       (swap! !ui-settings update-ui-settings! assoc :sort-by-opt (-> e .-target .-value)))))
   (e/for [[k [label]] sort-options]
     (dom/option
      (dom/props {:selected (= sort-by-opt k)
                  :value k})
      (dom/text label))))
  (dom/ul
   (e/for [[k [label]] filter-options]
     (let [id (str "SortFilterControls-filter-" k)]
       (dom/li
        (dom/input
         (dom/on
          "change"
          (e/fn [e]
            (e/client
             (let [v (-> e .-target .-checked)
                   f (if v conj disj)]
               (swap! !ui-settings update-ui-settings! update :filter-opts f k)))))
         (dom/props {:id id :type "checkbox" :checked (contains? filter-options k)}))
        (dom/label
         (dom/props {:for id})
         (dom/text label)))))))

(e/defn PageSelector [page num-pages]
  (dom/div
   (e/for [i (drop 1 (range (inc num-pages)))]
     (when (not= 1 i)
       (dom/text " | "))
     (dom/a
      (dom/on
       "click"
       (e/fn [_]
         (e/client
          (swap! !ui-settings assoc :page i))))
      (when (= i page)
        (dom/props {:style {:font-weight "bold"}}))
      (dom/text i)))))

(e/defn App []
  (e/server
   (let [{:as component :keys [datalevin-conn]}
         #__ (-> system :donut.system/instances :web-ui :app)
         {:keys [filter-opts page sort-by-opt]} (e/client ui-settings)
         filter-preds (mapv (comp second filter-options) filter-opts)
         filter-f #(loop [[pred & more] filter-preds]
                     (cond
                       (nil? pred) false
                       (pred %) true
                       :else (recur more)))
         repos  (let [[_ f g] (sort-options sort-by-opt)]
                  (->> repos vals
                       (filter (comp :is-brick? :brick-info))
                       (filter filter-f)
                       (sort-by f)
                       ((or g identity))))
         repos-on-page (->> repos
                            (drop (* 10 (dec page)))
                            (take 10))
         num-pages (+ (quot (count repos) 10)
                      (min 1 (mod (count repos) 10)))]
     (e/client
      (dom/link
       (dom/props {:rel "stylesheet" :href "/css/app.css"}))
      (ElementData. "component" (e/server
                                 (when datalevin-db
                                   (pprint-str component))))
      (ElementData. "schema" (e/server (when datalevin-db
                                         (pprint-str (into (sorted-map) (dtlv/schema datalevin-conn))))))
      (ElementData. "q" (e/server (pprint-str (dtlv/q '[:find (pull ?e [*]) :where [?e :git-repo/github-id]]
                                                      datalevin-db))))
      (ElementData. "ui-settings" (pprint-str ui-settings))
      (SortFilterControls. ui-settings)
      (Repos. repos-on-page)
      (PageSelector. page num-pages)))))
