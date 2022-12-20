(ns org.parkerici.enflame.sparql.generate
  (:require [org.parkerici.enflame.schema :as schema]
            [org.parkerici.enflame.blockdefs :as blockdefs]
            [org.parkerici.multitool.core :as u]
            [clojure.string :as str])
  )

#_
(schema/set-schema (org.parkerici.enflame.config/read-schema nil))


;;; Pasted from candel and should be folded

(def varcounter (atom {}))

;;; → Multitool
(defn safe-name [thing]
  (when #?(:clj (instance? clojure.lang.Named thing)
           :cljs (.-name thing))
    (name thing)))

(defn s [thing]
  (or (safe-name thing)
      (str thing)))

(defn symbol-conc
  [& things]
  (symbol (apply str (map s things))))

(defn ?var [kind]
  (swap! varcounter update kind #(inc (or % 0)))
  (symbol (str "?" (name kind) (get @varcounter kind))))

(defn reset-vars []
  (reset! varcounter {}))

(defn var-kind
  [var]
  (keyword (re-find #"\D*" (subs (name var) 1))))

(defn query-value
  [blockspec blockdef field]
  (let [field-def (some #(and (= (:name %) field) %) (get blockdef :args0))
        raw (get-in blockspec [:children field])]
    (cond (= :boolean (get blockdef :input))
          (get {"true" true "false" false "any" :any} raw)
          (= "field_dropdown" (:type field-def))
          ;; TODO randomly started acting weird, hence this 2-pronged approach
          (or (get (into {} (:options field-def)) raw)
              (keyword raw))
          (= raw "*") :any
          :else
          raw)))

(defn generate-regex
  [comp value]
  (case comp
    :contains (str ".*" value ".*")
    :starts-with (str "^" value ".*")
    :ends-with (str  ".*" value "$")))

;;; From CANDEL, but modified

#_
(defn pull-include
  [var]
  (let [kind (var-kind var)
        label (kind-label kind)]
    (if label
      [:db/id label]
      [:db/id])))

#_
(defn find-term
  [var type]
  (u/de-ns
   (case (or type :include)             ;default is :include
     :omit nil
     :include `(pull ~var ~(pull-include var))
     ;; TODO would like to eliminate */uid atts, but that makes query bigger and uglier
     ;; :pull `(pull ~var [* ~@(pull-subentity-names var)])
     :count `(count-distinct ~var))))



(defn label-var
  [base-var]
  (symbol-conc base-var "Label"))



(defn select-terms
  [var type]
   (case (or type :include)             ;default is :include
     :omit nil
     :include (list var (label-var var))
     :count `(count-distinct ~var)      ;TODO
     ))

;;; Actual

;;; TODO copypasta from candel.query, could be abstracted up
(defmulti build-query (fn [_ blockspec]
                        (-> blockspec
                            :type
                            blockdefs/block-def
                            (or (throw (ex-info "No such block type" {:type (:type blockspec)})))
                            ;; TODO Or :type ?
                            :query-builder)))


(def tap (atom nil))

(defn build-top-query
  [blockspec]
  #_ (reset-vars)
  (when blockspec
    (let [{:keys [filter where select] :as built}
          (build-query {} (assoc blockspec :top? true))
          base `(:bgp ~@where)
          filtered (if-not (empty? filter)
                     `(:filter ~@filter
                              ~base)
                     base)
          ;; TODO throwing away pulls, need to implement those some other way
          vars (map #(if (seq? %) (second %) %) select)
          ]
      (reset! tap built)
      ;; TODO
      `(:project ~vars ~filtered)
      )))

(defn spec-block-def [spec]
  (-> spec
      :type
      blockdefs/block-def))

(defn kind-label
  [kind]
  :rdfs/label)

(defmethod build-query :query-builder-query
  [{:keys [current-var] :as _query} {:keys [top?] :as blockspec}]
  (let [{:keys [output]} (spec-block-def blockspec)
        output (keyword output)
        output-var (or current-var (?var output))
        output-rdf-type (:uri (schema/kind-def output))
        constraints (blockdefs/listify (get-in blockspec [:children "constraint"])) 
        ;; Not rdf/type, its pull etc
        output-type (keyword (get-in blockspec [:children "output"])) ;oneof :include :pull :count etc.
        subqueries (map (partial build-query {:current-var output-var}) constraints)
        subquery-selects (mapcat :select subqueries)
        subquery-wheres (mapcat :where subqueries)
        type-where `[~output-var :rdf/type ~output-rdf-type]
        base-wheres (cons type-where subquery-wheres)
        subquery-filters (mapcat :filter subqueries)
        base-query
        ;; Do not understand this
        {:select (concat (select-terms output-var output-type)
                         subquery-selects)
         :where (if-let [label-attribute
                         (and top?
                              (empty? subquery-wheres)
                              (kind-label output))]
                  (cons [output-var label-attribute (label-var output-var)] base-wheres)
                  base-wheres)
         :filter subquery-filters
         :current-var output-var
         }]
    base-query))


(defmethod build-query :query-text-field
  [{:keys [current-var] :as query} blockspec]
  (let [{:keys [attribute] :as blockdef} (spec-block-def blockspec)
        value (query-value blockspec blockdef "V") 
        var (?var (:attribute blockdef)) ;may not be used and uses up a number...
        comp (query-value blockspec blockdef "comp")]
    ;; would be better expressed with merge-recursive
    (-> query
        (update :where concat
                (cond (= value :any)
                      [[current-var attribute var]]
                      (= comp :is)
                      [[current-var attribute value]]
                      :else             ;its a regex of some kind
                      [[current-var attribute var]])
                )
        (update :filter concat
                (when true ; -not (= comp :is)
                  (u/de-ns
                   `[(regex ~var ~(generate-regex comp value) "")])))

        ;; TODO prob wrong
        (update :find concat
                ;; NOTE: this includes the variable in the result unless it is an equality compare
                ;; TODO Might make sense to not do this if parent query is :omit
                (if (and (= :is comp) 
                         (not (= value :any)))
                  []
                  [var])))))


;;; This is unchanged from CANDEL, FYI
(defmethod build-query :query-entity-field
  [query blockspec]
  (let [{:keys [attribute invert?] :as _blockdef} (spec-block-def blockspec)]
    (if-let [value-block (get-in blockspec [:children "V"])]
      (let [subquery (build-query {} value-block)]
        (update subquery :where conj (if invert?
                                       [(:current-var subquery) attribute (:current-var query)]
                                       [(:current-var query) attribute (:current-var subquery)])))
      query)))


;;; Testing

(def c1 {:type "Gene_query",
 :children
 {"output" "include",
  "constraint"
  {:type "Gene_locusName", :children {"comp" "starts-with", "V" "C"}}}})


#_
(build-top-query c1)
