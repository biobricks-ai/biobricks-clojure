(ns biobricks.brick-repo.ifc
  (:require [babashka.fs :as fs]
            [biobricks.process.ifc :as p]
            [clj-yaml.core :as yaml]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hato.client :as hc]))

(defn clone
  "Clones a git repo into dir.

   Returns a Path to the repo directory."
  [base-dir repo-url]
  (fs/with-temp-dir [tmpdir {:prefix "biobricks"}]
    (-> (p/process
         {:dir (fs/file tmpdir) :err :string :out :string}
         "git" "clone" repo-url)
        p/throw-on-error)
    (fs/create-dirs base-dir)
    (-> tmpdir fs/list-dir first
        (fs/move base-dir))))

(defn brick-dir?
  "Returns a boolean indicating whether dir contains a git repository for a biobrick."
  [dir]
  (fs/exists? (fs/path dir "dvc.yaml")))

(defn brick-config
  "Returns the brick config as a map.

   Example:
   `(brick-config \"bricks/zinc\")`

   ```
   {\"remote.biobricks.ai.url\" \"https://ins-dvc.s3.amazonaws.com/insdvc\",
    \"remote.s3.biobricks.ai.url\" \"s3://ins-dvc/insdvc\",
    \"core.remote\" \"biobricks.ai\"}
   ```"
  [dir]
  (->> @(p/process
         {:dir (fs/file dir) :err :string :out :string}
         "dvc" "config" "-l" "--project")
       :out
       str/split-lines
       (map #(str/split % #"="))
       (into {})))

(defn brick-lock
  "Returns the parsed data from dvc.lock."
  [dir]
  (with-open [rdr (io/reader (fs/file dir "dvc.lock"))]
    (yaml/parse-stream rdr)))

(defn- brick-data-file-specs
  "Returns a seq of {:hash :path :md5 :size :nfiles} maps for
   files in brick/.

   Files created prior to DVC 3.0 don't have a :hash entry."
  [dir]
  (let [lock (brick-lock dir)]
    (->> lock :stages vals
         (mapcat :outs)
         (keep
          (fn [{:as m :keys [path]}]
            (when (or (= "brick" path) (str/starts-with? path "brick/"))
              m))))))

(defn brick-data-bytes
  "Returns the size of the brick data in bytes."
  [dir]
  (->> (brick-data-file-specs dir)
       (map :size)
       (reduce + 0)))

(defn brick-health-git
  "Returns the result of health checks that can be performed on the files
   stored in git (not DVC)."
  [dir {:keys [data-bytes]}]
  {:has-brick-dir? (or (< 0 data-bytes)
                       "Does not have data in a \"/brick\" directory.")
   :has-dvc-config? (or (fs/exists? (fs/path dir ".dvc" "config"))
                        "Does not have a \"/.dvc/config\" file.")
   :has-dvc-lock? (or (fs/exists? (fs/path dir "dvc.lock"))
                      "Does not have a \"/dvc.lock\" file.")
   :has-dvc-yaml? (or (fs/exists? (fs/path dir "dvc.yaml"))
                      "Does not have a \"/dvc.yaml\" file.")
   :has-readme? (or (fs/exists? (fs/path dir "README.md"))
                    "Does not have a \"README.md\" file.")})

(defn- default-remote [config]
  (let [core-remote (get config "core.remote")]
    (get config (str "remote." core-remote ".url"))))

(defn download-url
  "Returns the download url for the given md5 hash.

   Usage:
   ```
   (download-url (brick-config \"bricks/zinc\") \"7eb3a14caf5b488296355129862d62d0.dir\")

   => \"https://ins-dvc.s3.amazonaws.com/insdvc/7e/b3a14caf5b488296355129862d62d0.dir\""
  [config md5 & {:keys [old-cache-location?]}]
  (if-let [remote-base (default-remote config)]
    (str remote-base
         (if old-cache-location? "/" "/files/md5/")
         (subs md5 0 2) "/" (subs md5 2))
    (throw (ex-info "No URL for default remote" {:config config :md5 md5}))))

(defn- list-dir
  "Returns a seq of {:md5 :relpath}"
  [config md5 & {:keys [old-cache-location?]}]
  (-> (download-url config md5 :old-cache-location? old-cache-location?)
      (hc/get {:as :stream
               :headers {"Accept" "application/json"}})
      :body
      io/reader
      (json/read {:key-fn keyword})))

(defn- brick-data-file-paths
  "Returns a seq of the brick data file paths.

   File paths are relative to the \"brick/\" dir."
  [dir]
  (let [config (brick-config dir)
        file-specs (brick-data-file-specs dir)]
    (->> file-specs
         (mapcat
          (fn [{:keys [hash md5 path]}]
            (if (str/ends-with? md5 ".dir")
              (map :relpath (list-dir config md5 :old-cache-location? (not hash)))
              [(->> path fs/components rest (apply fs/path) str)]))))))

(defn brick-data-file-extensions
  "Returns the set of file extensions in the brick data."
  [dir]
  (->> dir
       brick-data-file-paths
       (reduce #(conj % (fs/extension %2)) #{})))

(defn brick-info
  "Returns a map of brick info. Only returns :dir and :is-brick? if the repo
   does not appear to be a brick.

   ```
   {:data-bytes
    :dir
    :file-extensions
    :health-git
    :is-brick?}
   ```"
  [dir]
  (let [is-brick? (brick-dir? dir)
        brick-info {:dir dir
                    :is-brick? is-brick?}]
    (if-not is-brick?
      brick-info
      (let [brick-info (assoc
                        brick-info
                        :data-bytes (brick-data-bytes dir))]
        (assoc brick-info
               :file-extensions (try (brick-data-file-extensions dir)
                                     (catch Exception e
                                       (when (not= "status: 404" (ex-message e))
                                         (throw e))))
               :health-git (brick-health-git dir brick-info))))))
