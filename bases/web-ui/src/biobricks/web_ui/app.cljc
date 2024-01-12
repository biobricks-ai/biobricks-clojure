(ns biobricks.web-ui.app
  (:require [biobricks.ui-table.ifc :as ui-table]
            [biobricks.web-ui.api.routes :as routes]
            [biobricks.web-ui.app.shapes :as shapes]
            #?(:clj [clj-commons.humanize :as humanize])
            [clojure.edn :as edn]
            [clojure.string :as str]
            #?(:clj [datalevin.core :as dtlv])
            [heroicons.electric.v24.outline :as ho]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [medley.core :as me]
            [reitit.core :as rr]
            #?(:cljs [reitit.frontend.easy :as rfe]))
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
; Reactivity disabled for now
(e/def datalevin-db (e/server (e/watch (atom @(datalevin-conn system)))))

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
           (atom {:filter-opts {:file-type #{"hdt" "other" "parquet" "sqlite"}
                                :health #{"healthy"}}
                  :sort-by-opt "recently-updated"})))
(e/def ui-settings (e/client (e/watch !ui-settings)))

(def router (rr/router routes/routes))

#?(:cljs (e/def router-flow
           (->> (m/observe (fn [!] (rfe/start! router ! {:use-fragment false})))
             (m/relieve {})
             new)))

#?(:clj (defn date-str [date now] (humanize/datetime date :now-dt now)))

(e/defn RepoSmallInfo
  [{:biobrick/keys [data-bytes]
    :git-repo/keys [html-url updated-at]}]
  (e/client
    (dom/div
      (dom/props
        {:class
         "mt-3 flex items-center gap-x-2.5 text-xs leading-5 text-gray-400"})
      (when (some-> data-bytes
              pos?)
        (dom/p (dom/props {:class "whitespace-nowrap"})
          (dom/text (e/server (humanize/filesize data-bytes))))
        (shapes/DotDivider.))
      (when updated-at
        (dom/p (dom/props {:class "whitespace-nowrap"})
          (dom/text "Updated "
            (e/server (date-str updated-at now))))
        (shapes/DotDivider.))
      (dom/a (dom/props {:href html-url :target "_blank"})
        (dom/text "GitHub ")
        (ho/arrow-top-right-on-square
          (dom/props {:style {:display "inline" :width "1em"}}))))))

; https://tailwindui.com/components/application-ui/lists/stacked-lists#component-0ed7aad9572071f226b71abe32c3868f
(e/defn Repo
  [[{:as repo
     :biobrick/keys [health-check-failures]
     :git-repo/keys [full-name]}
    biobrick-files]]
  (let [[org-name brick-name] (e/server (str/split full-name
                                          (re-pattern "\\/")))
        brick-data-files (->> biobrick-files
                           (remove #(or (:biobrick-file/directory? %)
                                      (:biobrick-file/missing? %)
                                      (not (str/starts-with? (:biobrick-file/path %) "brick/"))))
                           (sort-by :biobrick-file/path))
        extensions (->> brick-data-files
                     (keep :biobrick-file/extension)
                     set)
        missing-files (->> biobrick-files
                        (filter :biobrick-file/missing?))
        brick-href (e/client
                     (rfe/href :biobrick
                       {:brick-name brick-name :org-name org-name}))]
    (when biobrick-files
      (e/client
        (dom/li
          (dom/props
            {:class
             "relative flex items-center space-x-4 px-4 py-4 sm:px-6 lg:px-8"})
          (dom/div
            (dom/props {:class "min-w-0 flex-auto"})
            (dom/div
              (dom/props {:class "flex items-center gap-x-3"})
              (shapes/StatusCircle.
                (cond (or (pos? health-check-failures) (seq missing-files)) false
                  (and (zero? health-check-failures) (seq extensions)) true))
              (dom/h2 (dom/props
                        {:class
                         "min-w-0 text-sm font-semibold leading-6 text-white"})
                (dom/a (dom/props {:class "flex gap-x-2" :href brick-href})
                  (dom/span (dom/props {:class "truncate"})
                    (dom/text org-name))
                  (dom/span (dom/props {:class "text-gray-400"})
                    (dom/text "/"))
                  (dom/span (dom/props {:class "whitespace-nowrap"})
                    (dom/text brick-name)))))
            (RepoSmallInfo. repo))
          (e/for [ext (sort extensions)] (shapes/FileExtensionBadge. ext))
          (e/client (dom/a (dom/props {:href brick-href})
                      (shapes/ChevronRight. nil))))))))

