(ns org.parkerici.enflame.config
  (:require [org.parkerici.enflame.api :as api]
            [org.parkerici.enflame.sparql.generate :as sparql-generate]
            [org.parkerici.enflame.candel.query :as candel-generate]
            )
  )

(def debug?
  ^boolean goog.DEBUG)

(defn put-local
  [tag value]
  (let [item (str "org.parkerici.enflame." (name tag))]
    (.setItem (.-localStorage js/window) item (str value))))

(defn get-local
  [tag]
  (let [item (str "org.parkerici.enflame." (name tag))]
    (.getItem (.-localStorage js/window) item))) 

;;; Do this or do it through re-frame?
(defonce the-config (atom nil))

(defn config
  ([key] (get @the-config key))
  ([] @the-config ))

;;; Get the config from the server
(defn init
  [cont]
  (api/ajax-get "/api/config"
                {:handler #(do (reset! the-config %)
                               (cont %))}))

;;; TODO must be better way to do this but I'm lasy
(def functions
  {:sparql-generate sparql-generate/build-top-query
   :candel-generate candel-generate/build-top-query})

(defn funcall
  [att & args]
  (let [f (get functions (get @the-config att))]
    (when-not f (throw (ex-info "Missing config value" {:config att})))
    (apply f args)))
