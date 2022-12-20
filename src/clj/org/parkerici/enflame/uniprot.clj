(ns org.parkerici.enflame.uniprot
  (:require
   [org.parkerici.enflame.sparql :as sq]
   [arachne.aristotle.registry :as reg]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.multitool.cljcore :as ju]
   [clojure.set :as set]
   )  )

;;; TODO get from config
;;; Note: has teneded to disappear and reappear, restarting Wifi helps
(def endpoint "https://sparql.uniprot.org/")

;;; → Multitool
(defn curried-api
  [namespace arg1]                      ;TODO should take arb # args
  `(do
     ~@(for [[s v] (ns-publics namespace)
             :when (:api (meta v))]
         `(def ~s ~(partial v arg1)))))

(eval (curried-api 'org.parkerici.enflame.sparql endpoint))

;;; These are silly
(reg/prefix 'uniprot "http://purl.uniprot.org/core/")
(reg/prefix 'unipath "http://purl.uniprot.org/unipathway/")
(reg/prefix 'unicite "http://purl.uniprot.org/citations/")
(reg/prefix 'unidb "http://purl.uniprot.org/database")
(reg/prefix 'dcterms "http://purl.org/dc/terms/")
(reg/prefix 'unienzyme "http://purl.uniprot.org/enzyme/")
(reg/prefix 'skos "http://www.w3.org/2004/02/skos/core#")

;;; Try (this does not seem to work, sigh)
(reg/prefix 'uniuni "http://purl.uniprot.org/")

(defn uniprot-q
  [sparql]
  (if (string? sparql)
    (sq/do-query (sq/sparql-source "https://sparql.uniprot.org/") sparql)
    (uniprot-q (sq/->sparql sparql :limit 100000)))) ;TODO limit temp  :limit 1000
    

(def external-ontology
  '{:rdf/Statement {:rdf/type (:owl/Class)}})

;;; TODO shouldn't run on compile
(defonce uniprot-ontology
  (->
   (sq/entify
    (uniprot-q
     '(:bgp [?s :rdfs/isDefinedBy ?uniprot]
            [?s ?p ?o])))
   (merge external-ontology)
   ;; This one field comes back with an unserializable object, just patch it
   ;; Real thing (.-lexicalValue _) if need be
   (assoc-in [:uniprot/Pathway :rdfs/label] '("Pathway"))))

;;; TODO damn I wish these were more composable


;;; Note :unipath/399.28.3.3 doesn't work with Clojure reader, argh
#_
(pull (keyword "unipath" "399.28.3.3"))


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

(defn describe
  [ent]
  (concat
    (uniprot-q `(:bgp [~ent ?p ?o]))
    (uniprot-q `(:bgp [?s ?p ~ent]))))


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

;;; TODO add rdfs/label field
;;; TODO add skos etc fields
(defn class-alzabo-fields
  [class]
  (apply
   merge
   (for [[n d] (properties-for-domain class)]
     {(nons n)
      {:type (or (nons (first (:rdfs/range d)))
                 :string)               ;temp but nil doen't work
         ;; :cardinality ...
       :uri n
       :attribute n                  ;aka :uri, but this leverages existing mechanisms
       :doc (first (:rdfs/comment d))}}
     )))

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


;;; Regex usage



    `(:bgp [?protein :rdf/type :uniprot/Protein]
           [?protein :uniprot/classifiedWith ?concept]
           [?concept :rdfs/label ?clabel]
           [(regex ?clabel "FOO.*" "")])
