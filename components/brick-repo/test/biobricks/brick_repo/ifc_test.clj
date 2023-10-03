(ns biobricks.brick-repo.ifc-test
  (:require [babashka.fs :as fs]
            [biobricks.brick-repo.ifc :as brick-repo]
            [clojure.test :as test :refer [deftest is]]))

(deftest test-brick-dir?
  (is (false? (brick-repo/brick-dir? (System/getProperty "user.dir")))))

(deftest test-clone
  (fs/with-temp-dir [dir {:prefix "biobricks-test"}]'
    (is (nil? dir))
    (brick-repo/clone (fs/file dir) "https://github.com/biobricks-ai/biobricks-issues.git")
    (is (false? (brick-repo/brick-dir? (fs/path dir "biobricks-issues"))))
    (brick-repo/clone (fs/file dir) "https://github.com/biobricks-ai/hgnc.git")
    (is (true? (brick-repo/brick-dir? (fs/path dir "hgnc"))))))
