(ns org.parkerici.enflame.library.view
  (:require [org.parkerici.enflame.html :as html]
            [org.parkerici.enflame.gcs :as gcs]
            [org.parkerici.enflame.library.item :as item]
            [org.parkerici.enflame.config :as config]
            [org.parkerici.multitool.core :as u]
            ))

(defn item-link
  [item]
  (str "/index.html?library=" (::item/entityId item)))

(defn navigate-to
  [url]
  (format "window.location = '%s'" url))

(defn item-image
  [item]
  (when (::item/image item)
    [:div (::item/image item)]))

(def date-format (java.text.SimpleDateFormat. "yyyy-MM-dd' 'HH:mm"))

(defn format-date
  [int]
  (.format date-format
           (java.util.Date. int)))

(defn library-items
  []
  (map item/localize-item (gcs/list-items "EnflameItem")))

(defn view
  []
  (html/html-frame
   "Library"
   [:div.container.col-12
    [:a {:href (config/config :library :gcs-console-url)}
     "Edit in GCS console"]
    [:table.table
     [:tbody
      (for [item (reverse (sort-by ::item/date-created (library-items)))]
        [:tr {:onclick (navigate-to (item-link item))}
         [:td {:style {:max-width "300px"}} (::item/query item)]
         [:td (::item/description item)]
         [:td (item-image item)]
         [:td ((u/safely format-date) (::item/date-created item))]
         ]

        )
      ]]]))
  

