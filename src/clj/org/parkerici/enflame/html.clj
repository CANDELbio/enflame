(ns org.parkerici.enflame.html
  (:require [hiccup.core :refer [html]]))

(defn html-frame
  [title contents]
   ;; should be a template I suppose but this was faster
  (html
   [:html
    [:head
     [:title (str "Enflame: " title)]
     [:meta {:charset "UTF-16"}]
     [:link {:href "http://fonts.googleapis.com/icon?family=Material+Icons"
             :rel "stylesheet"}]
     [:link {:rel "stylesheet"

             :href "https://cdn.jsdelivr.net/npm/bootstrap@5.0.0-beta3/dist/css/bootstrap.min.css"
             :integrity "sha384-eOJMYsd53ii+scO/bJGFsiCZc+5NDVN2yr8+0RDqr0Ql0h+rP48ckxlpbzKgwra6"
             :crossorigin "anonymous"}]
     [:link {:rel "stylesheet"
             :href "enflame.css"}]
     ;; Seems to not work with bootstrap?
     [:link {:href "http://fonts.googleapis.com/icon?family=Material+Icons"
             :rel "stylesheet"}]
     ]
    [:body 
     [:div.header
      [:h1 "Enflame: " title]
      ]
     contents]
    ]))
