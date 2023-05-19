(ns org.parkerici.enflame.schema
  (:require [org.parkerici.multitool.core :as u]
            ))

;;; TODO Most of this should migrate to Alzabo

;;; Note: Here and elsewhere, "field" is a plain keyword property of an Alzabo kind,
;;; while "attribute" refers to a named and namespaced Datomic entity.

;;; Allow schema to be changed (would be cleaner to pass this as an arg, but laziness)
;;; try to avoid accessing this from outside the namespace.

;;; NOTE: if this changes, you need to also change the bin/test.sh script.

;;; TODO this doesn't support switching schemas very well. Have sort of decided that isn't necessary, can force a page relaod if we have to.
(defonce the-schema (atom nil))

(defn set-schema [schema]
  (prn :set-schema (:version schema))
  (reset! the-schema schema))

(defn schema-version []                 ;TODO deversion
  (:version @the-schema))

;;; Kinds

(defn kinds
  []
  (keys (:kinds @the-schema)))

(defn kind-def
  [k]
  (get (:kinds @the-schema) k))

(defn kind-fields
  [k]
  (get-in @the-schema [:kinds k :fields]))

(defn kind-field
  [k f]
  (get-in @the-schema [:kinds k :fields f]))

(defn kind? [k]
  (contains? (:kinds @the-schema) k))

(defn kind-unique-id [k]
  (when-let [att (get-in (:kinds @the-schema) [k :unique-id])]
    (keyword (name k) (name att))))

(defn kind-label [k]
  (when-let [att (get-in (:kinds @the-schema) [k :label])]
    (keyword (name k) (name att))))

;;; TODO Datomic specific
(defn attribute-kind
  "Get kind of a datomic namespaced att "
  [att]
  (get-in @the-schema [:kinds (keyword (namespace att)) :fields (keyword (name att)) :type]))

;;; TODO this is CANDEL/Datomic specific
(defn inverse-relations
  []
  (reduce (fn [acc [kind kdef]]
            (reduce (fn [acc [field fdef]]
                      (if (get (:kinds @the-schema) (:type fdef))
                        (update-in acc [(:type fdef) kind] ;wrong for sparql and I think wrong for CANDEL...
                                   conj
                                   (assoc fdef
                                          :type kind
                                          :attribute (keyword (name kind)
                                                              ;; previously prepended _ , but that doesn't work on gaslight
                                                              (name field))))
                        acc))
                    acc
                    (:fields kdef)))
          {}
          (:kinds @the-schema)))


;;; Enums

(defn enums
  []
  (keys (:enums @the-schema)))

(defn enum-def
  [e]
  (get (:enums @the-schema) e))



