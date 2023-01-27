(ns org.parkerici.enflame.view.utils
  (:require
   [re-frame.core :as rf]
   [reagent.dom.server]
   [clojure.string :as str]
   [org.parkerici.enflame.blockdefs :as blockdefs]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.multitool.cljscore :as cljsu]
   [org.parkerici.enflame.config :as c]
   [org.parkerici.enflame.results :as results]
   )
  )

(defn spinner
  "Make a spinner. Size 10 is big, size 1 or 2 is good"
  [& [size]]
  (let [size (or size 10)]
    [:div.text-center
     [:div.spinner-border.pici-purple {:role "status"
                                       :style {:width (str size "em")
                                               :height (str size "em")
                                               :border-width (str (/ size 10.0) "em")}}
      ]]))

(defn icon
  [icon tooltip handler & {:keys [class disabled?]}]
  [:i.icon.material-icons.vcenter
   {:on-click (when-not disabled? handler)
    :class (str/join " " (list class (if disabled? "md-inactive" "")))
    :data-toggle "tooltip"
    :title tooltip}
   icon])


(defn entity-label
  [ent]
  (or (:label ent)
      (:label/label ent)                ;ARGH
      (:id ent)
      (:db/id ent)))

;;; Very non-re-frame sue me
(defn toggle
  [id class]
  (let [elt (.getElementById js/document id)]
    (.toggle (.-classList elt) class)))

;; Suppsed to use :data-toggle / :data-target but I couldn't get it to work, hence low-level dom manipulation
(defn card
  [title body & {:keys [default-open header-extra]}]
  (let [id (gensym "id")]
    [:div.card
     [:div.card-header
      [:h2.mb-0
       [:button.btn.btn-link {:type "button"
                              :on-click #(toggle id "show")
                              :aria-expanded "true"
                              :aria-controls "compacted"}
        title]
       ;; Having this stuff with :h2 is weird, but works mostly
       header-extra]]
     [:div.collapse {:aria-labelledby "compacted-head"
                     :class (if default-open "show" nil)
                     :id id}
      [:div.card-body
       body]]]))



;;; Rendering

(defn render-browse-link
  [ent]
  [:a
   ;; This should work but doesn't, so we go through an ugly kludge
   #_ {:href "javascript:void(0)" :on-click #(rf/dispatch [:browse ent])}
   {:href (str "javascript:org.parkerici.enflame.view.browser.browse.call(null,"
               (pr-str (:id ent))
               ");")}
   (entity-label ent)])

;;; This code should be kept in sync with export in server.clj
(defn render-entity
  [ent]
  (render-browse-link ent))

(defn delist
  [thing]
  (if (and (sequential? thing)
           (= 1 (count thing)))
    (first thing)
    thing))

(declare render)
(def list-limit 3)

;;; This fits ag-grid better
(defn render-list [l idents]
  `[:span
    ~@(interpose ", "
                 (map (fn [elt]  (render elt idents))
          (take list-limit l)))
    ~(when (>= (count l) list-limit)
       [:i (str ", " (count l) " total")])])

(defn sparql-uri?
  [thing]
  (and (string? thing)
       (re-matches #"<.*>" thing)))

;;; Render attributes by short name (note: something like "uniprot/core/organism" would also work)
(defn uri-short-name
  [uri]
  (let [short-name (second (re-matches #"<.*[\/\#](.*?)>" uri))]
    ;; TODO should have full name as tooltip or something
    short-name))

(defn render
  [thingy idents]
  (let [thing (delist thingy)]
    (cond (sequential? thing)
          (render-list thing idents)
          (map? thing)
          (if-let [ident (get idents (:db/id thing))]
            (name ident)
            (render-entity thing))
          ;; CANDEL
          #_ (results/entity-id? thing)
          (sparql-uri? thing)
          (if-let [ident (get idents thing)]
            (name ident)
            (render-entity {:id thing :label (uri-short-name thing)})) ;this should happen only on :db/id
          :else
          (str thing))))
