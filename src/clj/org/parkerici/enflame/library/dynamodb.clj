(ns org.parkerici.enflame.library.dynamodb
  (:require [cognitect.aws.client.api :as aws]
            [org.parkerici.enflame.library.item :as item]
            [org.parkerici.enflame.config :as config]
            [org.parkerici.multitool.core :as u]
            )
  )

(def dyna (aws/client {:api :dynamodb}))

;;; Invoke with error handling
(defn invoke-with-eh
  [arg-map]
  (let [resp (aws/invoke dyna arg-map)]
    (when (:cognitect.anomalies/category resp)
      (throw (ex-info "DynamoDB exception" {:arg-map arg-map :resp resp})))
    resp))

;;; When debugging, might want to Turn off the magic
#_
(defn invoke-with-eh
  [arg-map]
  (aws/invoke dyna arg-map))

(defn table
  []
  (config/config :library :table))

;;; Delete the table – beware, data loss!
(defn delete-table
  []
  (invoke-with-eh
   {:op :DeleteTable
    :request {:TableName (table)}}))

(defn create-table
  []
  (invoke-with-eh
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
        (invoke-with-eh {:op :Scan :request {:TableName (table)}}))))

(defn to-item
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
  (invoke-with-eh
   {:op :PutItem
    :request {:TableName (table)
              :Item (to-item thing)}})  )

(defn get-item
  [k]
  (-> (invoke-with-eh {:op :GetItem :request {:TableName (table) :Key {"entityId" {:S k}}}})
      :Item
      from-item))
  
