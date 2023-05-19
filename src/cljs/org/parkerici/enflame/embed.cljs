(ns org.parkerici.enflame.embed
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as rf]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.multitool.browser :as browser]
   [org.parkerici.enflame.blockly :as blockly]
   [org.parkerici.enflame.views :as views]
   [org.parkerici.enflame.view.graph :as graph]
   [org.parkerici.enflame.db]
   [org.parkerici.enflame.config :as config]
   ))

;;; Alternative to core for embedded

(rf/reg-sub
 :active-tab
 (fn [db [_ id]]
   (get-in db [:active-tab id])))

(rf/reg-event-db
 :choose-tab
 (fn [db [_ id tab]]
   (assoc-in db [:active-tab id] tab)))

;;; → web-utils, if it existed...
(defn tabs
  [id tabs]
  (let [active (or @(rf/subscribe [:active-tab id]) (first (first tabs)))]
    [:div
     [:ul.nav.nav-tabs
      (for [[name view] tabs]
        [:li.nav-item
         [:a.nav-link {:class (when (= name active) "active")
                       :on-click #(rf/dispatch [:choose-tab id name])}
          name]])]
     ((tabs active))]))

;;; The part of an embed that is not managed by blockly
(defn rest-pane
  [view]
  [:div
   [:a {:href @(rf/subscribe [:share-url]) :target "_realenflame"} "Open in Enflame"]
   [views/query-button {:row-limit? false :label "Query" :left? true}]
   (case view
     "graph"
     (tabs :foof {"graph" graph/render
                  "data" views/results})
     (views/results))])

(defn re-frame-init
  []
  (rf/dispatch-sync [:initialize-db])
  (rf/clear-subscription-cache!))

;; Probably this would be better if blockly was a real react component.
(defn init-embed
  []
  (let [{:keys [ddb query view rows] :as _params} (browser/url-params)]
    (let [blockly-id "blocklyEmbed"
          results-id "results"
          rows (or (u/coerce-numeric-hard rows) 100)
          ]
      (rf/dispatch [:set-ddb ddb])
      ;; See https://developers.google.com/blockly/guides/configure/web/configuration_struct
      (reagent/render [rest-pane view]
        (.getElementById js/document results-id))

      (blockly/init
       :id blockly-id
       :workspace-options {:readOnly false
                           :trashcan false
                           :scrollbars false
                           :toolbox false ;this will want to be different fro some examples
                           }
       :embedded? true)
      (rf/dispatch [:set-row-limit rows])
      (rf/dispatch [:dispatch-when :schema [:set-query query]]))))


