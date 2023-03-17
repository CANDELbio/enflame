(ns org.parkerici.enflame.blockdefs
  (:require [clojure.string :as str]
            [inflections.core :as inflect]
            [org.parkerici.enflame.schema :as schema]
            #?(:cljs
               [org.parkerici.enflame.view.graph :as graph])
            [org.parkerici.multitool.core :as u]
            ))

;;; This is the core part of Enflame. This code does two main things: defines the block types, and
;;; provides the machinery to generate queries from block structure. Normally this runs in the
;;; browser but it's defined as cljc so that it can be debugged in a clj environment.

(declare block-def)

;;; ⊓⊔⊓⊔ Block type definitions ⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔

(defn svg-color [c]
  (let [{:keys [r g b]} c]
    #?
    (:cljs
    (str "#" (.substr (str "000000" (.toString (+ (Math/round b 1)
                                                  (* 256 (Math/round g 1))
                                                  (* 256 256 (Math/round r 1))) 16)) -6))
     :clj
     (format "#%02x%02x%02x" b g r))))

;;; Maximally distinct colors from http://vrl.cs.brown.edu/color
;;; TODO need better color theory

;;; TODO load these from config
(def kind-defined-color
  {:cell-type "#35618f",
   :therapy "#92a654",
   :measurement-set "#240e45",
   :clinical-trial "#5cb206",
   :nanostring-signature "#d003d6",
   :variant "#163719",
   :gene-product "#d867be",
   :genomic-coordinate "#1aa7ee",
   :cnv "#873073",
   :ctcae-adverse-event "#27b378",
   :comorbidity "#f63943",
   :tcr "#321201",
   :gdc-anatomic-site "#ce8d8d",
   :sample "#7a0910",
   :measurement "#f27e3a",
   :treatment-regimen "#453cc5",
   :epitope "#fb0998",
   :gene "#8a96f7",
   :protein "#6d4c2b",
   :timepoint "#3f16f9",
   :drug-regimen "#8f6bb0",
   :cell-population "#9001fb",
   :drug "#60a66e",
   :meddra-disease "#16316b",
   :chr-acc-reg "#764e8a",
   :clinical-observation "#bdc836",
   :subject "#636da2",
   :atac-peak "#a2da36",
   :assay "#444518", 
   :dataset "#47a44a", 
   :neo-antigen "#15b540"
   :clinical-observation-set "#ffcc33"
   }

  )

;;; Ag-grid needs css classes. Doing this by hand for now
#?
(:clj
 (defn write-kind-css
   []
   (doseq [kind (keys kind-defined-color)]
     (println (format ".%s-kind {background-color: %s;color: white;}"
                      (name kind)
                      (kind-defined-color kind))))))

(defn kind-color [kind]
  (or (kind-defined-color kind)
      ;; Will do something vaguely reasonable for unknown kinds (TODO would be good to control the color a bit)
      (str "#" (u/hex-string (mod (hash kind) 0xffffff)))))

(defn alzabo-url
  [kind]
  ;; TODO root shoud be configurable (client side)
  ;; TODO flush version, maybe have alt named schemas though
  (str "alzabo/schema/" (schema/schema-version) "/" (name kind) ".html"))

(def output-options
  [["name" "include"]                   ;was "entity", which is technically more correct but this is better for naive user
   ["everything" "pull"]
   ["count" "count"]
   ["omit" "omit"]])

(defn kind-query-blockdef
  [kind]
  {:type (str (name kind) "_query")
   :message0 (str (inflect/plural (name kind)) " where")
   :message1 "%1"
   :args1 [{:type "input_statement"
            :name "constraint"
            :check (str (name kind) "_constraint")}]
   :message2 "%1"                       ;TODO eliminated "output " to cutdown on visual clutter
   :args2 [{:type "field_dropdown"
            :name "output"
            :options output-options}]
   :output (name kind)
   :colour (kind-color kind)
   :helpUrl (alzabo-url kind)
   :query-builder :query-builder-query
   })

;;; TODO alt: "all genes" instead of "any gene"
(defn kind-any-blockdef
  [kind]
  {:type (str (name kind) "_any")
   :message0 (str "any " (name kind))
   :message1 "%1"
   :args1 [{:type "field_dropdown"
            :name "output"
            :options output-options}]
   :output (name kind)
   :colour (kind-color kind)
   :helpUrl (alzabo-url kind)
   :query-builder :query-builder-query
   })

