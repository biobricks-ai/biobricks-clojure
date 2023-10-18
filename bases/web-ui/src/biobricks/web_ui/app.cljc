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

(e/def system (e/server (e/watch @(resolve 'biobricks.web-ui.system/system))))
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
  (e/server (when (-> system
                      instance
                      :debug?)
              (e/client (dom/details (dom/summary (dom/text label))
                                     (dom/pre (dom/text s)))))))

(e/defn StatusCircle
  "Shows a green circle for true status, rose for false,
   and gray for nil."
  [status]
  (dom/div
    (dom/props
      {:class (case status
                nil "flex-none rounded-full p-1 text-gray-500 bg-gray-100/10"
                true "flex-none rounded-full p-1 text-green-400 bg-green-400/10"
                false
                  "flex-none rounded-full p-1 text-rose-400 bg-rose-400/10")})
    (dom/div (dom/props {:class "h-2 w-2 rounded-full bg-current"}))))

(e/defn DotDivider
  []
  (dom/element :svg
               (dom/props {:viewBox "0 0 2 2",
                           :class "h-0.5 w-0.5 flex-none fill-gray-300"})
               (dom/element :circle (dom/props {:cx "1", :cy "1", :r "1"}))))

(e/defn RoundedBadge
  [colors label]
  (dom/div
    (dom/props
      {:class
         (str
           "rounded-full flex-none py-1 px-2 text-xs font-medium ring-1 ring-inset "
           colors)})
    (dom/text label)))

(e/defn FileExtensionBadge
  [ext]
  (case ext
    "hdt" (RoundedBadge. "text-amber-400 bg-amber-400/10 ring-amber-400/30"
                         "HDT")
    "parquet" (RoundedBadge.
                "text-indigo-400 bg-indigo-400/10 ring-indigo-400/30"
                "Parquet")
    (RoundedBadge. "text-gray-400 bg-gray-400/10 ring-gray-400/30" ext)))

(e/defn Repo
  [[{:as repo,
     :db/keys [id],
     :biobrick/keys [data-bytes data-pulled? health-check-data
                     health-check-failures],
     :git-repo/keys [checked-at description full-name html-url updated-at]}
    biobrick-file-ids]]
  (let [biobrick-files (e/server
                         (dtlv/pull-many datalevin-db '[*] biobrick-file-ids))
        extensions (->> biobrick-files
                        (map :biobrick-file/extension)
                        set)]
    (e/client
      (dom/li
        (dom/props
          {:class
             "relative flex items-center space-x-4 px-4 py-4 sm:px-6 lg:px-8"})
        (dom/div
          (dom/props {:class "min-w-0 flex-auto"})
          (dom/div (dom/props {:class "flex items-center gap-x-3"})
                   (StatusCircle. (cond (pos? health-check-failures) false
                                        (seq extensions) true))
                   (dom/h2
                     (dom/props
                       {:class
                          "min-w-0 text-sm font-semibold leading-6 text-white"})
                     (dom/a (dom/props {:href html-url, :class "flex gap-x-2"})
                            (dom/text full-name))))
          (dom/div
            (dom/props
              {:class
                 "mt-3 flex items-center gap-x-2.5 text-xs leading-5 text-gray-400"})
            (when (some-> data-bytes
                          pos?)
              (dom/p (dom/props {:class "whitespace-nowrap"})
                     (dom/text (e/server (humanize/filesize data-bytes))))
              (DotDivider.))
            (when updated-at
              (dom/p (dom/props {:class "whitespace-nowrap"})
                     (dom/text "Updated "
                               (e/server (date-str updated-at now))))))
          #_(dom/div
              (cond (nil? health-check-failures) (dom/text
                                                   "Waiting on health check")
                    (zero? health-check-failures) (dom/text "✓ Healthy")
                    :else (let [fails (->> health-check-data
                                           edn/read-string
                                           (me/filter-vals (comp not true?)))]
                            (dom/details
                              (dom/summary
                                (dom/text "✗ " (count fails) " checks failed"))
                              (dom/ul (e/for [[_ v] fails]
                                        (dom/li (dom/text v)))))))))
        (e/for [ext (sort extensions)] (FileExtensionBadge. ext))
        (e/server (when (-> system
                            instance
                            :debug?)
                    (e/client (dom/div (dom/props {:style {:clear "left"}})
                                       (ui/button
                                         (e/fn []
                                           (e/server (brick-db/check-brick-by-id
                                                       (-> system
                                                           instance
                                                           :brick-db)
                                                       id)))
                                         (dom/text
                                           "Force brick info update"))))))
        (ElementData. "repo" (e/server (pprint-str repo)))
        (ElementData. "biobrick-files"
                      (e/server (pprint-str biobrick-files)))))))

(e/defn Repos
  [repos]
  (dom/ul (dom/props {:role "list", :class "divide-y divide-white/5"})
          (e/for [repo repos] (Repo. repo))))

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

; https://tailwindui.com/components/application-ui/page-examples/home-screens#component-1cb122f657954361d2f5fce7ec641480
(e/defn App
  []
  (e/server
    (let [instance (-> system
                       :donut.system/instances
                       :web-ui
                       :app)
          {:keys [filter-opts page sort-by-opt]} (e/client ui-settings)
          filter-preds (mapv (comp second filter-options) filter-opts)
          filter-f #(loop [[pred & more] filter-preds]
                      (cond (nil? pred) false
                            (pred %) true
                            :else (recur more)))
          repos (->> (dtlv/q '[:find (pull ?e [*]) (distinct ?file) :where
                               [?e :git-repo/is-biobrick? true]
                               [?file :biobrick-file/biobrick ?e]]
                             datalevin-db)
                     (concat (dtlv/q '[:find (pull ?e [*]) :where
                                       [?e :git-repo/is-biobrick? true]
                                       (not [?file :biobrick-file/biobrick ?e])]
                                     datalevin-db)))
          repos (let [[_ f g] (sort-options sort-by-opt)]
                  (->> repos
                       (filter (comp #(and (:git-repo/is-biobrick? %)
                                           (filter-f %))
                                     first))
                       (sort-by (comp f first))
                       ((or g identity))))
          repos-on-page (->> repos
                             (drop (* 10 (dec page)))
                             (take 10))
          num-pages (+ (quot (count repos) 10) (min 1 (mod (count repos) 10)))]
      (when datalevin-db
        (e/client
          (dom/link (dom/props {:rel "stylesheet", :href "/css/compiled.css"}))
          (dom/div
            (dom/div (dom/props {:class "xl:pl-72"})
                     (dom/main
                       (dom/props {:clas "lg:pr-96"})
                       (ElementData. "instance"
                                     (e/server (when datalevin-db
                                                 (pprint-str instance))))
                       (ElementData. "schema"
                                     (e/server (when datalevin-db
                                                 (pprint-str (into
                                                               (sorted-map)
                                                               (dtlv/schema
                                                                 (datalevin-conn
                                                                   system)))))))
                       (comment
                         ;; Used for development
                         (ElementData. "query"
                                       (e/server (when datalevin-db
                                                   (pprint-str repos)))))
                       (ElementData. "ui-settings" (pprint-str ui-settings))
                       (SortFilterControls. ui-settings)
                       (Repos. repos-on-page)
                       (PageSelector. page num-pages)))))))))