(e/defn Repos
  [repos]
  (e/client
    (dom/ul (dom/props {:role "list", :class "divide-y divide-white/5"})
      (e/for [repo repos] (Repo. repo)))))

(e/defn BrickFiles [biobrick-files]
  (e/client
    (ui-table/Container.
      "Files"
      (e/fn []
        (ui-table/Head. ["Path" "Size" "Download"]))
      (e/fn []
        (ui-table/Body.
          (e/for [{:biobrick-file/keys [dvc-url path size]}
                  (->> biobrick-files
                    (remove #(or (:biobrick-file/directory? %)
                               (:biobrick-file/missing? %)
                               (not (str/starts-with? (:biobrick-file/path %) "brick/"))))
                    (sort-by :biobrick-file/path))]
            (ui-table/Row.
              [(subs path 6)
               (e/server (some-> size humanize/filesize))
               (e/fn []
                 (dom/a (dom/props {:href dvc-url :target "_blank"})
                   (ho/arrow-down-tray (dom/props {:style {:width "1em"}}))))])))))))

(e/defn BioBrick
  [{:as repo
     :biobrick/keys [health-check-data health-check-failures]
     :git-repo/keys [description full-name]}
   biobrick-files]
  (e/server
    (let [[org-name brick-name] (str/split full-name (re-pattern "\\/"))
          brick-data-files (->> biobrick-files
                             (remove #(or (:biobrick-file/directory? %)
                                        (:biobrick-file/missing? %)
                                        (not (str/starts-with? (:biobrick-file/path %) "brick/"))))
                             (sort-by :biobrick-file/path))
          extensions (->> brick-data-files
                       (keep :biobrick-file/extension)
                       set)
          missing-files (->> biobrick-files
                          (filter :biobrick-file/missing?))]
      (e/client
        (dom/li
          (dom/props
            {:class
             "relative flex items-center space-x-4 px-4 py-4 sm:px-6 lg:px-8"})
          (dom/div
            (dom/props {:class "min-w-0 flex-auto"})
            (dom/div
              (dom/props {:class "flex items-center gap-x-3"})
              (shapes/StatusCircle.
                (cond (or (pos? health-check-failures) (seq missing-files)) false
                  (and (zero? health-check-failures) (seq extensions)) true))
              (dom/h2 (dom/props
                        {:class
                         "min-w-0 text-sm font-semibold leading-6 text-white"})
                (dom/span (dom/props {:class "flex gap-x-2"})
                  (dom/span (dom/props {:class "truncate"})
                    (dom/text org-name))
                  (dom/span (dom/props {:class "text-gray-400"})
                    (dom/text "/"))
                  (dom/span (dom/props {:class "whitespace-nowrap"})
                    (dom/text brick-name)))))
            (RepoSmallInfo. repo))
          (e/for [ext (sort extensions)] (shapes/FileExtensionBadge. ext)))
        (dom/li
          (dom/props
            {:class
             "relative flex items-center space-x-4 px-4 py-4 sm:px-6 lg:px-8"})
          (dom/div (dom/props {:class "min-w-0 flex-auto text-gray-300"})
            (dom/p (dom/text description))
            (when (some-> health-check-failures
                    pos?)
              (let [fails (->> health-check-data
                            edn/read-string
                            (me/filter-vals (comp not true?)))]
                (dom/div (dom/props {:style {:margin-top "1em"}})
                  (dom/ul (e/for [[_ v] fails]
                            (dom/li (shapes/XRed.) (dom/text v)))))))
            (when (seq missing-files)
              (dom/p (shapes/XRed.)
                (dom/text "Missing "
                  (count missing-files)
                  (if (= 1 (count missing-files))
                    " file or directory"
                    " files or directories")
                  " on S3 remote:"))
              (dom/ul (dom/props {:class "list-disc pl-5"})
                (e/for [{:biobrick-file/keys [directory? path]}
                        missing-files]
                  (dom/li (dom/text path (when directory? "/"))))))))
        (when (seq brick-data-files)
          (BrickFiles. brick-data-files))
        (shapes/BrickBadge. router full-name)))))

