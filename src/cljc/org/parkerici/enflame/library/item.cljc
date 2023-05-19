(ns org.parkerici.enflame.library.item
  (:require [clojure.spec.alpha :as s]
            [org.parkerici.multitool.core :as u]
            ))

(s/def ::item
  (s/keys :req-un [::blockdef           ;the raw xml from blockly
                   ::query               ;automatically generated text version of query
                   ::author
                   ::date-created]
          :opt-un [::entityId            ;added on retrieval
                   ::description         ;user-generated text desc
                   ::input-kinds
                   ::label
                   ::image
                   ::output-kind]))

(defn item-keys
  []
  (let [map (apply hash-map (rest (s/describe ::item)))]
    (concat (:req-un map) (:opt-un map))))

(defn item-label
  [item]
  (or (::label item) (::description item) (::query item)))

#_(def big-keys #{::blockdef ::image})


;;; Convert from gcs
(defn localize-item
  [m]
  (u/map-keys #(keyword "org.parkerici.enflame.library.item" (name %)) m))
