(ns biobricks.github.ifc-test
  (:require [clojure.test :as test :refer [deftest is]]
            [biobricks.github.ifc :as github]))

(deftest test-list-org-repos
  (is (= "biobricks-ai"
        (-> (github/list-org-repos {:token (github/get-token-from-env)}
              "biobricks-ai")
          first
          :owner
          :login))))
