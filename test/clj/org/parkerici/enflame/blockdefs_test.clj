(ns org.parkerici.enflame.blockdefs-test
  (:require [clojure.test :refer :all]
            [org.parkerici.enflame.blockdefs :refer :all]
            [org.parkerici.enflame.test-utils :refer :all]
            [org.parkerici.enflame.schema :as schema]
            ))

(use-fixtures :once with-test-config)

(deftest blockdef-test
  (testing "blockdefs for single kind (subject)"
    ;; TODO this will be kind of brittle against schema changes.
    (is (= #{"subject_age" "subject_dataset" "subject_comorbidities" "subject_or" "subject_treatment" "subject_measurement" "subject_menopause" "subject_bmi" "subject_any" "subject_HLA-C-type" "subject_variant" "subject_HLA-B-type" "subject_ethnicity" "subject_clinical-observation" "subject_metastatic-disease" "subject_HLA-DR-type" "subject_cause-of-death" "subject_therapies" "subject_sample" "subject_HLA-DQ-type" "subject_drug" "subject_dead" "subject_HLA-A-type" "subject_HLA-DP-type" "subject_freetext-disease" "subject_query" "subject_sex" "subject_id" "subject_race" "subject_meddra-disease" "subject_smoker" "subject_uid" "subject_adverse-event"}
           (set (map :type (kind-blockdefs :subject)))))
    (let [subject-any (first (kind-blockdefs :subject))]
      (is (= "any subject" (:message0 subject-any)))
      ;; TODO
      #_ (is (= (str "alzabo/schema/" schema/default-schema-version "/subject.html") (:helpUrl subject-any)))
      (is (= :query-builder-query (:query-builder subject-any))))
    (testing "distinct colors"
      (let [all-kinds (org.parkerici.enflame.schema/kinds)]
        (= (count (distinct (map (comp :colour kind-query-blockdef) all-kinds)))
           (count all-kinds))))))

(deftest toolbox-test
  (let [tb (toolbox-def)]
    (is (= :toolbox (first tb)))
    (is (= '("Experimental" "Reference")
           (map second (rest tb))))))

(deftest block-toolbox-test
  (testing "construction of toolbox entry with nested block"
    (is (= (toolbox-def-block "subject_sample")
           [:block "subject_sample" {} [:value "V" [:block "sample_query" {}]]]))
    ))


           

