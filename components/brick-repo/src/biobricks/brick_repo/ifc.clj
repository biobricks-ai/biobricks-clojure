(ns biobricks.brick-repo.ifc
  (:require [babashka.fs :as fs]
            [biobricks.process.ifc :as p]
            [clojure.data.json :as json]
            [clojure.string :as str]))

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

(defn brick-data-bytes
  "Returns the size of the brick data in bytes."
  [dir]
  (->> @(p/process
         {:dir (fs/file dir) :err :string :out :string}
         "dvc" "list" "." "--json")
       :out
       json/read-str
       (keep (fn [{:strs [path size]}]
               (when (or (= "brick" path) (str/starts-with? path "brick/"))
                 size)))
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

(defn brick-info
  "Returns a map of brick info. Only returns :dir and :is-brick? if the repo
   does not appear to be a brick.

   ```
   {:data-bytes
    :dir
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
        (assoc brick-info :health-git (brick-health-git dir brick-info))))))
