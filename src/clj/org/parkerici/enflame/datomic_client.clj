(ns org.parkerici.enflame.datomic-client
  (:require [datomic.client.api :as d]
            [taoensso.timbre :as log]
            [org.parkerici.multitool.core :as u]
            [org.parkerici.enflame.config :as config]
            ))

;;; TODO do these at a reasonable time

(u/def-lazy client (d/client (config/config :source :config)))

;;; OK probably we want to handle multiple databases
(u/defn-memoized conn
  [db]
  (d/connect @client {:db-name db}))

(defn query
  [db query args]              
  (d/q {:query query :args (cons (d/db (conn db)) args)}))

(defn dbs
  []
  (d/list-databases @client {}))
  
