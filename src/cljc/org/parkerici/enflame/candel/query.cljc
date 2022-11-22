(ns org.parkerici.enflame.candel.query
  (:require [org.parkerici.enflame.schema :as schema]
            [org.parkerici.enflame.blockdefs :as blockdefs]
            [org.parkerici.multitool.core :as u]
            [clojure.string :as str]))

;;; ⊓⊔⊓⊔ Query logic ⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔

(def varcounter (atom {}))

(defn ?var [kind]
  (swap! varcounter update kind #(inc (or % 0)))
  (symbol (str "?" (name kind) (get @varcounter kind))))

(defn reset-vars []
  (reset! varcounter {}))

(defmulti build-query (fn [_ blockspec]
                        (-> blockspec
                            :type
                            blockdefs/block-def
                            (or (throw (ex-info "No such block type" {:type (:type blockspec)})))
                            :query-builder)))

;;; So non-query blocks dont blow up (TODO bit of a kludge)
(defmethod build-query :default
  [query _blockspec]
  query)

(defn build-top-query
  [blockspec]
  (reset-vars)
  (when blockspec
    (-> (build-query {} (assoc blockspec :top? true))
        (update :find #(remove nil? %))  ;clean nils from :find
        (dissoc :current-var)
        )))

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

;;; Builders for various different blocks (dispatched via :query-builder prop on blockdef). 

(defn variable? [thing]
  (and (symbol? thing)
       (= \? (first (name thing)))))

(defn spec-block-def [spec]
  (-> spec
      :type
      blockdefs/block-def))

(defmethod build-query :query-primitive-field
  [{:keys [current-var] :as query} blockspec]
  (let [{:keys [attribute] :as blockdef} (spec-block-def blockspec)
        value (query-value blockspec blockdef "V")
        value (if (= :any value) (?var attribute) value)
        query-from (if (variable? value)
                     (update query :find conj value)
                     query)]
    (update query-from :where conj [current-var attribute value])))

(defmethod build-query :query-numeric-field
  [{:keys [current-var] :as query} blockspec]
  (let [{:keys [attribute] :as blockdef} (spec-block-def blockspec)
        comp (symbol (query-value blockspec blockdef "comp"))
        raw (query-value blockspec blockdef "V")]
    (if (and (= comp '=) (not (= raw :any)))
      (-> query
          (update :where conj
                  [current-var attribute (u/coerce-numeric raw)]))
      (let [var (?var (:attribute blockdef))]
        (-> query
            (update :where concat
                    (if (= raw :any)
                      `[[~current-var ~attribute ~var]]
                      `[[~current-var ~attribute ~var]
                        [(~comp ~var ~(u/coerce-numeric raw))]]))
            (update :find conj var))))))

(defn generate-regex
  [comp value]
  (case comp
    :contains (str ".*" value ".*")
    :starts-with (str "^" value ".*")
    :ends-with (str  ".*" value "$")))

(defmethod build-query :query-text-field
  [{:keys [current-var] :as query} blockspec]
  (let [{:keys [attribute] :as blockdef} (spec-block-def blockspec)
        value (query-value blockspec blockdef "V") 
        var (?var (:attribute blockdef)) ;may not be used and uses up a number...
        comp (query-value blockspec blockdef "comp")]
    (-> query
        (update :where concat
                (cond (= value :any)
                      [[current-var attribute var]]
                      (= comp :is)
                      [[current-var attribute value]]
                      :else
                      (u/de-ns
                       `[[~current-var ~attribute ~var]
                         [(re-pattern ~(generate-regex comp value)) ?regex] ;TODO need to gensym regex var
                         [(re-find ?regex ~var)]])))
        (update :find concat
                ;; NOTE: this includes the variable in the result unless it is an equality compare
                ;; TODO Might make sense to not do this if parent query is :omit
                (if (and (= :is comp) 
                         (not (= value :any)))
                  []
                  [var])))))

(defmethod build-query :query-tuple-field
  [{:keys [current-var] :as query} blockspec]
  (let [{:keys [attribute] :as blockdef} (spec-block-def blockspec)
        value (query-value blockspec blockdef "V") 
        elts (vec (str/split value #"\|"))]
    (-> query
        (update :where concat
                [[current-var attribute elts]])
        )))

(defmethod build-query :query-entity-field
  [query blockspec]
  (let [{:keys [attribute invert?] :as _blockdef} (spec-block-def blockspec)]
    (if-let [value-block (get-in blockspec [:children "V"])]
      (let [subquery (build-query {} value-block)]
        (update subquery :where conj (if invert?
                                       [(:current-var subquery) attribute (:current-var query)]
                                       [(:current-var query) attribute (:current-var subquery)])))
      query)))

(defn kind-attributes
  "All dataomic attributes for a kind, including :db/id"
  [kind]
  (cons :db/id
        (for [[field _props] (schema/kind-fields kind)]
          (keyword (name kind) (name field)))))

(defn kind-subentity-names
  [kind]
  (for [[field props] (schema/kind-fields kind)
        :let [sub-kind (get props :type)
              sub-kind-label-attribute (schema/kind-label sub-kind)]
        :when sub-kind-label-attribute]
    `{~(keyword (name kind) (name field))
      [:db/id ~sub-kind-label-attribute]}))

;; Total crock – will break if there are ever any kind names with digits, which could obviously happen
(defn var-kind
  [var]
  (keyword (re-find #"\D*" (subs (name var) 1))))

(defn pull-subentity-names
  [var]
  (kind-subentity-names (var-kind var)))

(defn pull-include
  [var]
  (let [kind (var-kind var)
        label (schema/kind-label kind)]
    (if label
      [:db/id label]
      [:db/id])))

(defn find-term
  [var type]
  (u/de-ns
   (case (or type :include)             ;default is :include
     :omit nil
     :include `(pull ~var ~(pull-include var))
     ;; TODO would like to eliminate */uid atts, but that makes query bigger and uglier
     :pull `(pull ~var [* ~@(pull-subentity-names var)])
     :count `(count-distinct ~var))))

;;; belongs elsewhere
(def primitive-types #{:string :boolean :float :double :long :bigint :bigdec :instant :keyword :uuid})

(defn ref-type?
  [type]
  (not (contains? primitive-types type)))

;;; This way-too-hairy code will transform a query in which some return vars are tuples into an untupled equivalent
;;; that works better with display (but will return more rows since multi-valued tuple slots get expanded).

;;; Remove the tuple itself from the query results
(defn remove-tuple-itself
  [from var tuple-attribute]
  (map (fn [clause]
         (if (= clause (list 'pull var '[*]))
           (list 'pull var (into [] (disj (set (kind-attributes (var-kind var))) tuple-attribute)))
           clause))
       from))
           
;;; Transforms the query so that tuple elts get their own result var
(defn modify-for-tuple-field
  [query field-id field-def var]
  (let [main-var (:current-var query)
        subtypes (:type field-def)
        subvars (map ?var subtypes)
        tuple-attribute (keyword (name var) (name field-id))
        new-find-clauses
        (map (fn [type var]
               (if (ref-type? type)
                 `(pull ~var ~(pull-include var))
                 var))
             subtypes
             subvars)
        new-where-clauses
        `[[~main-var ~tuple-attribute ?tuple]
          [(untuple ?tuple) ~subvars]]]
    (u/de-ns
     (-> query
         (update :find remove-tuple-itself main-var tuple-attribute)
         (update :find concat new-find-clauses)
         (update :where concat new-where-clauses)
         ;; vectorize both clauses for cosmetic purposes
         (update :find vec)
         (update :where vec)
         ))))

;;; Detect a tuple query
(defn tuple-hack
  [query output output-type]
  (if (= :pull output-type)
    (let [kind (schema/kind-def output)
          [tuple-field-id tuple-field] (u/some-thing (fn [[_ v]] (vector? (:type v))) (:fields kind))]
      (if tuple-field
        (modify-for-tuple-field query tuple-field-id tuple-field output)
        query))
    query))

;;; Another hack for layer blocks
;;; just pass down to the actual query
(defmethod build-query :query-builder-layer [query blockspec]
  (let [real-query-block (get-in blockspec [:children "data"])]
    (build-query query (assoc real-query-block :top? true))))

(defmethod build-query :query-builder-query
  [{:keys [current-var] :as _query} {:keys [top?] :as blockspec}]
  (let [{:keys [output]} (spec-block-def blockspec)
        output (keyword output)
        output-var (or current-var (?var output))
        constraints (blockdefs/listify (get-in blockspec [:children "constraint"])) 
        output-type (keyword (get-in blockspec [:children "output"])) ;oneof :include :pull :count etc.
        subqueries (map (partial build-query {:current-var output-var}) constraints)
        subquery-finds (mapcat :find subqueries)
        subquery-wheres (mapcat :where subqueries)
        base-query
        {:find (if-let [term (find-term output-var output-type)]
                 (conj subquery-finds term)
                 subquery-finds)
         :where (if-let [label-attribute
                         (and top?
                              (empty? subquery-wheres)
                              (schema/kind-label output))]
                  [[output-var label-attribute (?var label-attribute)]] ;TODO not sure about this
                  subquery-wheres)
         :current-var output-var
         }
        ]
    (tuple-hack base-query output output-type)))


(defn andify [clauses]
  (if (> (count clauses) 1)
    (conj clauses 'and)
    (first clauses)))

(defmethod build-query :query-builder-or
  [{:keys [current-var] :as query} blockspec]
  (let [constraints (blockdefs/listify (get-in blockspec [:children "constraint"])) 
        clauses (map (fn [constraint]
                       (-> (build-query (select-keys query [:current-var]) constraint)
                           :where
                           andify))
                     constraints)]
    (-> query
        (update :where conj
                ;; If there are other variables in the clauses besides main one, then you need to use or-join
                (if (> (count (distinct (filter variable? (flatten clauses)))) 1)
                  (cons 'or-join (cons [current-var] clauses))
                  (cons 'or clauses))))))
    
(defmethod build-query :query-named-entity
  [_query {:keys [top?] :as blockspec}]
  (let [{:keys [output attribute] :as blockdef} (spec-block-def blockspec)
        output-var (?var output)
        value (query-value blockspec blockdef "V")]
    {:find (list (find-term output-var (if top? :pull :omit))) ; do a pull if this is the top level query, omit otherwise
     :where (list [output-var attribute value])
     :current-var output-var}))

;;; ⧀⧁⧁ Complex relations ⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁⧀⧁

;;; This works by
;;; - identifing the input and the next-to-input variables,
;;; - fudging a block for that relationship and calling build query on it
;;; - substituting vars in the rest of the query template
;;; which sounds complicated but in fact a lot simpler than the previous code

(defmethod build-query :query-builder-complex
  [{:keys [current-var] :as _query} blockspec]
  (let [{:keys [template input output*] :as _blockdef} (spec-block-def blockspec)
        input-clause (some #(and (or (= (nth % 2) input) (= (nth % 0) input)) %) template) ;TODO half-implemented inverse?
        other-clauses (remove #(= % input-clause) template)
        penultimate (if (= input (first input-clause))
                      (nth input-clause 2)
                      (first input-clause))
        input-pseudo-type (str (name penultimate) "_" (name input))
        input-clause-var (?var penultimate)
        base (build-query {:current-var input-clause-var} (assoc blockspec :type input-pseudo-type))
        sub-template (u/substitute-gen other-clauses
                                  {; input input-var
                                   penultimate input-clause-var
                                   output* current-var}
                                  #(if (schema/kind? %)
                                     (?var %)
                                     %))]
    (update base
            :where
            concat sub-template)))


;;; Browser support

(defn entity-pull-query
  [kind]
  (u/de-ns `{:find ((pull ?e [* ~@(kind-subentity-names kind)])) :in ($ ?e)}))
