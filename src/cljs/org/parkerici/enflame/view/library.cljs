(ns org.parkerici.enflame.view.library
  (:require
   [re-frame.core :as rf]
   [org.parkerici.enflame.view.utils :as vu]
   [reagent.dom.server]
   [org.parkerici.multitool.browser :as browser]
   [org.parkerici.enflame.config :as c]
   [org.parkerici.enflame.blockly :as blockly]
   [org.parkerici.enflame.library.save :as save]
   )
  )

;;; TODO probably some better way to do this
(defn make-share-url [ddb]
  (str "http://"
       (browser/host)
       "/index.html?"                     ;TODO better page name
       (browser/->url-params {:ddb ddb  
                              :query (blockly/base64-string)}))) ;reads the XML out of Blockly directly

;;; TODO these subs probably don't belong in views file

(rf/reg-sub
 :share-url
 (fn [{:keys [ddb]} _]
   ;; TODO
   (and ddb (not (empty? @(rf/subscribe [:query])))
        (make-share-url ddb)
        )))

(rf/reg-event-db
 :save
 (fn [db [_ description]]
   (save/save description)
   db))

;;; TODO move to its own file
(defn share-card
  []
  [vu/card "Share"
   [:div

    ;; URL/copy
    [:div
     [:h6 "URL"]
     [:input#url.form-control.smallfont
      {:name "URL"
       :style {:width "80%"
               :display "inline-block"
               :margin-top "5px"} 
       :value (or @(rf/subscribe [:share-url]) "")}]
     [:button.btn-primary.smallfont
      {:on-mouse-down (partial browser/copy-to-clipboard "url")}
      "Copy"]
     ]

    ;; Library

    [:hr]
    [:div {:style {:margin-top "10px"}}
     [:div

      [:a.float-right2 {:href "/library"
                        :target "_blank"}
       "Browse"]]
     [:h6 "Library"]

     [:div
      [:label {:for "text"} "Text"]
      [:div#text.smallfont.textbox @(rf/subscribe [:text])]        
      [:label {:for "descriptions"} "Description"]
      ;; TODO smaller font size
      [:textarea#description.form-control.smallfont
       {:rows 3
        :style {:margin-bottom "5px"}
        }]
      [:button.btn.btn-primary.smallfont.float-right
       {:on-mouse-down #(rf/dispatch [:save (.-value (.getElementById js/document "description"))])
        :disabled (not (nil? @(rf/subscribe [:query-invalid?])))
        :data-toggle "tooltip"
        :title (or @(rf/subscribe [:query-invalid?]) "")
        :style {:margin-bottom "0.5rem"}
        }
       "Save"]          
      #_
      [:button.btn.btn-danger
       {:on-mouse-down bo/clear-workspace}
       "clear"]
      ]]]
   ])
