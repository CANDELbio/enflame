(ns org.parkerici.enflame.library.dynamodb
  (:require [cognitect.aws.client.api :as aws]
            [org.parkerici.enflame.library.item :as item]
            [org.parkerici.enflame.config :as config]
            [org.parkerici.multitool.core :as u]
            )
  )

(def dyna (aws/client {:api :dynamodb}))

(defn table
  []
  (config/config :library :table))

;;; Delete the table – beware, data loss!
(defn delete-table
  []
  (aws/invoke dyna
              {:op :DeleteTable
               :request {:TableName (table)}}))

(defn create-table
  []
  (aws/invoke dyna
              {:op      :CreateTable
               :request {:TableName             (table)
                         ;; Note: only key fields should be defined here
                         :AttributeDefinitions  [{:AttributeName "entityId"
                                                  :AttributeType "S"}
                                                 ]

                         :KeySchema             [{:AttributeName "entityId"
                                                  :KeyType       "HASH"}
                                                 ]
                         :ProvisionedThroughput {:ReadCapacityUnits  1
                                                 :WriteCapacityUnits 1}}}))


(defn from-item
  [item]
  (-> (into {}
            (map (fn [[k v]]
                   [(keyword "org.parkerici.enflame.library.item" (name k))
                    (or (:N v) (:S v))])
                 item))
      (update :org.parkerici.enflame.library.item/date-created
              #(-> % parse-long))))     ;view calls  java.util.Date.

(defn list-items
  []
  (map from-item
       (:Items
        (aws/invoke dyna {:op :Scan :request {:TableName (table)}}))))

(defn to-items
  [thing]
  (let [thang (assoc thing :entityId (str (java.util.UUID/randomUUID)))
        ks (keys thang)]
     (zipmap ks
            (map #(let [v (% thang)]
                    (cond (number? v)
                        {:N (str v)}
                        :else
                        {:S (str v)}))
                 ks))))

(defn upload
  [thing]
  (aws/invoke
   dyna
   {:op :PutItem
    :request {:TableName (table)
              :Item (to-items thing)}})  )

(defn get-item
  [k]
  (-> (aws/invoke dyna {:op :GetItem :request {:TableName (table) :Key {"entityId" {:S k}}}})
      :Item
      from-item))
  
