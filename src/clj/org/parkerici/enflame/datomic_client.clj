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



(defn dbs
  []
  (d/list-databases @client {}))
  
;;; Query optimizer
;;; Cute hack, and no idea why Datomic doesn't just do this

(u/defn-memoized db-stats
  [db]
  (d/db-stats (d/db (conn db))))

(defn db-att-count
  [db att]
  (get-in (db-stats db) [:attrs att :count] 0))

;;; Resorts where clauses so smaller searches come first
(defn optimize-query
  [db query]
  (update query :where
          (fn [clauses]
            (sort-by (comp (partial db-att-count db) second)
                     clauses))))

                     
(defn query
  [db query args]              
  (d/q {:query (optimize-query db query)
        :args (cons (d/db (conn db)) args)}))
