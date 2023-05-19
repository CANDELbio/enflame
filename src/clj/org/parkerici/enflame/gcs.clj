(ns org.parkerici.enflame.gcs
  (:require [org.parkerici.enflame.config :as config]
             [org.parkerici.multitool.core :as u]
            )
  (:import (com.google.cloud.datastore
            DatastoreOptions Datastore Entity Key StringValue 
            Query StructuredQuery$PropertyFilter StructuredQuery$OrderBy StructuredQuery$CompositeFilter)))

;;; Interface to GCS Datastore service

;;; TODO this is very bad. A little less bad made lazy
(u/def-lazy service
  (.getService (-> (DatastoreOptions/newBuilder)
                   (.setProjectId (config/config :library :gcs-project))
                   (.build))))

(u/defn-memoized key-factory
  [kind]
  (-> (.newKeyFactory @service)
      (.setKind kind)))

(defn get-entity-key
  [kind ent-id]
  (-> (.newKeyFactory @service)
      (.setKind kind)
      (.newKey ent-id)))

(defn delete-entity
  [ent-id]
  (.delete @service (into-array [(get-entity-key ent-id)])))

(defn to-map
  [ent]
  (let [names (set (.getNames ent))]
    (into {:entityId (.getId (.getKey ent))}
          (map #(vector (keyword %)
                        (.get (.getValue ent %)))
               names))))

(defn get-item
  [kind ent-id]
  (let [k (get-entity-key kind ent-id)]
    (when k 
      (to-map (.get @service k)))))

;;: TODO parameterize query maybe
(defn list-items
  [kind]
  (let [query (-> (Query/newEntityQueryBuilder)
                  (.setKind kind))]
    (map to-map
         (iterator-seq
          (.run @service (.build query))))))

(defn latest-item
  [kind field]
  (let [query (-> (Query/newEntityQueryBuilder)
                  (.setKind kind)
                  (.setOrderBy (StructuredQuery$OrderBy/desc field) (into-array StructuredQuery$OrderBy []))
                  (.setLimit (int 1)))]
    (to-map
     (first
      (iterator-seq
       (.run @service (.build query)))))))

(defn all-items
  [kind]
  (let [query (-> (Query/newEntityQueryBuilder)
                  (.setKind kind)
                  )]
    (map to-map
      (iterator-seq
       (.run @service (.build query))))))

;;; Not presently called
(defn upload-property-names
  [fields]
  (let [key-factory (key-factory "PropertyName")
        v (map name (keys fields))
        entities (map #(.build (Entity/newBuilder (.newKey key-factory %))) v)]
    (.put @service (into-array entities))))

(defn big-string [s]
  (-> s
      StringValue/newBuilder
      (.setExcludeFromIndexes true)
      (.build)))

(defn add-fields-to-entity
  [ent-builder item big-keys]
  (doseq [[att val] item]
    (when-not (u/nullish? val)
      (let [val (if (get big-keys att) ;TODO this is kind of ugly
                  (big-string val)
                  val)]
        (.set ent-builder (name att) val))))
  ent-builder)

(defn upload
  [kind item big-keys]
  (let [key (->> (.newKey (key-factory kind))
                 (.allocateId @service))
        builder (-> (Entity/newBuilder key)
                    (add-fields-to-entity item big-keys))]
    (.add @service (.build builder))))

;;; Datastore does not support OR or IN operatore, so we fake it
;;; Warning: this does a crossproduct on all multiple-valued prop filters, can be expensive
(defn query-from-map
  "Do a single query. Propvals is map of property names to string values"
  [kind propvals]
  (let [property-filters (and (not (empty? propvals))
                              (map (fn [[prop val]]
                                     (StructuredQuery$PropertyFilter/eq (name prop) val))
                                   propvals))
        query (-> (Query/newEntityQueryBuilder)
                  (.setKind kind))]
    (when property-filters
      (.setFilter query property-filters))
    (into [] (iterator-seq
              (.run @service (.build query))))))
