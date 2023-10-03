(ns biobricks.github.ifc
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [hato.client :as hc]))

(defn get-url [url & [opts]]
  (let [response (hc/get
                  url
                  (merge
                   {:headers
                    {"Accept" "application/vnd.github+json"
                     "Authorization" "Bearer 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"
                     "User-Agent" "Hato"
                     "X-GitHub-Api-Version" "2022-11-28"}
                    :as :stream}
                   opts))]
    (-> response :body io/reader (json/read {:key-fn keyword}))))

(defn list-org-repos
  "Lists repositories for the specified organization.

   https://docs.github.com/en/free-pro-team@latest/rest/repos/repos?apiVersion=2022-11-28#list-organization-repositories"
  [org-name & [page-num]]
  (lazy-seq
   (let [page-num (or page-num 1)
         per-page 30
         results (get-url (str "https://api.github.com/orgs/" org-name "/repos")
                          {:query-params {"page" page-num "per_page" per-page}})]
     (concat
      results
      (when (= per-page (count results))
        (list-org-repos org-name (inc page-num)))))))