;;; Define type-specific OR block
(defn kind-or-blockdef
  [kind]
  {:type (str (name kind) "_or")
   :message0 " or %1"
   :args0 [{:type "input_statement"
            :name "constraint"
            :check (str (name kind) "_constraint")}]
   :previousStatement (str (name kind) "_constraint")
   :nextStatement (str (name kind) "_constraint")
   :colour (kind-color kind)
   :helpUrl (alzabo-url kind)
   :query-builder :query-builder-or
   }
  )

(defn field-def-type
  "The type-dependent parts of a field blockdef"
  [field type]
  (case type                            ;TODO Maybe defmethod this
    :boolean
    {:message0 (str (name field) "? %1")
     :args0 [{:name "V"
              :type "field_dropdown"
              :options '[["yes" true] ["no" false] ["defined" any]]}]
     :query-builder :query-primitive-field}
    (:long :float)             
    {:message0 (str (name field) " %2 %1")
     :args0 [{:name "V" :type "field_input"}
             {:name "comp"
              :type "field_dropdown"
              :options (mapv (fn [x] [x x]) ["<" "<=" "=" "=>" ">" "!="])}]
     :query-builder :query-numeric-field}
    ;; Date is not built into blockly, and nothing important in CANDEL uses it, so....
    ;; https://developers.google.com/blockly/guides/create-custom-blocks/fields/built-in-fields/date
    #_
    :instant                          
    #_
    {:message0 (str (name field) " is %1")
     :args0 [{:name "V" :type "field_date"}]
     :query-builder :query-primitive-field}    
    (:string :ref :instant)                      ;TODO :ref is temp to cover some schema incompleteness. :instant also here just to avoid errors
    {:message0 (str (name field) " %2 %1")
     :args0 [{:name "V"
              :type "field_input"}
             {:name "comp"
              :type "field_dropdown"
              :options [["is" :is] ["contains" :contains] ["starts with" :starts-with] ["ends with" :ends-with]]}]
     :query-builder :query-text-field}

    ;; For :uid fields, mostly for blockify. Field is a | separated list of tuple elts
    {:* :string}
    {:message0 (str (name field) " is %1")
     :args0 [{:name "V"
              :type "field_input"}
             ]
     :query-builder :query-tuple-field}


    ;; default
    (cond
      (schema/enum-def type)
      {:message0 (str (name field) " is %1")
       :args0 [{:type "field_dropdown"
                :name "V"
                :options (conj (mapv (fn [[key text]] [text key]) (:values (schema/enum-def type)))
                               ["any" :any])}]
       :query-builder :query-primitive-field}

      (schema/kind-def type)
      {:message0 (str (name field) " is %1")
       :args0 [{:type "input_value"
                :check (name type)
                :name "V"}]
       :query-builder :query-entity-field}

      ;; TODO for now, no blocks generated for tuple types, but might do that in future
      ;; fixed-size tuples
      (vector? type) nil
      ;; var-size tuples
      (map? type) nil

      :else
      ;; shouldn't happen 
      (do (prn :error "unknown type" type)
          nil
          )
      #_ (throw (ex-info "unknown type" {:type type}))
      )))

;;; → schema
(defn field-def [kind field]
  (get-in (schema/kind-def kind) [:fields field]))


(defn base-block
  [kind field attribute]
  {:type (str (name kind) "_" (name field))
   :previousStatement (str (name kind) "_constraint")
   :nextStatement (str (name kind) "_constraint")
   :colour (kind-color kind)
   :helpUrl (alzabo-url kind)
   :attribute (or attribute (keyword (name kind) (name field)))
   :input (or (:type (field-def kind field))
              field)
   })


;;; eg :measurement :measurement-set {:type :measurement-set,    :cardinality :many,    :doc "The measurement values for this measurement set",    :attribute :measurement-set/measurements}
(defn kind-field-inverse-blockdef
  [kind {:keys [type attribute]}]
  (let [field type]
    (merge
     {:invert? true}
     (field-def-type field field)
     (base-block kind field attribute))))


(defn kind-field-forward-blockdef
  [kind field]
  ;; TODO this is wrong, some attributes are plural eg :measurement-set/measurements
  (let [{:keys [type attribute]} (field-def kind field)]
    (when-let [field-def (field-def-type field type)]
      (merge
       field-def
       (base-block kind field attribute)
       ))))




;;; ⊓⊔⊓⊔ Complex relations ⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔

;;; TODO check against schema?
;;; TODO measurement-assay
;;; TODO these are CANDEL specific and should part of the config
(def complex-constraints
  '[{:id :measurement_dataset
     :output :measurement
     :input :dataset
     :query
     ([:measurement-set :measurement-set/measurements :measurement]
      [:assay :assay/measurement-sets :measurement-set]
      [:dataset :dataset/assays :assay] )
     }
    {:id :variant_dataset
     :output :variant
     :input :dataset
     :query
     ([:measurement :measurement/variant :variant]
      [:measurement-set :measurement-set/measurements :measurment]
      [:assay :assay/measurement-sets :measurement-set]
      [:dataset :dataset/assays :assay] )
     }
    {:id :subject_measurement
     :output :subject
     :input :measurement
     :query
     ([:sample :sample/subject :subject]
      [:measurement :measurement/sample :sample]
      )}
    {:id :subject_variant
     :output :subject
     :input :variant
     :query
     ([:sample :sample/subject :subject]
      [:measurement :measurement/sample :sample]
      [:measurement :measurement/variant :variant]
      )}
    {:id :subject_treatment             ;TODO test
     :output :subject
     :input :treatment-regimen
     :query
     ([:subject :subject/therapies :therapy]
      [:therapy :therapy/treatment-regimen :treatment-regimen]
      )}
    {:id :subject_drug             ;TODO test
     :output :subject
     :input :drug
     :query
     ([:subject :subject/therapies :therapy]
      [:therapy :therapy/treatment-regimen :treatment-regimen]
      [:treatment-regimen :treatment-regimen/drug-regimens :drug-regimen]
      [:drug-regimen :drug-regimen/drug :drug]
      )}
    ;; TODO probably want similar things for most clinical observation fields
    ;; At least for some fields: metastatic, recist, responder, disease-stage...talk to Lacey
    ;; IOW clinical-observation is probably something a user never wants to think about directly
    {:id :subject_bmi 
     :output :subject
     :input :bmi
     :input-type :long                  ;TODO could infer this from schema probably
     :query
     ([:clinical-observation :clinical-observation/subject :subject]
      [:clinical-observation :clinical-observation/bmi :bmi]
      )}
    ])

