(ns org.parkerici.enflame.datomic-client
  (:require [datomic.client.api :as d]
            [taoensso.timbre :as log]
            [org.parkerici.multitool.core :as u]
            [org.parkerici.enflame.config :as config]
            ))

;;; TODO do these at a reasonable time

(def client (d/client (config/config :source :config)))

;;; OK probably we want to handle multiple databases
(def conn (d/connect client {:db-name "pici0044-5"})) ;; (config/config :source :db-name)}))

(defn query
  [db query args]              
  (prn :query db query args)
  (d/q {:query query :args (cons (d/db conn) args)}))
