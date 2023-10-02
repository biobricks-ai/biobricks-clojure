(ns biobricks.github.interface-test
  (:require [clojure.test :as test :refer [deftest is]]
            [biobricks.github.interface :as github]))

(deftest test-list-org-repos
  (is (= "biobricks-ai"
         (-> (github/list-org-repos "biobricks-ai")
             first :owner :login))))
