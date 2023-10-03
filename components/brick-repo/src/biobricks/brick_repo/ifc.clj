(ns biobricks.brick-repo.ifc
  (:require [babashka.fs :as fs]
            [biobricks.process.ifc :as p]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]))

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

(defn brick-health-git
  "Returns the result of health checks that can be performed on the files
   stored in git (not DVC)."
  [dir]
  {:has-brick-dir? (or (with-open [rdr (io/reader (fs/file dir "dvc.lock"))]
                         (->> rdr yaml/parse-stream :stages vals (mapcat :outs)
                              (map :path) (some #{"brick/"}) boolean))
                       "Does not have a \"/brick\" output directory specified in \"/dvc.lock\".")
   :has-dvc-config? (or (fs/exists? (fs/path dir ".dvc" "config"))
                        "Does not have a \"/.dvc/config\" file.")
   :has-dvc-lock? (or (fs/exists? (fs/path dir "dvc.lock"))
                      "Does not have a \"/dvc.lock\" file.")
   :has-dvc-yaml? (or (fs/exists? (fs/path dir "dvc.yaml"))
                      "Does not have a \"/dvc.yaml\" file.")
   :has-readme? (or (fs/exists? (fs/path dir "README.md"))
                    "Does not have a \"README.md\" file.")})
