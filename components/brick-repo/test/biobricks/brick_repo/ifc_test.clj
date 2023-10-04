(ns biobricks.brick-repo.ifc-test
  (:require [babashka.fs :as fs]
            [biobricks.brick-repo.ifc :as brick-repo]
            [biobricks.process.ifc :as p]
            [clojure.test :as test :refer [deftest is]]))

(deftest test-brick-config
  (fs/with-temp-dir [dir {:prefix "biobricks-test"}]
    @(p/process
      {:dir (fs/file dir)}
      "dvc" "init")
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
    (is (nil? dir))
    (brick-repo/clone (fs/file dir) "https://github.com/biobricks-ai/biobricks-issues.git")
    (is (false? (brick-repo/brick-dir? (fs/path dir "biobricks-issues"))))
    (brick-repo/clone (fs/file dir) "https://github.com/biobricks-ai/hgnc.git")
    (is (true? (brick-repo/brick-dir? (fs/path dir "hgnc"))))))
