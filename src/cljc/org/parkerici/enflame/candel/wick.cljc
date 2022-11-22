(ns org.parkerici.enflame.candel.wick
  (:require [clojure.string :as str]))

;;; Convert a Clojure-syntax query (gaslight format) to Wick
;;; This is pretty janky

(defn lines
  [lines]
  (str/join "\n" lines))

(defn comma-lines
  [lines]
  (str/join ",\n" lines))

(declare wickify)

(defn r-vector
  [items]
  (str "c("
       (str/join ", " (map wickify items))
       ")"))

(defn r-map-entry
  [[k v]]
  (str (wickify k) " = " (wickify v)))

(defn r-map
  [m]
  (str "{"
       (str/join ", " (map r-map-entry m))
       "}"))

(defn namespaced-key
  [nsk]
  (str (namespace nsk) "/" (name nsk)))

(defn wickify
  [thing]
  (cond (vector? thing)
        (r-vector thing)
        (map-entry? thing)
        (r-map-entry thing)
        (map? thing)
        (r-map thing)
        (keyword? thing)
        (namespaced-key thing)
        (= '* thing) "."
        :else (str thing)))

(defn find-clause
  [[_ var spec]]
  (str " find(pull("
       var
       ", "
       (wickify spec)
       ")),"))

(defn where-value
  [thing]
  (cond (keyword? thing)
        (str (namespace thing) "/" (name thing))
        (string? thing)
        (str \" thing \")               ;ok dumb
        (boolean? thing)
        (if thing "TRUE" "FALSE")
        ;; Assumes this is always an infix op, which may or not be valid
        (and (list? thing) (fn? (eval (first thing))))
        (str (second thing) " " (first thing) " " (nth thing 2))
        :else
        thing))

(defn where-subclause
  [ws]
  (str "  d("
       (str/join ", " (map where-value ws))
       ")"))

(defn where-clause
  [w]
  (str " where(\n"
        (comma-lines (map where-subclause w))
        "\n ))"))

(defn translate
  [query]
  (lines
   `("query("
     ~(find-clause (first (:find query))) ; wick can only handle a single pull, 
     ~(where-clause (:where query))
     )))
