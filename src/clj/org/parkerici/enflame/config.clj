(ns org.parkerici.enflame.config
  (:require [environ.core :as env]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [org.parkerici.multitool.core :as u]
            [org.parkerici.enflame.schema :as schema]
            [clojure.java.io :as io]
            ))

;;; TODO Aero-ize

(def the-config (atom nil))

(defn config
  [& keys]
  (assert @the-config "Config not set")
  (get-in @the-config keys))

;;; Called from server
;;; Here because schema is cljc
(defn read-schema [from]
  (-> from
      io/resource
      slurp
      edn/read-string))

(defn load-config
  [file]
  (let [env-config {} ; if we want to supply some config from environment
        file-config (edn/read-string (slurp (io/resource file)))
        config (merge env-config file-config)]
    (pprint/pprint config)
    (reset! the-config config)
    (schema/set-schema (read-schema (config :schema)))))




;;; TODO
:port 
:dev? 
:schema                                 ;schema file or url (or)
:schemas                                ;map of names to above
:library                                ;{:endpoint :credentials etc}
:generator                              ; :candel or other
:kind-colors                            ;see blockdefs.cljc
:complex-constraints
:rh-cards []
:query-generator
:query-executor
