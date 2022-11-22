(ns org.parkerici.enflame.query-test    ;TODO .candel. 
  (:require [clojure.test :refer :all]
            [org.parkerici.enflame.test-utils :refer :all]
            [org.parkerici.enflame.candel.query :refer :all]))

(use-fixtures :once with-schema)

(defn set= [a b]
  (and (= (set a) (set b))
       ;; Prevent duplicate items
       (= (count a) (count b))))

(defn build-test-query [compact]
  (build-top-query compact))

(deftest basic-build-query-test
  (let [compact
        ;; subjects with race=white
        '{:type "subject_query",
          :btype :block,
          :children
          {"output" "include",            ;TODO should work without this
           "constraint"
           {:type "subject_race",
             :btype :block, 
            :children {"V" "white"}}}}
        query (build-test-query compact)]
    (is (= '((pull ?subject1 [:db/id :subject/id])) (:find query)))
    (is (= '([?subject1 :subject/race :subject.race/white]) (:where query)))
    ))

(deftest build-query-inverted-blockdef-test
  (let [compact
        '{:type "subject_query",
          :children
          {"output" "include",
           "constraint"
           {:type "subject_dataset",
            :children
            {"V"
             {:type "dataset_query",
              :children
              {"output" "include",
               "constraint"
               {:type "dataset_name", :children {"comp" "is", "V" "azif"}}}}}}}}
        query (build-test-query compact)]
    (is (set= '([?dataset1 :dataset/name "azif"]
                [?dataset1 :dataset/subjects ?subject1])
              (:where query)))))

(deftest build-query-numeric-blockdef-test
  (let [compact
        ;; subject with age < 40
        '{:type "subject_query",
         :children
         {"constraint"
          {:type "subject_age",
           :children {"comp" "<", "V" "40"}}}}
        query (build-test-query compact)]
    (is (set= '([(< ?age1 40)] [?subject1 :subject/age ?age1])
              (:where query)))
    (is (set= '((pull ?subject1 [:db/id :subject/id]) ?age1)
              (:find query)))
    (testing "equality"
      ;; subject with age = 40
      (let [compact (assoc-in compact [:children "constraint" :children "comp"] "=")
            query (build-test-query compact)]
        (is (set= '([?subject1 :subject/age 40])
                  (:where query)))))
    (testing "wild"
      ;; subject with age specified (*)
      (let [compact (assoc-in compact [:children "constraint" :children "V"] "*")
            query (build-test-query compact)]
        (is (set= '([?subject1 :subject/age ?age1])
                  (:where query)))))))

;;; Don't have anything suitable now
(deftest build-query-complex-numeric-blockdef-test
  (let [compact
        ;; subjects with bmi < 40
        '{:type "subject_query",
          :btype :block,
          :children
          {"constraint"
           {:type "subject_bmi",
             :btype :block, 
             :children {"comp" "<",
                        "V" "40"}}}}
        query (build-test-query compact)]
    (is (set= '([(< ?bmi1 40)]
                [?clinical-observation1 :clinical-observation/subject ?subject1]
                [?clinical-observation1 :clinical-observation/bmi ?bmi1])
              (:where query)))
    (is (set= '((pull ?subject1 [:db/id :subject/id]) ?bmi1)
              (:find query)))
    (testing "equality"
      ;; clinical observations with bmi = 40
      (let [compact (assoc-in compact [:children "constraint"  :children "comp"] "=")
            query (build-test-query compact)]
        (is (set= '([?clinical-observation1 :clinical-observation/bmi 40]
                    [?clinical-observation1 :clinical-observation/subject ?subject1])
                  (:where query)))))
    (testing "wild"
      ;; clinical observations with bmi specified (*)
      (let [compact (assoc-in compact [:children "constraint"  :children "V"] "*")
            query (build-test-query compact)]
        (is (set= '([?clinical-observation1 :clinical-observation/bmi ?bmi1]
                    [?clinical-observation1 :clinical-observation/subject ?subject1])
                  (:where query)))))))

