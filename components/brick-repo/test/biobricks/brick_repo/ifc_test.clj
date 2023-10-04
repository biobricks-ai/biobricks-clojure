(ns biobricks.brick-repo.ifc-test
  (:require [babashka.fs :as fs]
            [biobricks.brick-repo.ifc :as brick-repo]
            [biobricks.process.ifc :as p]
            [clojure.test :as test :refer [deftest is]]))

(deftest test-brick-config
  (fs/with-temp-dir [dir {:prefix "biobricks-test"}]
    @(p/process
      {:dir (fs/file dir)
       :err :string
       :extra-env {"DVC_NO_ANALYTICS" "1"}
       :out :string}
      "dvc" "init" "--no-scm")
    (fs/create-dirs (fs/path dir ".dvc"))
    (spit (fs/file dir ".dvc" "config")
          "[core]\n  remote = biobricks.ai\n['remote \"biobricks.ai\"']\n  url = https://ins-dvc.s3.amazonaws.com/insdvc\n")
    (is (= {"core.remote" "biobricks.ai"
            "remote.biobricks.ai.url" "https://ins-dvc.s3.amazonaws.com/insdvc"}
         (brick-repo/brick-config dir)))))

(deftest test-brick-dir?
  (is (false? (brick-repo/brick-dir? (System/getProperty "user.dir")))))

(deftest test-clone
  (fs/with-temp-dir [dir {:prefix "biobricks-test"}]
    (brick-repo/clone (fs/file dir) "https://github.com/biobricks-ai/biobricks-issues.git")
    (is (false? (brick-repo/brick-dir? (fs/path dir "biobricks-issues"))))
    (brick-repo/clone (fs/file dir) "https://github.com/biobricks-ai/hgnc.git")
    (is (true? (brick-repo/brick-dir? (fs/path dir "hgnc"))))))

(deftest test-download-url
  (let [config {"core.remote" "biobricks.ai"
                "remote.biobricks.ai.url" "https://ins-dvc.s3.amazonaws.com/insdvc"}]
    (is (= "https://ins-dvc.s3.amazonaws.com/insdvc/00/044973831d9a3bbe307dc46f436451"
           (@#'brick-repo/download-url config "00044973831d9a3bbe307dc46f436451"))
        "File download URLs work")
    (is (= "https://ins-dvc.s3.amazonaws.com/insdvc/7e/b3a14caf5b488296355129862d62d0.dir"
           (@#'brick-repo/download-url config "7eb3a14caf5b488296355129862d62d0.dir"))
        "Directory download URLs work")))
