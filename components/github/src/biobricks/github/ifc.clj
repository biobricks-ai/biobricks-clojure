(ns biobricks.github.ifc
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [hato.client :as hc]))

(defn get-url [url]
  (let [response (hc/get
                  url
                  {:headers
                   {"Accept" "application/vnd.github+json"
                    "User-Agent" "Hato"
                    "X-GitHub-Api-Version" "2022-11-28"}
                   :as :stream})]
    (-> response :body io/reader (json/read {:key-fn keyword}))))

(defn list-org-repos
  "Lists repositories for the specified organization.

   https://docs.github.com/en/free-pro-team@latest/rest/repos/repos?apiVersion=2022-11-28#list-organization-repositories"
  [org-name]
  (get-url (str "https://api.github.com/orgs/" org-name "/repos")))
