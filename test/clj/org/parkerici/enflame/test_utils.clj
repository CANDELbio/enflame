(ns org.parkerici.enflame.test-utils
  (:require [clojure.test :refer :all]
            [org.parkerici.enflame.config :as config]
            ))

(defn with-test-config
  [f]
  (config/load-config "test/test-config.edn")
  (f))

;;; Put this in individual test files
#_ (use-fixtures :once with-test-config)