;;; Any complex constraint with a non-primitive input can be inverted with very little change.
(def inverted-complex-constraints
  (filter
   identity
   (map (fn [{:keys [id input output input-type] :as constraint}]
         (when-not input-type          ;if input is nonprimitive
           (-> constraint
               (assoc :input output
                      :output input
                      :id (let [[_ a b] (re-matches #"(.*)_(.*)" (name id))] (keyword (str b "_" a)))))))
       complex-constraints)))
           
(defn complex-blockdef [{:keys [id input output query input-type]}]
  (let [input-type (or input-type input)]
    (-> (field-def-type input (or input-type input))
        (merge
         {:type (name id)
          :previousStatement (str (name output) "_constraint")
          :nextStatement (str (name output) "_constraint")
          :colour (kind-color output)
          :helpUrl (alzabo-url output)
          :query-builder :query-builder-complex
          :template query
          :input input
          :output* output              ;Can't use :output because that has a blockly-defined meaning
          :input-type input-type
          })
        (update :message0 #(str "⨷ " %)))))

(defn kind-complex-blockdefs [kind]
  (map complex-blockdef
       (filter #(= kind (:output %)) (concat complex-constraints
                                             inverted-complex-constraints))))
(defn labelize
  "Convert - and _ to spaces"
  [s]
  (str/replace (name s) #"[\_\-]" " "))

;;; ⊓⊔⊓⊔  Putting it all together ⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔⊓⊔

(defn kind-blockdefs
  [kind]
  (concat
   (list (kind-any-blockdef kind)
         (kind-query-blockdef kind))
   (->>
    (concat 
     ;; forward relations
     (map (fn [field]
            (kind-field-forward-blockdef kind field))
          (keys (schema/kind-fields kind)))
     ;; inverse relations
     (map (fn [[other-kind [adef]]]
            (kind-field-inverse-blockdef kind adef))
          (kind (schema/inverse-relations)))
     (kind-complex-blockdefs kind))
    (filter identity)
    (sort-by :type))
   (list (kind-or-blockdef kind))
   ))

(defn schema-defs
  []
  (into []
        (concat #?(:cljs (graph/graph-blockdefs))
                (mapcat kind-blockdefs (schema/kinds)))))

;;; Takes version and memoizes so block generation only needs to be done once
;;; Means everything here is dependent on global state schema/version, which
;;; is unclojurish.
(def block-defs                         ;u/defn-memoized wasn't compiling properly
  (memoize
   (fn [version]                        ;TODO deversion
     (let [blocks (schema-defs)]
       (zipmap (map :type blocks) blocks))
     )))

(defn block-def
  [block-type]
  (get (block-defs @schema/the-schema) block-type))

(defn toolbox-def-block
  [type]
  (let [blockdef (block-def type)
        input-type (get-in blockdef [:args0 0 :type])
        ;; Add a default query block if appropriate
        content (when (= input-type "input_value")
                  (list [:value "V" (toolbox-def-block (str (name (:input blockdef)) "_query") )]))]
    `[:block ~type {} ~@content]))

(defn toolbox-def-kind
  [kind]
  (into []
        (map toolbox-def-block
             (map :type (kind-blockdefs kind)))))

;;; Makes a graph 
(defn graph-block-toolbox
  [block-type]
  (let [spec (block-def block-type)
        args (map :name (:args0 spec))]
    `[:block ~block-type {}
      ~@(map (fn [arg] [:value (name arg) [:block "text"]])
             args)]))

(defn toolbox-def
  []
  (let [defcat (fn [cname kinds]
                 `[:category ~cname {} ~@(mapv (fn [kind]
                                             `[:category ~(name kind) {:colour ~(kind-color kind)}
                                               ~@(toolbox-def-kind kind)])
                                           (sort kinds))])
        kgroups (group-by #(true? (get (schema/kind-def %) :reference?))
                          (schema/kinds))]
    [:toolbox
     (defcat "Experimental" (get kgroups false))
     (defcat "Reference" (get kgroups true))
     #?(:cljs
        (graph/visualize-toolbox))]))

(defn re-pattern-literal
  [s]
  #?(:clj  
     (re-pattern (java.util.regex.Pattern/quote s))
     :cljs
     (re-pattern (str/replace s #"([)\/\,\.\,\*\+\?\|\(\)\[\]\{\}\\])" "\\$1"))))

(defn template-subst
  [template sub-fn]
  (let [matches (re-seq #"%\d" template)]
    (reduce (fn [s match]
              (str/replace s (re-pattern-literal match) (sub-fn match)))
            template matches)))

;;; TODO maybe should be part of compact, but then you have some children as lists....
(defn listify
  "Turns blocks linked by :next into a list" 
  [block]
  (when block
    (cons block (listify (get-in block [:children :next])))))

(declare text)

(defn format-for-block
  [{:keys [children] :as _compact} message args]
  (when message
    (template-subst message
                    (fn [var]
                      (let [arg-n (or (u/coerce-numeric (subs var 1)) 0) ;or 0 TEMP to fix compile issue
                            arg (nth args (- arg-n 1))
                            arg-name (:name arg)
                            child (get children arg-name)]
                        (cond (= arg-name "constraint")
                              ""
                              (map? child)
                              (text child)
                              :else
                              (str child)))))))

(defmulti text (fn [compact] (let [bd  (block-def (:type compact))]
                               (:query-builder bd))))

(defn- bracket
  [s]
  (str "[" s "]"))

(defmethod text :default
  [{:keys [type] :as compact}]
  (let [{:keys [message0 args0]} (block-def type)]
    (bracket (format-for-block compact message0 args0))))

(defmethod text :query-builder-query
  [{:keys [type children] :as compact}]
  (let [{:keys [message0 args0]} (block-def type)
        constraints (listify (get children "constraint"))
        base (format-for-block compact message0 args0)]
    (bracket
     (if (empty? constraints)
       (str/replace base #" where$" "")
       (str base " "
            (str/join " and " (map text constraints)))))))

(defmethod text :query-builder-or
  [{:keys [children]}]
  (let [constraints (listify (get children "constraint"))]
    (bracket (str/join " or " (map text constraints)))))

(defmethod text :query-builder-layer
  [{:keys [children]}]
  (let [query (get children "data")]
    (bracket (str "graph of " (text query)))))


       
