(ns org.parkerici.enflame.download
  (:require [clojure.data.csv :as csv]
            [org.parkerici.enflame.datomic-relay :as d]
            [org.parkerici.enflame.results :as results]
            ))

;;; This code basically replicates the table and render code from views.cljs, and should be kept in sync with changes there.

(defn render-entity
  [thing]
  (str (or (:label thing)
           (:db/id thing))))

;;; Should parallel views/render
(defn render
  [thing idents]
  (cond (sequential? thing)
        (pr-str (map #(render % idents) thing))
        (map? thing)
        (or (get idents (:db/id thing))
            (render-entity thing))
        :else
        (str thing)))

(defn export-results
  [ddb results query candelabra-token config]
  (let [columns (:find query)
        idents (d/idents ddb candelabra-token config)
        reshaped (results/reshape-results results idents columns)
        reshaped-cols (results/result-columns reshaped)
        csv-data (cons reshaped-cols
                       (map (fn [row]
                              (map (fn [col]
                                     (render (get row col) idents))
                                   reshaped-cols))
                            reshaped))]
    (with-out-str
      (csv/write-csv *out* csv-data
                     :separator \tab))))
