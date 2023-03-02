(ns org.parkerici.enflame.admin
  (:require [environ.core :as env]
            [org.parkerici.enflame.html :as html]
            [hiccup.util :as hu]
            [clojure.java.shell :as sh]
            [org.parkerici.multitool.core :as u]
            ))

(defn map-table
  [name map]
  [:div [:h2 name]
   [:table.table-bordered
    (for [key (sort (keys map))]
      [:tr
       [:th (str key)]
       [:td (if (map? (get map key))
              (map-table "" (get map key))
              (hu/escape-html (str (get map key))))]])]])

(defn git-info []
  (u/ignore-errors
   {:commit (:out (sh/sh "git" "log" "-1" "--format=short"))
    :branch (:out (sh/sh "git" "rev-parse" "--abbrev-ref" "HEAD"))}))

(defn view
  [req]
  (html/html-frame
   "Admin"
   [:div
    (map-table "Version" (merge (select-keys env/env [:enflame-version :java-version])
                                (git-info)))
    (map-table "Env" env/env)
    (map-table "System/getenv"(System/getenv))
    (map-table "HTTP req" req)
    ]))
