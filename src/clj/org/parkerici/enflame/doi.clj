(ns org.parkerici.enflame.doi
  (:require [clj-http.client :as client]
            [org.parkerici.multitool.core :as u]
            [taoensso.timbre :as log]))

;;; TODO error handling

;;; → To Voracious

;;; Not currently used, but TODO use to fix formatting, add italics
(defn doi-content
  [doi]
  (:body
   (client/get
    (str "https://doi.org/" doi)
    {:accept "application/vnd.citationstyles.csl+json"
     :as :json})))

;;; Defined styles: https://github.com/citation-style-language/styles
(defn doi-text
  [doi style]
  (:body
   (client/get
    (str "https://doi.org/" doi)
    {:accept (format "text/x-bibliography; style=%s" style)
     })))

(u/defn-memoized doi-citation
  [doi]
  (subs (doi-text doi "nature") 2))     ;remove the "1." prefix


