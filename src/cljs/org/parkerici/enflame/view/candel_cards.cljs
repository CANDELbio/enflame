(ns org.parkerici.enflame.view.candel-cards
  (:require
   [re-frame.core :as rf]
   [org.parkerici.enflame.view.utils :as vu]
   [reagent.dom.server]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.enflame.config :as c]
   [org.parkerici.enflame.candel.wick :as wick]
   )
  )

;;; CANDEL specific cards 

;;; TODO conceivably this could switch between multiple configurations.
;;; Hm. Haven't really been designing with that in mind but it could work.
(defn db-card []
  [vu/card "DB"
  [:div
   [:select.form-control 
    {:name "ddb"
     :style {:width "100%"}
     :on-change #(rf/dispatch [:set-ddb (-> % .-target .-value)])
     :value (or @(rf/subscribe [:ddb]) "")}
    (for [db @(rf/subscribe [:ddbs])]
      [:option {:key db :value db} db])]]])

(defn wick-card
  []
  (let [query @(rf/subscribe [:query])]
    [vu/card "Wick"
     [:pre
      (try
        (wick/translate query)
        (catch :default e
          (str "Wick generation failed: " e)))]]))
