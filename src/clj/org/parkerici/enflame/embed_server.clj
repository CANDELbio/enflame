(ns org.parkerici.enflame.embed-server
  (:require [ring.util.response :as response]
            )
  (:use [hiccup.core]))

(defn embed-iframe-contents
  [db query]
  (response/content-type
   (response/response
    (html
     ;; Doing this because the blockly region isn't managed by React – it probably should be
     [:html.embedc
      [:head
       [:link {:rel "stylesheet"
               :href "http://fonts.googleapis.com/icon?family=Material+Icons"}]
       [:link {:rel "stylesheet"
               :href "https://stackpath.bootstrapcdn.com/bootstrap/4.5.2/css/bootstrap.min.css"
               :integrity "sha384-JcKb8q3iqJ61gNV9KGb8thSsNjpSL0n8PARn9HuZOnIxN0hoP+VmmDGMN5t9UJ0Z"
               :crossorigin "anonymous"}]
       [:link {:rel "stylesheet" :href "enflame.css"}]
       ]

      [:body
       ;; TODO draggable boundary between parts. Use css resize: horizontal properyty, but needs rejiggering of the other layout.
       [:div.row
        [:div.col-5.px-0
         [:div#blocklyEmbed {:style {:height "300px" :width "100%"}}] 
         ]
        [:div.col-7
         ;; style="height:300px;  width: 100%; overflow-y: scroll;
         [:div.scrollbar.scrollbar-primary {:style {:height "300px" :width "100%" :overflow-y "scroll"}}
          [:div#results.force-overflow]]]]
       [:script {:src "js/compiled/app.js"}]]
      [:script "org.parkerici.enflame.embed.init();"]
      ]))
   "text/html"))

;;; Not actually used, right now these are embedded in doc/guide.org
(defn iframe
  [ddb query]
  [:iframe.embedi
   {:src (str "embed?ddb=" ddb "&query=" query)}])

