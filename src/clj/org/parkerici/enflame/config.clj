(ns org.parkerici.enflame.config
  (:require [environ.core :as env]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [org.parkerici.multitool.core :as u]
            ))

;;; TODO Aero-ize

(def the-config (atom nil))

(defn load-config
  [file]
  (let [env-config {} ; if we want to supply some config from environment
        file-config (edn/read-string (slurp file))
        config (merge env-config file-config)]
    (pprint/pprint config)
    (reset! the-config config)))

(defn config
  ([& keys] (get-in @the-config keys))
  ([] @the-config ))

;;; Called from server
;;; TODO use version 
(u/defn-memoized read-schema [version]
  (-> (config :schema)
      slurp
      edn/read-string))


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