(deftest build-query-or-blockdef-test
  (let [compact
        '{:type "subject_query",
          :children
          {"output" "pull",
           "constraint"
           {:type "subject_or",
            :children
            {"constraint"
             {:type "subject_race",
              :children
              {"V" "white",
               :next {:type "subject_race", :children {"V" "asian"}}}}}}}}
        query (build-test-query compact)]
    (is (set= '((or [?subject1 :subject/race :subject.race/white]
                    [?subject1 :subject/race :subject.race/asian]))
              (:where query)))))

;;; TODO test string fields, 
;;; TODO broken in 0.3.0
(deftest build-query-compound-test
  (let [compact
        ;; subjects with clinical-observation where bmi < 40
        '{:type "subject_query",
          :children
          {"output" "pull",
           "constraint"
           {:type "subject_clinical-observation",
            :children
            {"V"
             {:type "clinical-observation_query",
              :children
              {"output" "include",
               "constraint"
               {:type "clinical-observation_bmi",
                :children {"comp" "<", "V" "40"}}}}}}}}
        query (build-test-query compact)]
    (is (set= '([(< ?bmi1 40)]
                [?clinical-observation1 :clinical-observation/bmi ?bmi1]
                [?clinical-observation1 :clinical-observation/subject ?subject1])
              (:where query)))))


(deftest build-query-boolean-field-test
  (let [compact
        '{:type "sample_query",
          :btype :block,
          :children
          {"output" "include",
           "constraint"
           {:type "sample_subject",
            :btype :block,
            :children
            {"V"
             {:type "subject_query",
              :btype :block,
              :children
              {"output" "include",
               "constraint"
               {:type "subject_dead",
                :btype :block, 
                :children {"V" "true"}}}}}}}}
        query (build-test-query compact)]
    (is (set= '[[?subject1 :subject/dead true]
                [?sample1 :sample/subject ?subject1]]
              (:where query)))))


;;; TODO broken in 0.3.0
(deftest build-query-complex-primitive-test
  (let [compact
        '{:type "subject_query",
          :btype :block,
          :children
          {"output" "include",
           "constraint"
           {:type "subject_bmi",
             :btype :block, 
            :children {"comp" "<=", "V" "40"}}}}
        query (build-test-query compact)]
    (is (= '((pull ?subject1 [:db/id :subject/id]) ?bmi1) (:find query)))
    (is (set= '[[(<= ?bmi1 40)]
                [?clinical-observation1 :clinical-observation/bmi ?bmi1]
                [?clinical-observation1 :clinical-observation/subject ?subject1]]
              (:where query)))))

;; variants where subject is dead
(deftest build-query-complex-relation-test
  (let [compact
        '{:type "variant_query",
          :name nil,
          :btype :block,
          :children
          {"output" "include",
           "constraint"
           {:type "variant_subject",
            :name nil,
            :btype :block,
            :children
            {"V"
             {:type "subject_query",
              :name nil,
              :btype :block,
              :children
              {"output" "include",
               "constraint"
               {:type "subject_dead",
                :name nil, 
                :btype :block, 
                :children {"V" "true"}}}}}}}}
        query (build-test-query compact)]
    (is (= '((pull ?variant1 [:db/id :variant/id])
             (pull ?subject1 [:db/id :subject/id])) (:find query)))
    (is (set= '([?measurement1 :measurement/variant ?variant1]
                [?measurement1 :measurement/sample ?sample1]
                [?sample1 :sample/subject ?subject1] 
                [?subject1 :subject/dead true])
              (:where query))))  )

(deftest tuple-query-test
  (let [compact {:type "nanostring-signature_query", :children {"output" "pull"}}]
    (is (= '{:find [(pull ?nanostring-signature1 [:db/id :nanostring-signature/name])
                    (pull ?gene1 [:db/id :gene/hgnc-symbol])
                    ?double1]
             :where [[?nanostring-signature1 :nanostring-signature/name ?name1]
                     [?nanostring-signature1 :nanostring-signature/gene-weights ?tuple]
                     [(untuple ?tuple) [?gene1 ?double1]]]}
           (build-test-query compact)))))