(defn healthy?
  [[repo files]]
  (and
    (some-> repo
      :biobrick/health-check-failures
      zero?)
    (every? (complement :biobrick-file/missing?) files)))

(def health-filter-options
  {"healthy" ["Healthy" healthy?]
   "unhealthy" ["Unhealthy" (complement healthy?)]})

(defn file-type-filter [file-type]
  (fn [[_ & files]]
    (some
      #(= file-type (:biobrick-file/extension %))
      (apply concat files))))

(defn other-file-type-filter [[_ & files]]
  (or (empty? files)
    (every?
      #(not (#{"hdt" "parquet" "sqlite"} (:biobrick-file/extension %)))
      (apply concat files))))

(def file-type-filter-options
  {"hdt" ["HDT" (file-type-filter "hdt")]
   "other" ["Other" other-file-type-filter]
   "parquet" ["Parquet" (file-type-filter "parquet")]
   "sqlite" ["SQLite" (file-type-filter "sqlite")]})

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
  (e/client
    (let [{:keys [file-type health]} (:filter-opts ui-settings)]
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
      (dom/div
        (dom/props {:class "items-center"})
        (e/for [[k [label]] health-filter-options]
          (let [id (str "SortFilterControls-filter-" k)
                checked? (contains? health k)]
            (dom/div
              (dom/on "click"
                (e/fn [_]
                  (e/client (let [f (if checked? disj conj)]
                              (swap! !ui-settings
                                update-ui-settings!
                                update-in
                                [:filter-opts :health]
                                f
                                k)))))
              (dom/button
                (dom/props {:aria-checked checked?
                            :aria-labelledby (str id "-label")
                            :class (str
                                     (if checked? "bg-indigo-600" "bg-gray-200")
                                     " relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:ring-offset-2")
                            :role "switch"
                            :type "button"})
                (dom/span
                  (dom/props {:aria-hidden true
                              :class (str
                                       (if checked? "translate-x-5" "translate-x-0")
                                       " pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out")})))
              (dom/span
                (dom/props {:class "ml-3 text-sm font-medium text-gray-300"
                            :id (str id "-label")})
                (dom/text label))))))
      (dom/div (dom/props {:class "mt-4"}))
      (dom/div
        (dom/props {:class "items-center"})
        (e/for [[k [label]] file-type-filter-options]
          (let [id (str "SortFileTypeFilterControls-filter-" k)
                checked? (contains? file-type k)]
            (dom/div
              (dom/on "click"
                (e/fn [_]
                  (e/client (let [f (if checked? disj conj)]
                              (swap! !ui-settings
                                update-ui-settings!
                                update-in
                                [:filter-opts :file-type]
                                f
                                k)))))
              (dom/button
                (dom/props {:aria-checked checked?
                            :aria-labelledby (str id "-label")
                            :class (str
                                     (if checked? "bg-indigo-600" "bg-gray-200")
                                     " relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:ring-offset-2")
                            :role "switch"
                            :type "button"})
                (dom/span
                  (dom/props {:aria-hidden true
                              :class (str
                                       (if checked? "translate-x-5" "translate-x-0")
                                       " pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out")})))
              (dom/span
                (dom/props {:class "ml-3 text-sm font-medium text-gray-300"
                            :id (str id "-label")})
                (dom/text label)))))))))

(e/defn PageSelector
  [page num-pages]
  (e/client
    (dom/div (dom/props {:class "text-gray-300"})
      (e/for [i (drop 1 (range (inc num-pages)))]
        (when (not= 1 i) (dom/text " | "))
        (dom/a (dom/on "click"
                 (e/fn [_]
                   (e/client (rfe/set-query
                               #(if (= 1 i)
                                  (dissoc % :page)
                                  (assoc % :page (str i)))))))
          (dom/props {:style {:cursor "pointer",
                              :font-weight (when (= i page) "bold")}})
          (dom/text i))))))

