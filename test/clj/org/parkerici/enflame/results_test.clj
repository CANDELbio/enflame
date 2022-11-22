(ns org.parkerici.enflame.results-test
  (:require [clojure.test :refer :all]
            [org.parkerici.enflame.results :refer :all]))

(deftest infer-kind-test
  (let [r0 {:db/id 102020, :kind :drug}
        r1 '{:db/id 17592186331045, :subject/id "400-0001", :subject/uid "pici0001/400-0001"}]
    (is (= :drug (infer-kind r0)))
    (is (= :subject (infer-kind r1)))
    ;; TODO test column argument
    ))

(deftest regularize-entity-test
  (let [r1 '{:db/id 17592186331045, :subject/id "400-0001", :subject/uid "pici0001/400-0001",
             :subject/sex {:db/id 17592186045459},
             :subject/race {:db/id 17592186045461},
             :subject/therapies [{:db/id 17592189302964, :therapy/treatment-regimen {:db/id 17592188499170}, :therapy/line 2, :therapy/order 2}]}
        regularized (regularize-entity r1 nil {})
        therapy-regularized (first (:subject/therapies regularized))
        treatment-regimen-regularized (:therapy/treatment-regimen therapy-regularized)]
    (is (= :subject (:kind regularized)))
    (is (= "400-0001" (:label/label regularized)))
    (is (= :therapy (:kind therapy-regularized)))
    (is (= :treatment-regimen (:kind treatment-regimen-regularized)))
    ))

(deftest reshape-top-test
  (let [r1 '{:db/id 17592186331045, :subject/id "400-0001", :subject/uid "pici0001/400-0001",
             :subject/sex {:db/id 17592186045459},
             :subject/race {:db/id 17592186045461},
             :subject/therapies [{:db/id 17592189302964, :therapy/treatment-regimen {:db/id 17592188499170}, :therapy/line 2, :therapy/order 2}]}
        reshaped-results (reshape-top-item r1 '?subject1 {})
        reshaped-browser (reshape-top-item r1 :browser {})]
    ;; :label/label gets added, :kind does not
    (is (= nil (:kind reshaped-results)))
    (is (= "400-0001" (:label/label reshaped-results)))
    ;; Magic inner object column created, with funny label
    (is (= {:db/id 17592186331045, :label/label "400-0001", :kind :subject}
           (get reshaped-results (keyword "subject" " * subject"))))

    ;; Browser version contains unique-id field, normal version does not
    (is (= "400-0001" (:subject/id reshaped-browser)))
    (is (not (:subject/id reshaped-results)))
    (is (= reshaped-results (dissoc reshaped-browser :subject/id)))))


;; TODO test processing of count results
    
