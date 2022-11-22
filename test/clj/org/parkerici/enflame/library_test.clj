(ns org.parkerici.enflame.library-test
  (:require [clojure.test :refer :all]
            [org.parkerici.enflame.blockdefs :as blockdefs]
            [org.parkerici.enflame.candel.query :as query]
            [org.parkerici.enflame.candel.wick :as wick]
            [org.parkerici.enflame.test-utils :refer :all]
            [clojure.test :as t]))


(use-fixtures :once with-schema)

;;;  Turn off annoying print-map feature 
(defmethod print-method clojure.lang.IPersistentMap [m, ^java.io.Writer w]
  (#'clojure.core/print-meta m w)
  (#'clojure.core/print-map m #'clojure.core/pr-on w))

;;; This file tests the query generation machinery on a collection of queries gleaned from the
;;; contents of the library as of 10/12/2019, and of course can be extended. Will probably break if
;;; the CANDEL schema changes.

(defn set= [a b]
  (and (= (set a) (set b))
       ;; Prevent duplicate items
       (= (count a) (count b))))

;;; Order of find clauses is actually relevant, so may not want to use set=
;;; OTOH, there are some internal structures in here (like pull specs) that are order-independent
(defn query= [q1 q2]
  (and (set= (:find q1) (:find q2))
       (set= (:where q1) (:where q2))))

;;; Queries generated from library (and some more added by hand)

(def queries
  '[
    {:query "[assays where [technology is any]]"
     :compact
     {:type "assay_query",
      :name nil,
      :children
      {"output" "include",
       "constraint"
       {:type "assay_technology",
        :name nil, 
        :children {"V" "any"}}}}
     :dquery
     {:find ((pull ?assay1 [:db/id :assay/name]) ?technology1),
      :where ([?assay1 :assay/technology ?technology1]), :improve true}
     }

    {:query "[any subject]"
     :compact {:type "subject_any", :name nil, :children {"output" "include"}}
     :dquery
     {:find ((pull ?subject1 [:db/id :subject/id])), :where ([?subject1 :subject/id ?id1])}
     }
    {:query "[samples where [tumor-type is *]]",
     :compact
     {:type "sample_query",
      :name nil,
      :children
      {"output" "count",
       "constraint"
       {:type "sample_tumor-type",
        :name nil, 
        :children {"comp" "is", "V" "*"}}}},
     :dquery
     {:find ((count-distinct ?sample1) ?tumor-type1),
      :where ([?sample1 :sample/tumor-type ?tumor-type1])}}

    {:query "[subjects where [meddra-disease is [meddra-diseases]]]",
     :compact
     {:type "subject_query",
      :name nil,
      :children
      {"output" "count",
       "constraint"
       {:type "subject_meddra-disease",
        :name nil,
        :children
        {"V"
         {:type "meddra-disease_query",
          :name nil,
          :children {"output" "include"}}}}}},
     :dquery
     {:find
      ((count-distinct ?subject1)
       (pull ?meddra-disease1 [:db/id :meddra-disease/preferred-name])),
      :where
      ([?subject1 :subject/meddra-disease ?meddra-disease1])}}



    {:query "[samples where [gdc-anatomic-site is [gdc-anatomic-sites]]]",
     :compact
     {:type "sample_query",
      :name nil,
      :children
      {"output" "count",
       "constraint"
       {:type "sample_gdc-anatomic-site",
        :name nil,
        :children
        {"V"
         {:type "gdc-anatomic-site_query",
          :name nil,
          :children {"output" "include"}}}}}},
     :dquery
     {:find
      ((count-distinct ?sample1)
       (pull ?gdc-anatomic-site1 [:db/id :gdc-anatomic-site/name])),
      :where
      ([?sample1 :sample/gdc-anatomic-site ?gdc-anatomic-site1])}}

    ;; Converted this one to new scheme (no _with_ blocks)
    {:query
     "[clinical-observations where [subject is [subjects where [⨷ variant is [variants where [gene is [genes where [hgnc-symbol is PBRM1]]]]]]]]"
     :compact
     {:type "clinical-observation_query",
      :children
      {"output" "pull",
       "constraint"
       {:type "clinical-observation_subject",
        :children
        {"V"
         {:type "subject_query",
          :children
          {"output" "include",
           "constraint"
           {:type "subject_variant",
            :children
            {"V"
             {:type "variant_query",
              :children
              {"output" "include",
               "constraint"
               {:type "variant_gene",
                :children
                {"V"
                 {:type "gene_query",
                  :children
                  {"output" "omit",
                   "constraint"
                   {:type "gene_hgnc-symbol",
                    :children {"comp" "is", "V" "PBRM1"}}}}}}}}}}}}}}}}
     :dquery
     {:find
      ((pull
        ?clinical-observation1
        [*
         {:clinical-observation/study-day [:db/id :study-day/id]}
         {:clinical-observation/metastasis-gdc-anatomic-sites [:db/id :gdc-anatomic-site/name]}
         {:clinical-observation/timepoint [:db/id :timepoint/id]}
         {:clinical-observation/subject [:db/id :subject/id]}])
       (pull ?subject1 [:db/id :subject/id])
       (pull ?variant1 [:db/id :variant/id])),
      :where
      ([?clinical-observation1 :clinical-observation/subject ?subject1]
       [?measurement1 :measurement/variant ?variant1]
       [?variant1 :variant/gene ?gene1]
       [?gene1 :gene/hgnc-symbol "PBRM1"]
       [?sample1 :sample/subject ?subject1]
       [?measurement1 :measurement/sample ?sample1])}
     }

    {:query "[subjects where [⨷ drug is [drugs]]]",
     :compact
     {:type "subject_query",
      :name nil,
      :children
      {"output" "count",
       "constraint"
       {:type "subject_drug",
        :name nil,
        :children
        {"V"
         {:type "drug_query",
          :name nil,
          :children {"output" "include"}}}}}},
     :dquery
     {:find
      ((count-distinct ?subject1)
       (pull ?drug1 [:db/id :drug/preferred-name])),
      :where
      ([?drug-regimen1 :drug-regimen/drug ?drug1]
       [?treatment-regimen1 :treatment-regimen/drug-regimens ?drug-regimen1]
       [?therapy1 :therapy/treatment-regimen ?treatment-regimen1]
       [?subject1 :subject/therapies ?therapy1])}}

    {:query "[gene-products where [gene is [genes]]]",
     :compact
     {:type "gene-product_query",
      :name nil,
      :children
      {"output" "include",
       "constraint"
       {:type "gene-product_gene",
        :name nil,
        :children
        {"V"
         {:type "gene_query",
          :name nil,
          :children {"output" "include"}}}}}},
     :dquery
     {:find
      ((pull ?gene-product1 [:db/id :gene-product/id])
       (pull ?gene1 [:db/id :gene/hgnc-symbol])),
      :where
      ([?gene-product1 :gene-product/gene ?gene1])}}

    {:query "[variants where [gene is [genes]]]",
     :compact
     {:type "variant_query",
      :name nil,
      :children
      {"output" "count",
       "constraint"
       {:type "variant_gene",
        :name nil,
        :children
        {"V"
         {:type "gene_query",
          :name nil,
          :children {"output" "include"}}}}}},
     :dquery
     {:find
      ((count-distinct ?variant1)
       (pull ?gene1 [:db/id :gene/hgnc-symbol])),
      :where
      ([?variant1 :variant/gene ?gene1])}}

    {:query "[subjects where [dataset is [datasets]]]",
     :compact
     {:type "subject_query",
      :name nil,
      :children
      {"output" "count",
       "constraint"
       {:type "subject_dataset",
        :name nil,
        :children
        {"V"
         {:type "dataset_query",
          :name nil,
          :children {"output" "include"}}}}}},
     :dquery
     {:find
      ((count-distinct ?subject1) (pull ?dataset1 [:db/id :dataset/name])),
      :where
      ([?dataset1 :dataset/subjects ?subject1])}}

    {:query
     "[datasets where [subjects is [subjects]] and [samples is [samples]]]",
     :compact
     {:type "dataset_query",
      :children
      {"output" "include",
       "constraint"
       {:type "dataset_subjects",
        :children
        {"V" {:type "subject_query", :children {"output" "count"}},
         :next
         {:type "dataset_samples",
          :children
          {"V" {:type "sample_query", :children {"output" "count"}}}}}}}}
     :dquery
     {:find
      ((pull ?dataset1 [:db/id :dataset/name])
       (count-distinct ?subject1)
       (count-distinct ?sample1)),
      :where
      ([?dataset1 :dataset/samples ?sample1]
       [?dataset1 :dataset/subjects ?subject1]
       )}}

    {:query
     "[genes where [genomic-coordinates is [genomic-coordinates where [contig is chr8]]]]",
     :compact
     {:type "gene_query",
      :name nil,
      :children
      {"output" "pull",
       "constraint"
       {:type "gene_genomic-coordinates",
        :name nil,
        :children
        {"V"
         {:type "genomic-coordinate_query",
          :name nil,
          :children
          {"output" "omit",
           "constraint"
           {:type "genomic-coordinate_contig",
            :name nil,
            :children {"comp" "is", "V" "chr8"}}}}}}}}
     :dquery
     {:find ((pull ?gene1 [* {:gene/genomic-coordinates [:db/id :genomic-coordinate/id]}])),
      :where ([?genomic-coordinate1 :genomic-coordinate/contig "chr8"]
              [?gene1 :gene/genomic-coordinates ?genomic-coordinate1])}}

    {:query
     "[variants where [⨷ subject is [subjects where [dead? true]]]]",
     :compact
     {:type "variant_query",
      :name nil,
      :children
      {"output" "include",
       "constraint"
       {:type "variant_subject",
        :name nil,
        :children
        {"V"
         {:type "subject_query",
          :name nil,
          :children
          {"output" "include",
           "constraint"
           {:type "subject_dead",
            :name nil,
            :children {"V" "true"}}}}}}}},
     :dquery
     {:find
      ((pull ?variant1 [:db/id :variant/id])
       (pull ?subject1 [:db/id :subject/id])),
      :where
      ([?measurement1 :measurement/variant ?variant1]
       [?measurement1 :measurement/sample ?sample1]
       [?sample1 :sample/subject ?subject1]
       [?subject1 :subject/dead true])}}

    {:query
     "[datasets where [subjects is [subjects where [sample is [samples]] and [meddra-disease is [meddra-diseases]]]]]",
     :compact
     {:type "dataset_query",
      :children
      {"output" "include",
       "constraint"
       {:type "dataset_subjects",
        :children
        {"V"
         {:type "subject_query",
          :children
          {"output" "count",
           "constraint"
           {:type "subject_sample",
            :children
            {"V" {:type "sample_query", :children {"output" "count"}},
             :next
             {:type "subject_meddra-disease",
              :children
              {"V"
               {:type "meddra-disease_query",
                :children {"output" "include"}}}}}}}}}}}},
     :dquery
     {:find
      ((pull ?dataset1 [:db/id :dataset/name])
       (count-distinct ?subject1)
       (count-distinct ?sample1)
       (pull ?meddra-disease1 [:db/id :meddra-disease/preferred-name])),
      :where
      ([?subject1 :subject/meddra-disease ?meddra-disease1]
       [?sample1 :sample/subject ?subject1]
       [?dataset1 :dataset/subjects ?subject1])}}

    {:query "[measurements where [nanostring-count > 0]]",
     :compact
     {:type "measurement_query",
      :name nil,
      :children
      {"output" "pull",
       "constraint"
       {:type "measurement_nanostring-count",
        :name nil,
        :children {"comp" ">", "V" "0"}}}},
     :dquery
     {:find ((pull ?measurement1 [*
                                  {:measurement/nanostring-signature [:db/id :nanostring-signature/name]}
                                  {:measurement/variant [:db/id :variant/id]}
                                  {:measurement/gene-product [:db/id :gene-product/id]}
                                  {:measurement/cnv [:db/id :cnv/id]}
                                  {:measurement/otu [:db/id :otu/id]}
                                  {:measurement/tcr [:db/id :tcr/id]}
                                  {:measurement/sample [:db/id :sample/id]}
                                  {:measurement/epitope [:db/id :epitope/id]}
                                  {:measurement/cell-population [:db/id :cell-population/name]}
                                  {:measurement/atac-peak [:db/id :atac-peak/name]}])
             ?nanostring-count1),
      :where ([(> ?nanostring-count1 0)]
              [?measurement1 :measurement/nanostring-count ?nanostring-count1])}}

    {:query "[measurements where [[leukocyte-count > 2] or [U-L > 2]]]"
     :compact {:type "measurement_query",
               :name nil,
               :children
               {"output" "include",
                "constraint"
                {:type "measurement_or",
                 :name nil,
                 :children
                 {"constraint"
                  {:type "measurement_leukocyte-count",
                   :name nil,
                   :children {"comp" ">"
                              "V" "2"
                              :next
                              {:type "measurement_U-L",
                               :name nil, 
                               :children {"comp" ">", "V" "2"}}}}}}}}
     :dquery {:find ((pull ?measurement1 [:db/id :measurement/id])),
              :where
              ((or-join [?measurement1]
                        (and [?measurement1 :measurement/leukocyte-count ?leukocyte-count1]
                             [(> ?leukocyte-count1 2)])
                        (and [?measurement1 :measurement/U-L ?U-L1]
                             [(> ?U-L1 2)])))}
     }
    ]
  )

(deftest library-query-gen
  (doseq [{:keys [query compact dquery] :as item} queries]
    (testing (str "query gen " query)
      (let [gen-query (query/build-top-query compact)]
        (is (query= gen-query dquery))))))

(deftest library-title-gen
  (doseq [{:keys [query compact]} queries]
    (testing (str "query gen " query)
      (let [text (blockdefs/text compact)]
        (is (= text query))))))

;;; TODO this ensures wick gen doesn't error, but not correctness
(deftest library-wick-gen
  (doseq [{:keys [query compact]} queries]
    (testing (str "wick gen " query)
      (let [gen-query (query/build-top-query compact)]
        (wick/translate gen-query)
        ))))