; https://tailwindui.com/components/application-ui/page-examples/home-screens#component-1cb122f657954361d2f5fce7ec641480
(e/defn ReposList
  []
  (e/server
    (let [{:keys [filter-opts sort-by-opt]} (e/client ui-settings)
          {:keys [file-type health]} filter-opts
          page (e/client (or (some-> router-flow
                               :query-params
                               :page
                               parse-long)
                           1))
          file-type-filter-preds (mapv (comp second file-type-filter-options) file-type)
          file-type-filter-f #(loop [[pred & more] file-type-filter-preds]
                                (cond (nil? pred) false
                                  (pred %) true
                                  :else (recur more)))
          health-filter-preds (mapv (comp second health-filter-options) health)
          health-filter-f #(loop [[pred & more] health-filter-preds]
                             (cond (nil? pred) false
                               (pred %) true
                               :else (recur more)))
          start (System/nanoTime)
          repos (->> (dtlv/q '[:find (pull ?e [*]) (distinct ?file)
                               :where
                               [?e :git-repo/archived? false]
                               [?e :git-repo/git-sha-latest ?sha]
                               [?e :git-repo/is-biobrick? true]
                               [?file :biobrick-file/biobrick ?e]
                               [?file :biobrick-file/git-sha ?sha]]
                       datalevin-db)
                  (concat (dtlv/q '[:find (pull ?e [*]) :where
                                    [?e :git-repo/archived? false]
                                    [?e :git-repo/is-biobrick? true]
                                    (not [?file :biobrick-file/biobrick ?e])]
                            datalevin-db)))
          repos (let [[_ f g] (sort-options sort-by-opt)]
                  (->> repos
                    (sort-by (comp f first))
                    ((or g identity))
                    (map (fn [[repo files]]
                           [repo
                            (dtlv/pull-many datalevin-db '[*] files)]))
                    (filter #(and (health-filter-f %) (file-type-filter-f %)))))
          repos-on-page (->> repos
                          (drop (* 10 (dec page)))
                          (take 10)
                          vec)
          num-pages (+ (quot (count repos) 10) (min 1 (mod (count repos) 10)))
          data-ms (quot (- (System/nanoTime) start) 1000000)]
      (e/client
        (when datalevin-db
          (js/console.log "Data loaded in" data-ms "ms")
          (dom/link
            (dom/props
              {:rel "stylesheet"
               :href (str "/css/compiled.css?v=" (e/server (System/getProperty "HYPERFIDDLE_ELECTRIC_SERVER_VERSION")))}))
          (dom/div
            (dom/div (dom/props {:class "xl:pl-72"})
              (dom/main
                (dom/props {:clas "lg:pr-96"})
                (SortFilterControls. ui-settings)
                (Repos. repos-on-page)
                (PageSelector. page num-pages)))))))))

(e/defn BioBrickPage
  []
  (e/server
    (let [{:keys [brick-name org-name]} (e/client (-> router-flow
                                                    :path-params))
          repo-name (str org-name "/" brick-name)
          [repo biobrick-file-ids]
          #__ (->> (dtlv/q '[:find (pull ?e [*]) (distinct ?file) :in $ ?name
                             :where [?e :git-repo/is-biobrick? true]
                             [?e :git-repo/full-name ?name]
                             [?file :biobrick-file/biobrick ?e]]
                     datalevin-db
                     repo-name)
                (concat (dtlv/q '[:find (pull ?e [*]) :in $ ?name :where
                                  [?e :git-repo/is-biobrick? true]
                                  [?e :git-repo/full-name ?name]
                                  (not [?file :biobrick-file/biobrick ?e])]
                          datalevin-db
                          repo-name))
                first)
          files (dtlv/pull-many datalevin-db '[*] (take 100 biobrick-file-ids))]
      (e/client
        (dom/div (dom/div (dom/props {:class "xl:pl-72"})
                   (dom/main (dom/props {:clas "lg:pr-96"})
                     (BioBrick. repo files))))))))

(e/defn App
  []
  (e/client
    (binding [dom/node js/document.body]
      (let [match router-flow
            match-name (-> match
                         :data
                         :name)]
        (if match
          (case match-name
            :home (ReposList.)
            :biobrick (BioBrickPage.)
            (dom/div (dom/text "No component for " (str match-name))))
          (dom/div (dom/text "404 Not Found")))))))
