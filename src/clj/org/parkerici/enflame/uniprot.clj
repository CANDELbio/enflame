(ns org.parkerici.enflame.uniprot
  (:require
   [org.parkerici.enflame.sparql :as sq]
   [arachne.aristotle.registry :as reg]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.multitool.cljcore :as ju]
   [clojure.set :as set]
   )  )

;;; TODO this now gets loaded at startup, should not do any queries

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

;;; These are silly
(reg/prefix 'uniprot "http://purl.uniprot.org/core/")
(reg/prefix 'uniprotein "http://purl.uniprot.org/uniprot/")
(reg/prefix 'unipath "http://purl.uniprot.org/unipathway/")
(reg/prefix 'unicite "http://purl.uniprot.org/citations/")
(reg/prefix 'unidb "http://purl.uniprot.org/database")
(reg/prefix 'dcterms "http://purl.org/dc/terms/")
(reg/prefix 'unienzyme "http://purl.uniprot.org/enzyme/")
(reg/prefix 'taxon "http://purl.uniprot.org/taxonomy/")
(reg/prefix 'skos "http://www.w3.org/2004/02/skos/core#")

;;; Try (this does not seem to work, sigh)
#_ (reg/prefix 'uniuni "http://purl.uniprot.org/")








  
