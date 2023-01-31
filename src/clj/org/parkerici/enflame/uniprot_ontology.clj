(ns org.parkerici.enflame.uniprot-ontology
  (:require
   [org.parkerici.enflame.sparql :as sq]
   [arachne.aristotle.registry :as reg]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.multitool.cljcore :as ju]
   [clojure.set :as set]
   )  )

;;; Stuff involved in building the ontology, should not be needed at query runtime

;;; TODO get from config
(def endpoint "https://sparql.uniprot.org/")

;;; → Multitool - but shouldn't it be a macro rather than having to call eval TODO
(defn curried-api
  [namespace arg1]                      ;TODO should take arb # args
  `(do
     ~@(for [[s v] (ns-publics namespace)
             :when (:api (meta v))]
         `(def ~s ~(partial v arg1)))))

(eval (curried-api 'org.parkerici.enflame.sparql endpoint))


(def external-ontology
  '{:rdf/Statement {:rdf/type (:owl/Class)}
    ;; This way might be cleaner but more work
    ;; :Taxon {:fields {:scientificName {:name? true}}}
    :Taxon {:fields {:label {:type :string :uri :uniprot/scientificName :attribute :uniprot/scientificName}}}
    :Gene  {:fields {:label {:type :string :uri :skos/prefLable :attribute :skos/prefLable}}}
    })

;;; ❖⟐❖ fixing blank-node domains ❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖⟐❖

(defn fix-domain
  [att]
  (->> (concat
        (map :d1 (q `[:bgp [~att :rdfs/domain ?d1]]))
        (map :d2 (remove #(keyword? (:d1 %)) (q `[:bgp [~att :rdfs/domain ?d1] [?d1 ?p1 ?d2]])))
        (map :d3 (remove #(keyword? (:d2 %)) (q `[:bgp [~att :rdfs/domain ?d1] [?d1 ?p1 ?d2] [?d2 ?p2 ?d3]])))
        (map :d4 (remove #(keyword? (:d3 %)) (q `[:bgp [~att :rdfs/domain ?d1] [?d1 ?p1 ?d2] [?d2 ?p2 ?d3] [?d3 ?p3 ?d4]]))))
       distinct
       (filter #(and (keyword? %) (= "uniprot" (namespace %))))))

(defn fix-domains
  [ontology-in]
  (let [atts (->> ontology-in
                  (filter (fn [[k v]] (let [domain (:rdfs/domain v)]
                                        (and domain (> (count domain) 1)))))
                  (map first))]
    (reduce (fn [ontology att]
              (assoc-in ontology [att :rdfs/domain] (fix-domain att)))
            ontology-in
            atts)))

;;; TODO shouldn't run on compile
(def uniprot-ontology
  (->
   (sq/entify
    (q
     '(:bgp [?s :rdfs/isDefinedBy ?uniprot]
            [?s ?p ?o])))
   (merge external-ontology)
   ;; This one field comes back with an unserializable object, just patch it
   ;; Real thing (.-lexicalValue _) if need be
   (assoc-in [:uniprot/Pathway :rdfs/label] '("Pathway"))
   fix-domains))

(defn filtered-by
  [field value]
  (u/dissoc-if (fn [[n d]]
                 (not (some #(= value %) (field d))))
               uniprot-ontology))

(defn filtered-by-any
  [field values]
  (u/dissoc-if (fn [[n d]]
                 (empty? (set/intersection (set (field d)) (set values))))
               uniprot-ontology))


(defn filtered-by-rdf-type
  [type]
  (u/dissoc-if (fn [[n d]]
                 ;; TODO assuming a single type
                 (not (= (:rdf/type d) (list type))))
               uniprot-ontology)  )

(defn classes
  []
  (filtered-by-rdf-type :owl/Class))

(defn subclasses
  [class]
  (filtered-by :rdfs/subClassOf class))

(def all-subclasses
  (u/transitive-closure (comp keys subclasses)))

(defn properties
  []
  (merge (filtered-by-rdf-type :owl/DatatypeProperty)
         (filtered-by-rdf-type :owl/ObjectProperty)))

(defn properties-for-domain
  [class]
  (filtered-by-any :rdfs/domain (all-subclasses class)))

(defn uniprot?
  [ent]
  (and (keyword? ent)
       (= "uniprot" (namespace ent))))

(defn top-classes
  []
  (filter (fn [[c d]]
            (not (contains? (classes) (:rdfs/subClassOf d))))
          (classes)))

(defn top-classes
  []
  (let [non-tops (keys (filtered-by-any :rdfs/subClassOf (keys (classes))))]
    (apply dissoc (classes) non-tops)))


#_
(:uniprot/Database
 :uniprot/Structured_Name
 :uniprot/Enzyme_Regulation_Annotation
 :uniprot/Enzyme
 :uniprot/Excluded_Proteome
 :uniprot/Gene
 :uniprot/Citation
 :uniprot/Attribution
 :uniprot/Organelle
 :uniprot/Status
 :uniprot/Part
 :uniprot/Participant
 :uniprot/Journal
 :uniprot/Structure_Mapping_Statement
 :uniprot/Proteome
 :uniprot/Nucleotide_Mapping_Statement
 :uniprot/Method
 :uniprot/Taxon
 :uniprot/Molecule
 :uniprot/Obsolete
 :uniprot/Disease
 :uniprot/Resource
 :uniprot/Proteome_Component
 :uniprot/Cluster
 :uniprot/Domain_Assignment_Statement
 :uniprot/Protein_Existence
 :uniprot/Subcellular_Location
 :uniprot/Transposon
 :uniprot/Plasmid
 :uniprot/Concept
 :uniprot/Annotation
 :uniprot/Endpoint_Statement
 :uniprot/Protein
 :uniprot/Tissue
 :uniprot/Sequence
 :uniprot/Strain
 :uniprot/Interaction
 :uniprot/Catalytic_Activity
 :uniprot/Not_Obsolete
 :uniprot/Pathway
 :uniprot/Citation_Statement
 :uniprot/Rank
 :uniprot/Reviewed)



(comment
  (count (instances :uniprot/Pathway))
  ; 3117
  (count (instances :uniprot/Disease))
  ; 6202 whoops now 0, wtf?
  (count (instances :uniprot/Molecule))
  )

(comment
(frequencies (map :rdf/type (vals uniprot-ontology) ))
{(:owl/DatatypeProperty) 43,
 (:owl/ObjectProperty) 67,
 (:owl/NamedIndividual :owl/Thing :uniprot/Organelle) 9,
 (:owl/NamedIndividual :owl/Thing :uniprot/Rank) 31,
 (:owl/Class) 168,
 (:owl/InverseFunctionalProperty :owl/FunctionalProperty :owl/ObjectProperty) 1,
 (:owl/FunctionalProperty :owl/ObjectProperty) 6,
 (:owl/FunctionalProperty :owl/DatatypeProperty) 31,
 (:owl/NamedIndividual :owl/Thing :uniprot/Status) 4,
 (:owl/NamedIndividual :owl/Thing :uniprot/Protein_Existence) 5,
 (:owl/NamedIndividual :owl/Thing :uniprot/Mass_Measurement_Method) 7,
 (:owl/NamedIndividual :owl/Thing :uniprot/Structure_Determination_Method) 7}
)

;;; Alzabo schema gen

;;; TODO should include the real URI somewhere

;;; TODO Alzabo has no concept of subclass, would be interesting to add
;;; For now, it compresses everything into top classes

;;; Remove namespace (see u/d-ns)
(defn nons
  [key]
  (if (keyword key)
    (keyword (name key))
    key))

;;; TODO add skos etc fields
(defn class-alzabo-fields
  [class]
  (apply
   merge
   {:label {:type :string
            :uri :rdfs/label
            :attribute :rdfs/label}}
   (for [[n d] (properties-for-domain class)]
     {(nons n)
      {:type (or (nons (first (:rdfs/range d)))
                 :string)               ;temp but nil doen't work
         ;; :cardinality ...
       :uri n
       :attribute n                  ;aka :uri, but this leverages existing mechanisms
       :doc (first (:rdfs/comment d))}}
     )
   ))

(defn alzabo
  []
  (u/clean-walk
   {:title "UNIPROT"
    :kinds
    (apply
     merge
     (for [[tc tc-def] (top-classes)]
       {(nons tc)
        {:doc (first (:rdfs/comment tc-def))
         :title (first (:rdfs/label tc-def)) ;not actually used or defined
         :fields (or (class-alzabo-fields tc) {})
         :uri tc
         }}))}
   nil?))


#_
(ju/schppit "uniprot-ontology.edn" uniprot-ontology)

#_
(ju/schppit "resources/uniprot-alzabo.edn" (alzabo))

#_
(frequencies (map :rdfs/domain (vals (properties))))

;;; Huh.
#_
{nil 17,
 (:uniprot/Subcellular_Location_Annotation) 1,
 (:uniprot/Structured_Name) 1,
 (:uniprot/Proteome) 2,
 (:uniprot/Interaction) 1,
 (:uniprot/Reviewed_Protein) 1,
 (_626915beb033654fc13c8409d68fbefb) 1,
 (:uniprot/RNA_Editing_Annotation) 1,
 (:uniprot/External_Sequence) 1,
 (_135e6ace0ba508ab2319c50063cc0ede) 1,
 (_7a99d05307375d434d4a3f01c938cad7) 1,
 (_b868ac3eb7960429cb539cea5f6300ae) 1,
 (:uniprot/Gene) 2,
 (_ade505809c0086211dc02c0f9464258e) 1,
 (:uniprot/Resource) 1,
 (_cfd54a0e1d32a8d62b76f3490d7f2311) 1,
 (:uniprot/Transcript_Resource) 2,
 (:uniprot/Published_Citation) 2,
 (_e978c46c81fd658d3e99796c5eaf4502) 1,
 (_5fcb391e3136fc76ba988a8b5f961505) 1,
 (_941b9dd17dd300caedb9cb8c0e3f958e) 1,
 (:uniprot/Disease_Annotation) 1,
 (_d0e6352838bda96a5d57075fed61b8fc) 1,
 (:uniprot/Simple_Sequence) 2,
 (:uniprot/Protein) 13,
 (:uniprot/Modified_Sequence) 1,
 (:uniprot/Enzyme) 2,
 (:uniprot/Catalytic_Activity_Annotation) 2,
 (_49396fea4d19b3d5b39285cc1252056b) 1,
 (_e2daa4cde5ccb48ceaa946a7c97ec83e) 1,
 (:uniprot/Journal) 1,
 (_3725fc1a96109bd014937233e0cc1e80) 1,
 (:uniprot/Cluster) 2,
 (_086cb0592bb819a0cd93d44d3bf577d8) 1,
 (:uniprot/Binding_Site_Annotation) 2,
 (_63f5f671dd82a005a9e91a5c22c458a5) 1,
 (:uniprot/Structure_Mapping_Statement) 1,
 (_a2745b16016d308213a790963118d9a8) 1,
 (:rdfs/Resource) 1,
 (:uniprot/Thesis_Citation) 1,
 (_d84a5a698f10400d9ffba7376653fc21) 1,
 (:uniprot/Nucleotide_Resource) 2,
 (_9d3897b13bb29c76ec292b254542decb) 1,
 (:uniprot/Subcellular_Location) 1,
 (_612e2c86654093537c55009db223f480) 1,
 (:uniprot/Book_Citation) 2,
 (:uniprot/Kinetics_Annotation) 2,
 (:uniprot/Sequence) 3,
 (:uniprot/Cofactor_Annotation) 1,
 (:uniprot/Catalytic_Activity) 1,
 (:uniprot/Citation) 5,
 (:uniprot/Database) 5,
 (:uniprot/Submission_Citation) 1,
 (:uniprot/Taxon) 4,
 (:uniprot/Attribution) 1,
 (:uniprot/Citation_Statement) 2}



    













#_
#:uniprot{:submittedName (:uniprot/Part :uniprot/Protein),
          :organism (:uniprot/Protein :uniprot/Sequence),
          :date (:uniprot/Attribution :uniprot/Citation),
          :representativeFor (:uniprot/Protein :uniprot/Sequence),
          :mnemonic (:uniprot/Cluster :uniprot/Protein),
          :reviewed (:uniprot/Protein :uniprot/Taxon),
          :place (:uniprot/Book_Citation :uniprot/Thesis_Citation),
          :substitution (:uniprot/Mutagenesis_Annotation :uniprot/Natural_Variant_Annotation),
          :method (:uniprot/Mass_Spectrometry_Annotation :uniprot/Structure_Resource),
          :obsolete (:uniprot/Protein :uniprot/Taxon),
          :orientation (:uniprot/Cellular_Component :uniprot/cellularComponent),
          :created (:uniprot/Protein :uniprot/Resource),
          :structuredName (:uniprot/Part :uniprot/Protein),
          :modified (:uniprot/Cluster :uniprot/Protein),
          :cellularComponent (:uniprot/Cellular_Component :uniprot/cellularComponent),
          :pages (:uniprot/Book_Citation :uniprot/Journal_Citation),
          :volume (:uniprot/Book_Citation :uniprot/Journal_Citation),
          :citation (:uniprot/Cellular_Component :uniprot/Database),
          :alternativeName (:uniprot/Part :uniprot/Protein),
          :conflictingSequence (:uniprot/Protein :uniprot/Sequence_Caution_Annotation),
          :enzyme (:uniprot/Part :uniprot/Protein),
          :attribution (:uniprot/Protein),
          :recommendedName (:uniprot/Part :uniprot/Protein),
          :sequence (:uniprot/Annotation :uniprot/Protein),
          :replaces (:uniprot/Enzyme :uniprot/Protein),
          :mappedAnnotation (:uniprot/Citation_Statement :uniprot/Protein),
          :version (:uniprot/Protein :uniprot/Sequence),
          :replacedBy (:uniprot/Enzyme :uniprot/Protein),
          :topology (:uniprot/Cellular_Component :uniprot/cellularComponent)}

      
  

