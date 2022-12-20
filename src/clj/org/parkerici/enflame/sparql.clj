(ns org.parkerici.enflame.sparql
  (:require
   [clj-http.client :as client]
   [arachne.aristotle.query :as q]
   [arachne.aristotle.registry :as reg]
   [arachne.aristotle.graph :as graph] 
   [clojure.data.json :as json]
   [org.parkerici.multitool.core :as u]
   )
  (:import (org.apache.jena.rdf.model Model Resource Literal Property Statement RDFNode))
  )

;;; Not clear what Aristotle does that isn't better handled by Jena SSE https://jena.apache.org/documentation/notes/sse.html


(defn ->sparql [bgp & {:keys [limit]}]
  (let [query (-> bgp
                  q/build
                  org.apache.jena.sparql.algebra.OpAsQuery/asQuery)]
    ;; TODO nany other options, try (bean query)
    (when limit
      (.setLimit query limit))
    (str query)))

(comment 
  (reg/prefix 'ds "https://data.lacity.org/resource/zzzz-zzzz/")
  (->sparql '(:bgp {:rdf/about ?e, :ds/zip_code "90001", :ds/total_population ?pop})))

;;; This could probably be improved
;;; see https://github.com/ajoberstar/ike.cljj
(defn make-consumer [collector]
  (reify java.util.function.Consumer
    (accept [& thing]
      (swap! collector conj thing))))

;;; Note: not a Graph
(defn sparql-source
  [url]
  (-> (org.apache.jena.rdfconnection.RDFConnectionRemote/create)
      (.destination url)))

;;; TODO - want to try q/run, but it requires a Graph object and I can't figure it out...

(defn do-query-raw
  "Source is an org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder
  q is a sparql string"
  [source q]
  (let [collector (atom [])]            
    (.querySelect (.build source) q
                  (make-consumer collector))
    collector))

(defn result-binding->clj [rb]
  (let [bindings (.getBinding rb)
        vars (iterator-seq (.vars bindings))]
    (zipmap (map #(keyword (.getVarName %)) vars)
            (map #(graph/data (.get bindings %)) vars))))

(defn do-query [source q]
  (let [results @(do-query-raw source q)]
    (map result-binding->clj results)))


(defn xquery [& args]
  (apply q/run args))


;;; Reshaping tools


(defn entify-1
  [query-results]
  (u/map-values (fn [c] (map :o c))
                (group-by :p query-results)))


(defn entify
  "Turn a query results with :s :p :o fields into a set of maps"
  [query-results]
  (->> query-results
       (group-by :s)
       (u/map-values entify-1)))

;;; Searching for usable endpoints

(def sparql-dbs
  (clojure.data.json/read-str (slurp "scrap/sparql-endpoints.json")
                              :key-fn keyword))


(defn check-link
  [url]
  (client/head url
               {:cookie-policy :standard
                :trace-redirects true
                :redirect-strategy :graceful}))

(defn test-endpoint
  [e]
  (prn :testing e)
  (check-link (:u e)))

;;; From goddinpotty
(defn check-external-links
  "Find bad external links in the background."
  []
  (let [bads (atom [])
        goods (atom [])]
    (doseq [{:keys [u item] :as db} sparql-dbs]
      (future-call
       #(try
          (prn :checking db)
          (swap! goods conj [db (check-link u)])
          (catch Throwable e (swap! bads conj [db e])))))
    [bads goods]))


(comment
;;; These are some that at least respond.
("http://vulcan.cs.uga.edu/sparql"  ;;; Prokino, see http://prokino.uga.edu/prokino/query/sparql


 "http://opencitations.net/sparql"
 "https://sparql.proconsortium.org/virtuoso/sparql"
 "http://opendatacommunities.org/sparql"
 "http://data.allie.dbcls.jp/sparql"
 "https://bgee.org/sparql"
 "https://ld.cultural.jp/sparql"
 "http://genome.microbedb.jp/sparql"
 "https://data.europa.eu/euodp/sparqlep"
 "http://id.nlm.nih.gov/mesh/sparql"
 "https://colil.dbcls.jp/sparql"
 "http://data.archiveshub.ac.uk/sparql"
 "http://lod.nl.go.kr/sparql"
 "https://query.inventaire.io"
 "https://sparql.uniprot.org/sparql"
 "http://www.europeandataportal.eu/sparql"
 "https://data.norge.no/sparql"
 "http://id.sgcb.mcu.es/sparql"
 "http://data.bibliotheken.nl/sparql"
 "http://rdf.disgenet.org/sparql/"
 "http://statistics.data.gov.uk/sparql"
 "http://statistics.data.gov.uk/sparql"
 "http://linkedgeodata.org/sparql"
 "https://id.ndl.go.jp/auth/ndla/sparql"
 "http://cultura.linkeddata.es/sparql"
 "https://jpsearch.go.jp/rdf/sparql/"
 "https://isidore.science/sqe"
 "https://commons-query.wikimedia.org/"
 "https://mediag.bunka.go.jp/madb_lab/lod/sparql/"
 "http://patho.phenomebrowser.net/sparql/"
 "http://sparql.archives-ouvertes.fr/sparql"
 "https://www.dictionnairedesfrancophones.org/sparql"
 "https://database.factgrid.de/query/"
 "https://datos.gob.es/es/sparql"
 "https://sparql.orthodb.org/sparql"
 "https://data.gov.cz/sparql"
 "http://data.archaeologydataservice.ac.uk/sparql/repositories/archives"
 "https://lingualibre.org/bigdata/namespace/wdq/sparql"
 "https://slod.fiz-karlsruhe.de/sparql"
 "https://druid.datalegend.net/AdamNet/Heritage/sparql/Heritage"
 "http://www.genome.jp/sparql/linkdb"
 "http://ma-graph.org/sparql"
 "http://sparql.wikipathways.org/"
 "http://bnb.data.bl.uk/sparql"
 "http://data.culture.fr/thesaurus/sparql"
 "https://data.muziekweb.nl/MuziekwebOrganization/Muziekweb/sparql/Muziekweb"
 "http://data.bnf.fr/sparql/"
 "https://labs.onb.ac.at/en/tool/sparql/"
 "http://dati.camera.it/sparql"
 "https://tora.entryscape.net/snorql"
 "https://sparql.rhea-db.org/sparql"
 "http://data.nobelprize.org/"
 "https://triplestore.iccu.sbn.it/sparql"
 "https://triplestore.iccu.sbn.it/sparql"
 "https://query.linkedopendata.eu/sparql"
 "http://bio2rdf.org/sparql"
 "http://vocabulary.curriculum.edu.au/PoolParty/sparql/scot"
 "http://data.cervantesvirtual.com/openrdf-sesame/repositories/data"
 "https://libris.kb.se/sparql"
 "http://data.nationallibrary.fi/bib/sparql"
 "http://data.persee.fr/sparql"
 "https://data.idref.fr/sparql"
 "https://data.cssz.cz/sparql"
 "https://www.orpha.net/sparql"
 "https://www.orpha.net/sparql"
 "https://datos-abertos.galiciana.gal/pt/sparql"
 "https://xn--slovnk-7va.gov.cz/sparql"
 "https://idsm.elixir-czech.cz/sparql/"
 "http://dbpedia.org/sparql"
 "http://lod.openaire.eu/endpoint"
 "http://sparql.europeana.eu/"
 "http://data.ordnancesurvey.co.uk/datasets/os-linked-data/apis/sparql"
 "https://query.wikidata.org/sparql")
)


;;; Abstracted from Uniprot
(defn ^:api q
  [endpoint sparql]
  (if (string? sparql)
    (do-query (sparql-source endpoint) sparql)
    (q endpoint (->sparql sparql))))


(defn ontology-query
  [endpoint]
  (->
   (entify
    (q endpoint
       '(:bgp [?s :rdfs/isDefinedBy ?uniprot]
              [?s ?p ?o])))))

(defn ontology-query-2
  [endpoint]
  (->
   (entify
    (q endpoint
       '(:bgp [?s :rdf/type :owl/Class]
              [?s ?p ?o])))))

(defn ^:api instances
  "Does a pull of all instances of class"
  [endpoint class]
  (entify
   (q endpoint
    `(:bgp [?s :rdf/type ~class]
           [?s ?p ?o]))))

(defn ^:api instances-only
  "Gets all instances of class"
  [endpoint class]
  (q endpoint
   `(:bgp [?s :rdf/type ~class]
          )))

(defn ^:api pull
  [endpoint ent]
  (entify-1
   (q endpoint
    `(:bgp [~ent ?p ?o]))
   ))

(defn ^:api pull2
  [endpoint ent]
  (entify-1
   (q endpoint
      `(:conditional
        (:bgp [?o ?p2 ?o2])
        (:bgp [~ent ?p ?o])))))


(defn ^:api pull2
  [endpoint ent]
   (q endpoint
      `(:bgp [~ent ?p ?o]
             [?o ?p2 ?o2])))

(defn ^:api pull3
  [endpoint ent]
   (q endpoint
      `(:bgp [~ent ?p ?o]
             [?o ?p2 ?o2]
             [?o2 ?p3 ?o3]
             )))




(defn ^:api antipull
  [endpoint ent]
  (entify-1
   (q endpoint
    `(:bgp [?o ?p ~ent]))
   ))

(defn attributes
  [endpoint att]
   (q endpoint
    `(:bgp [?s ~att ?o]))
  )


(comment 
;;; Look here for examples
;;; https://github.com/arachne-framework/aristotle/blob/master/test/arachne/aristotle/query_test.clj#L90
;;; This runs but seems to ignore the aggregate spec
(q '(:group (?s) ((?s (count ?s))) (:bgp [?s :rdfs/domain :prokino/LigandActivity])))



;;; Example of DISTINCT
(def x (sq/q endpoint
             '(:distinct (:project [?o] (:bgp [?s :rdf/type  :prokino/LigandActivity]
                                              [?s :prokino/hasMOA ?o])))))
)
