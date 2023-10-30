(ns biobricks.web-ui.app
  (:require #?(:clj [biobricks.brick-db.ifc :as brick-db])
            #?(:clj [clj-commons.humanize :as humanize])
            [clojure.edn :as edn]
            [clojure.string :as str]
            [contrib.str :refer [empty->nil pprint-str]]
            #?(:clj [datalevin.core :as dtlv])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-svg :as svg]
            [hyperfiddle.electric-ui4 :as ui]
            [medley.core :as me]
            [missionary.core :as m]
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
           (atom {:filter-opts #{"healthy" "unhealthy"},
                  :sort-by-opt "recently-updated"})))
(e/def ui-settings (e/client (e/watch !ui-settings)))

(def router
  (rr/router [["/" ["" :home]]
              ["/u" ["/:org-name" ["/:brick-name" :biobrick]]]]))

#?(:cljs (e/def router-flow
           (->> (m/observe (fn [!] (rfe/start! router ! {:use-fragment false})))
                (m/relieve {})
                new)))

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
  (svg/svg (dom/props {:viewBox "0 0 2 2",
                       :class "h-0.5 w-0.5 flex-none fill-gray-300"})
           (svg/circle (dom/props {:cx "1", :cy "1", :r "1"}))))

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

(e/defn ChevronBase
  [style on-click]
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
         :clip-rule "evenodd"}))))

(e/defn ChevronRight [on-click] (ChevronBase. nil on-click))

(e/defn ChevronDown
  [on-click]
  (ChevronBase. {:transform "rotate(90deg)"} on-click))

(e/defn XRed
  []
  (svg/svg
    (dom/props
      {:viewBox "0 0 14 14",
       :class
         "align-middle inline-block h-5 w-5 stroke-red-700/75 group-hover:stroke-red-700/100"})
    (svg/path (dom/props {:d "M4 4l6 6m0-6l-6 6", :stroke-width "1.5"}))))

; https://tailwindui.com/components/application-ui/lists/stacked-lists#component-0ed7aad9572071f226b71abe32c3868f
(e/defn Repo
  [[{:biobrick/keys [data-bytes health-check-failures],
     :git-repo/keys [full-name html-url updated-at]} biobrick-file-ids]]
  (let [[org-name brick-name] (e/server (str/split full-name
                                                   (re-pattern "\\/")))
        biobrick-files (e/server
                         (dtlv/pull-many datalevin-db '[*] biobrick-file-ids))
        extensions (->> biobrick-files
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
            (StatusCircle.
              (cond (or (pos? health-check-failures) (seq missing-files)) false
                    (and (zero? health-check-failures) (seq extensions)) true))
            (dom/h2 (dom/props
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
                               (e/server (date-str updated-at now)))))))
        (e/for [ext (sort extensions)] (FileExtensionBadge. ext))
        (e/client (dom/a (dom/props {:href (rfe/href :biobrick
                                                     {:brick-name brick-name,
                                                      :org-name org-name})})
                         (ChevronRight. nil)))))))

(e/defn Repos
  [repos]
  (dom/ul (dom/props {:role "list", :class "divide-y divide-white/5"})
          (e/for [repo repos] (Repo. repo))))

(e/defn BioBrick
  [[{:as repo,
     :db/keys [id],
     :biobrick/keys [data-bytes health-check-data health-check-failures],
     :git-repo/keys [description full-name html-url updated-at]}
    biobrick-file-ids]]
  (let [biobrick-files (e/server
                         (dtlv/pull-many datalevin-db '[*] biobrick-file-ids))
        extensions (->> biobrick-files
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
            (StatusCircle.
              (cond (or (pos? health-check-failures) (seq missing-files)) false
                    (and (zero? health-check-failures) (seq extensions)) true))
            (dom/h2 (dom/props
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
                               (e/server (date-str updated-at now)))))))
        (e/for [ext (sort extensions)] (FileExtensionBadge. ext)))
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
                                        (dom/li (XRed.) (dom/text v)))))))
                 (when (seq missing-files)
                   (dom/p (XRed.)
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
                                       (dom/text "Force brick info update"))))))
      (ElementData. "repo" (e/server (pprint-str repo)))
      (ElementData. "biobrick-files" (e/server (pprint-str biobrick-files))))))


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
  (dom/ul (dom/props {:class "text-gray-300"})
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
                                             :checked (contains? filter-options
                                                                 k)}))
                      (dom/label (dom/props {:for id}) (dom/text label)))))))

(e/defn PageSelector
  [page num-pages]
  (dom/div (dom/props {:class "text-gray-300"})
           (e/for [i (drop 1 (range (inc num-pages)))]
             (when (not= 1 i) (dom/text " | "))
             (dom/a (dom/on "click"
                            (e/fn [_]
                              (e/client (rfe/set-query #(assoc %
                                                          :page (str i))))))
                    (dom/props {:style {:cursor "pointer",
                                        :font-weight (when (= i page) "bold")}})
                    (dom/text i)))))

; https://tailwindui.com/components/application-ui/page-examples/home-screens#component-1cb122f657954361d2f5fce7ec641480
(e/defn ReposList
  []
  (e/server
    (let [instance (-> system
                       :donut.system/instances
                       :web-ui
                       :app)
          {:keys [filter-opts sort-by-opt]} (e/client ui-settings)
          page (e/client (or (some-> router-flow
                                     :query-params
                                     :page
                                     parse-long)
                             1))
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

(e/defn BioBrickPage
  []
  (e/server
    (let [{:keys [brick-name org-name]} (e/client (-> router-flow
                                                      :path-params))
          repo-name (str org-name "/" brick-name)
          repo (->> (dtlv/q '[:find (pull ?e [*]) (distinct ?file) :in $ ?name
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
                    first)]
      (e/client (dom/link (dom/props {:rel "stylesheet",
                                      :href "/css/compiled.css"}))
                (dom/div (dom/div (dom/props {:class "xl:pl-72"})
                                  (dom/main (dom/props {:clas "lg:pr-96"})
                                            (BioBrick. repo))))))))

(e/defn App
  []
  (e/client (let [match router-flow
                  match-name (-> match
                                 :data
                                 :name)]
              (if match
                (case match-name
                  :home (ReposList.)
                  :biobrick (BioBrickPage.)
                  (dom/div (dom/text "No component for " (str match-name))))
                (dom/div (dom/text "404 Not Found"))))))
