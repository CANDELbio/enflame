(ns org.parkerici.enflame.results
  (:require [org.parkerici.enflame.schema :as schema]
            [org.parkerici.enflame.candel.query :as query]
            [clojure.set :as set]))

;;; TODO this is pretty datomic specific, probably belings in .candel?

;;; Tools for reshaping raw Datomic results into display format.

(defn entity-id?
  [thing]
  (and (number? thing)
       (> thing 10000000000000)))

(defn infer-kind
  [ent & [column]]
  (or (:kind ent)
      (some #(and (not (= (namespace %) "db"))
                  (keyword (namespace %)))
            (keys ent))
      (and (keyword? column)
           (schema/attribute-kind column))
      (and (and (list? column) (= 'pull (first column)))
           (query/var-kind (second column)))))

(defn- label-entity
  "full? means: don't get rid of the now-redundant label field"
  [ent full?]
  (if-let [kind (:kind ent)]
    (if-let [label-field (schema/kind-label kind)]
      (if-let [label (get ent label-field)]
        (-> ent
            (assoc :label/label label)
            (dissoc (if full? nil label-field)))
        ent)
      ent)
    ent))

(defn regularize-entity
  "To regularize an entity means to add :kind and :label attributes when possible, for the given entity and any contained sub-entities."
  [entity column idents] 
  (if (map? entity)
    (let [kind (infer-kind entity column)
          ident (get idents (:db/id entity))]
      (cond
        ;; Maybe its an ident
        ident
        (assoc entity :label/label (name ident))
        kind
        (let [augmented
              (-> entity
                  (assoc :kind kind)
                  (label-entity (= column :browser)))]
          ;; Walks down contained entities
          (reduce-kv (fn [m k v] (assoc m k
                                        (cond (sequential? v)
                                              (map #(regularize-entity % k idents) v) ;TODO k is probably not right, need kind OR make infer-kind smarter
                                              (map? v)
                                              (regularize-entity v k idents)
                                              :else v)))
                     {}
                     augmented))
        :else
        entity))
    entity))


;;; String to prefix top entity column headings
;;; hack: the leading space makes these columns more leftmost (per-kind)
;;; Used to use the more interesting " ⬥ " but that breaks Vega.
(def top-entity-prefix " * ")

(defn reshape-top-entity
  "Adds a kind and  '* <type>' column to represent self"
  [top]
  (if-let [kind (or (:kind top) (infer-kind top))]
    (-> top
        (assoc (keyword (name kind) (str top-entity-prefix (name kind)))
               (assoc (select-keys top [:db/id :label/label]) :kind kind))
        (assoc :kind kind)
        )
    top))
  
(defn reshape-count-item [val var]
  (let [kind (query/var-kind var)]
    {(keyword (name kind) (str top-entity-prefix (name kind) " count")) val}))

(defn reshape-top-item
  "Reshaping a top item does various type- and column-dependent things."
  [entity qcol idents]
  (cond (map? entity)
        (-> (regularize-entity entity qcol idents)
            reshape-top-entity
            ;; Remove generic columns that don't merge
            (dissoc :kind :db/id :pret.import/most-recent))
        (and (list? qcol) (= 'count-distinct (first qcol)))
        (reshape-count-item entity (second qcol))
        (entity-id? entity)
        (if-let [ident (get idents entity)]
          {(keyword (str qcol))
           (name ident)}
          entity)
        :else
        {(keyword (str qcol)) entity}))
      
;;; TODO smarter, so it generats :foo/bar2 instead of :foo/bar11
;;; TODO this whole hack breaks column sorting as well.
(defn keyword-inc [k]
  (keyword (namespace k) (str (name k) 1)))

;;; Should be pluggable, this is very specific
(defn- generate-unique [key existing]
  (let [candidate (keyword-inc key)]
    (if (contains? existing candidate)
      (generate-unique candidate existing)
      candidate)))

(defn- pseudoassoc [m k v]
  (if (contains? m k)
    (assoc m (generate-unique k m) v)
    (assoc m k v)))

(defn pseudomerge
  "Akin to core/merge, but generates new unique keys for duplicates, rather than overwriting"
  ([a] a)
  ([a b]
   (reduce-kv (fn [acc k v]
                (pseudoassoc acc k v))
              a b))
  ([a b & rest]
   (reduce pseudomerge a (cons b rest))))


(defn reshape-row
  "Reshaping a results row consists of reshaping all of the items in that row, then merging them. "
  [row idents query-cols]
  (apply
   pseudomerge
   (map #(reshape-top-item %1 %2 idents) row query-cols)))

(defn reshape-results
  [results idents query-cols]
  (map #(reshape-row % idents query-cols) results))

(defn result-columns
  [results]
  (when (not (empty? results))
    (reduce set/union
            (map #(set (remove (fn [k] (or (= "label" (namespace k))
                                           (= "uid" (name k))))
                               (keys %)))
                 results))))
