(ns org.parkerici.enflame.test-utils
  (:require [clojure.test :refer :all]
            [org.parkerici.enflame.server :as server]
            [org.parkerici.enflame.schema :as schema]
            [org.parkerici.enflame.config :as config]
            ))

(use-fixtures :once
  (config/load-config "test/resources/test-config.edn"))


(defn with-schema [f]
  (schema/set-schema (config/read-schema nil nil)) ;TODO args
  (f))
