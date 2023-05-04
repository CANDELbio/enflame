(ns org.parkerici.enflame.view.card.sparql
  (:require
   [re-frame.core :as rf]
   [org.parkerici.enflame.view.utils :as vu]
   [reagent.dom.server]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.enflame.api :as api]
   )
  )

;;; This should all be bundled up in an abstraction, its ridiculous to
;;; have to choreograph by hand this kind of thing over and over. TODO!

(rf/reg-event-db
 :native-fetch
 (fn [db [_ q]]
   (api/ajax-get "/api/query-translate"
                {:url-params {:query (str q)}
                 :response-format :text
                 :handler (fn [resp]
                            (rf/dispatch [:got-translation q resp]))})
   (assoc-in db [:card :sparql :native-query q] "Pending...")))

;;; A small trick, using the reframe db for memoization. Might as well!
(rf/reg-event-db
 :got-translation
 (fn [db [_ q trans]]
   (assoc-in db [:card :sparql :native-query q] trans)))

(rf/reg-sub
  :native-query
  (fn [db [_ q]]
    (or (get-in db [:card :sparql :native-query q])
        (do (rf/dispatch [:native-fetch q])
            "pending..."))))

(defn card
  []
  (let [query @(rf/subscribe [:query])
        native @(rf/subscribe [:native-query query])]
    [vu/card "SPARQL"
     [:pre
      native]]))
