(ns biobricks.web-ui.app
  (:require #?(:clj [biobricks.brick-db.ifc :as brick-db])
            #?(:clj [clj-commons.humanize :as humanize])
            [clojure.edn :as edn]
            [clojure.string :as str]
            [contrib.str :refer [empty->nil pprint-str]]
            #?(:clj [datalevin.core :as dtlv])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [medley.core :as me])
  #?(:clj (:import [java.time LocalDateTime])))

(e/def system (e/server (e/watch @(resolve 'biobricks.web-ui.api/system))))
#?(:clj (defn instance
          [system]
          (-> system
              :donut.system/instances
              :web-ui
              :app)))
#?(:clj (defn datalevin-conn
          [system]
          (-> system
              instance
              :datalevin-conn)))

;; Used to trigger queries on db change
(e/def datalevin-db (e/server (e/watch (datalevin-conn system))))


#?(:clj (defonce !now (atom (LocalDateTime/now))))
(e/def now (e/server (e/watch !now)))
#?(:clj (defonce now-thread
          (doto (Thread. #(loop []
                            (reset! !now (LocalDateTime/now))
                            (Thread/sleep 1000)
                            (recur)))
            (.setDaemon true)
            .start)))

#?(:cljs (defonce !ui-settings
           (atom {:filter-opts #{"healthy" "unhealthy"},
                  :page 1,
                  :sort-by-opt "recently-updated"})))
(e/def ui-settings (e/client (e/watch !ui-settings)))

#?(:clj (defn date-str [date now] (humanize/datetime date :now-dt now)))

(e/defn ElementData
  [label s]
  (dom/details (dom/summary (dom/text label)) (dom/pre (dom/text s))))

(e/defn Repo
  [{:as repo,
    :db/keys [id],
    :biobrick/keys [data-bytes data-pulled? file-extension health-check-data
                    health-check-failures],
    :git-repo/keys [checked-at description full-name html-url updated-at]}]
  (e/client
    (dom/div
      (dom/props {:class "repo-card"})
      (dom/h3 (dom/a (dom/props {:href html-url}) (dom/text full-name)))
      (when (some-> data-bytes
                    pos?)
        (dom/div (dom/text (e/server (humanize/filesize data-bytes)))))
      (dom/div
        (cond (nil? health-check-failures) (dom/text "Waiting on health check")
              (zero? health-check-failures) (dom/text "✓ Healthy")
              :else (let [fails (->> health-check-data
                                     edn/read-string
                                     (me/filter-vals (comp not true?)))]
                      (dom/details
                        (dom/summary
                          (dom/text "✗ " (count fails) " checks failed"))
                        (dom/ul (e/for [[_ v] fails] (dom/li (dom/text v))))))))
      (when data-pulled? (dom/div (dom/text "✓ Data pulled")))
      (dom/p (dom/text description))
      (dom/p (when updated-at
               (dom/text "Updated " (e/server (date-str updated-at now)))))
      (dom/p (when checked-at
               (dom/text "Checked " (e/server (date-str checked-at now)))))
      (dom/div (dom/props {:class "repo-card-badges"})
               (e/for [ext (sort file-extension)]
                 (dom/div (dom/props {:class "repo-card-badge"})
                          (dom/text ext))))
      (dom/div (dom/props {:style {:clear "left"}})
               (ui/button (e/fn []
                            (e/server (brick-db/check-brick-by-id (-> system
                                                                      instance
                                                                      :brick-db)
                                                                  id)))
                          (dom/text "Force brick info update")))
      (ElementData. "repo" (pprint-str repo)))))

(e/defn Repos [repos] (dom/div (e/for [repo repos] (Repo. repo))))

(defn healthy?
  [repo]
  (boolean (some-> repo
                   :biobrick/health-check-failures
                   zero?)))

(def filter-options
  {"healthy" ["Healthy" healthy?],
   "unhealthy" ["Unhealthy" (complement healthy?)]})

(def sort-options
  {"size" ["Size"
           #(some-> %
                    :biobrick/data-bytes
                    -)],
   "name" ["Name" :git-repo/full-name],
   "recently-updated" ["Recently Updated" :git-repo/updated-at reverse]})

(defn update-ui-settings!
  [m f & args]
  (as-> (assoc m :page 1) $ (apply f $ args)))

(e/defn SortFilterControls
  [{:keys [sort-by-opt]}]
  (dom/select (dom/on "change"
                      (e/fn [e]
                        (e/client (swap! !ui-settings update-ui-settings!
                                    assoc
                                    :sort-by-opt
                                    (-> e
                                        .-target
                                        .-value)))))
              (e/for [[k [label]] sort-options]
                (dom/option (dom/props {:selected (= sort-by-opt k), :value k})
                            (dom/text label))))
  (dom/ul
    (e/for [[k [label]] filter-options]
      (let [id (str "SortFilterControls-filter-" k)]
        (dom/li (dom/input (dom/on "change"
                                   (e/fn [e]
                                     (e/client (let [v (-> e
                                                           .-target
                                                           .-checked)
                                                     f (if v conj disj)]
                                                 (swap! !ui-settings
                                                   update-ui-settings!
                                                   update
                                                   :filter-opts
                                                   f
                                                   k)))))
                           (dom/props {:id id,
                                       :type "checkbox",
                                       :checked (contains? filter-options k)}))
                (dom/label (dom/props {:for id}) (dom/text label)))))))

(e/defn PageSelector
  [page num-pages]
  (dom/div (e/for [i (drop 1 (range (inc num-pages)))]
             (when (not= 1 i) (dom/text " | "))
             (dom/a (dom/on "click"
                            (e/fn [_]
                              (e/client (swap! !ui-settings assoc :page i))))
                    (when (= i page) (dom/props {:style {:font-weight "bold"}}))
                    (dom/text i)))))

(e/defn App
  []
  (e/server
    (let [{:as instance, :keys [datalevin-conn]}
            #__
            (-> system
                :donut.system/instances
                :web-ui
                :app)
          {:keys [filter-opts page sort-by-opt]} (e/client ui-settings)
          filter-preds (mapv (comp second filter-options) filter-opts)
          filter-f #(loop [[pred & more] filter-preds]
                      (cond (nil? pred) false
                            (pred %) true
                            :else (recur more)))
          repos (->> (dtlv/q '[:find (pull ?e [*]) :where
                               [?e :git-repo/is-biobrick? true]]
                             datalevin-db)
                     (apply concat))
          repos (let [[_ f g] (sort-options sort-by-opt)]
                  (->> repos
                       (filter #(and (:git-repo/is-biobrick? %) (filter-f %)))
                       (sort-by f)
                       ((or g identity))))
          repos-on-page (->> repos
                             (drop (* 10 (dec page)))
                             (take 10))
          num-pages (+ (quot (count repos) 10) (min 1 (mod (count repos) 10)))]
      (when datalevin-db
        (e/client
          (dom/link (dom/props {:rel "stylesheet", :href "/css/app.css"}))
          (ElementData. "instance"
                        (e/server (when datalevin-db (pprint-str instance))))
          (ElementData. "schema"
                        (e/server (when datalevin-db
                                    (pprint-str (into (sorted-map)
                                                      (dtlv/schema
                                                        datalevin-conn))))))
          (comment
            ;; Used for development
            (ElementData. "query"
                          (e/server (when datalevin-db (pprint-str repos)))))
          (ElementData. "ui-settings" (pprint-str ui-settings))
          (SortFilterControls. ui-settings)
          (Repos. repos-on-page)
          (PageSelector. page num-pages))))))
