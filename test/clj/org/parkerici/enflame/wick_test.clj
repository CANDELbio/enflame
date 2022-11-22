(ns org.parkerici.enflame.wick-test     ;TODO .candel.
  (:require [clojure.test :refer :all]
            [org.parkerici.enflame.candel.wick :as wick]
            [clojure.string :as str]))

;;; TODO → multitool
(defn stripped
  [s]
  (str/replace s #"[\s]" ""))

(defn =-w
  "String equality except for whitespace"
  [a b]
  (= (stripped a) (stripped b)))

(deftest infix-predicate-test
  (let [q '{:find ((pull ?measurement1 [:db/id]) ?percent-of-parent1),
            :where
            ([?measurement1 :measurement/percent-of-parent ?percent-of-parent1]
             [(< ?percent-of-parent1 50)])}
        c "query(find(pull(?measurement1, c(db/id))),
                 where(d(?measurement1, measurement/percent-of-parent, ?percent-of-parent1),
                       d(?percent-of-parent1 < 50)))"]
    (is (=-w c (wick/translate q)))))
