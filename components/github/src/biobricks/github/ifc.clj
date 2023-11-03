(ns biobricks.github.ifc
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [hato.client :as hc]
            [medley.core :as me])
  (:import [java.util Date]
           [java.time LocalDateTime ZoneId]
           [java.time.format DateTimeFormatter]))

(defn get-token-from-env
  "Returns a GitHub token from the environment."
  []
  (or (System/getenv "GH_TOKEN") (System/getenv "GITHUB_TOKEN")))

(defn get-url
  [url & [opts]]
  (let [{:keys [token]} opts
        headers (->> {"Accept" "application/vnd.github+json",
                      "Authorization" (when token (str "Bearer " token)),
                      "User-Agent" "Hato",
                      "X-GitHub-Api-Version" "2022-11-28"}
                  (me/remove-vals nil?))
        response (hc/get url (merge {:headers headers, :as :stream} opts))]
    (-> response
      :body
      io/reader
      (json/read {:key-fn keyword}))))

(defn list-org-repos
  "Lists repositories for the specified organization.

   https://docs.github.com/en/free-pro-team@latest/rest/repos/repos?apiVersion=2022-11-28#list-organization-repositories"
  [opts org-name & [page-num]]
  (lazy-seq (let [page-num (or page-num 1)
                  per-page 30
                  results
                  (get-url
                    (str "https://api.github.com/orgs/" org-name "/repos")
                    {:query-params {"page" page-num, "per_page" per-page},
                     :token (:token opts)})]
              (concat results
                (when (= per-page (count results))
                  (list-org-repos opts org-name (inc page-num)))))))

(defn parse-localdatetime
  "Parse a string in ISO Date/Time format to a java.time.LocalDateTime.

   Usage: `(parse-localdatetime \"2023-03-17T19:08:13Z\")`"
  [s]
  (LocalDateTime/parse s DateTimeFormatter/ISO_DATE_TIME))

(defn localdatetime->date
  [ldt]
  (-> ldt
    (.atZone (ZoneId/systemDefault))
    .toInstant
    Date/from))

(defn parse-date
  "Parse a string in ISO Date/Time format to a java.util.Date.

  Usage: `(parse-date \"2023-03-17T19:08:13Z\")`"
  [s]
  (localdatetime->date (parse-localdatetime s)))
