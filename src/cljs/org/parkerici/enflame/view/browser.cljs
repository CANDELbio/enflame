(ns org.parkerici.enflame.view.browser
  (:require
   [org.parkerici.enflame.view.utils :as vu]
   [re-frame.core :as rf]
   [reagent.dom.server]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.enflame.blockly :as blockly]
   [org.parkerici.enflame.candel.query :as query]
   [org.parkerici.enflame.datomic :as datomic]
   [org.parkerici.enflame.results :as results]
   )
  )

;;; CANDEL only, not sure why this exists when :browse-id handler doe the same thing
(defn browser-pull
  [ddb ent handler]
  (let [kind (results/infer-kind ent)]
    (datomic/do-query
     ddb
     (query/entity-pull-query kind)
     (list (:db/id ent))
     nil
     handler
     )))

;;; This stuff is a real mess
;;; Old CANDEL version
#_
(rf/reg-event-db
 :browse-id
 (fn [db [_ ddb id kind]]
   (when ddb (rf/dispatch [:set-ddb ddb]))
   (datomic/do-query
    (or ddb (:ddb db))
    (query/entity-pull-query kind)
    (list id)
    nil
    #(rf/dispatch [:browse-results %])
    )
   (-> db
;;; Would be nice...
;       (assoc-in [:browse :browsing] ent)
       (assoc-in [:browse :spin?] true))
   ))

;;; Probably should have uid as string, this is stupid. 
(defn sparql-pull-query
  [id kind]
  (prn :spq id kind)
  (u/de-ns
   `(:project (?p ?o)
              (:bgp
               [~id ?p ?o]))))



(defn sparql-do-pull
  [id]
  (datomic/do-query
    nil
    (sparql-pull-query id nil)
    nil
    nil
    #(rf/dispatch [:browse-results %])
    ))

;;; SPARQL version
(rf/reg-event-db
 :browse-id
 (fn [db [_ id kind]]
   (sparql-do-pull id)
   (-> db
;;; Would be nice...
       ; (assoc-in [:browse :browsing] ent)
       (assoc-in [:browse :spin?] true))
   ))

;;; TODO changing db should invalidate :browse state and perhaps other things
;;; CANDEL
(rf/reg-event-db
 :browse-0
 (fn [db [_ ent ddb]]
   (browser-pull
    (or ddb (:ddb db))                  ;TODO maybe set ddb if if isn't right
    ent
    #(rf/dispatch [:browse-results %]))
   (-> db
       (assoc-in [:browse :browsing] ent)
       (assoc-in [:browse :spin?] true))))

;;; SPARQL
(rf/reg-event-db
 :browse-0
 (fn [db [_ ent ddb]]
   (sparql-do-pull ent)
   (-> db
       (assoc-in [:browse :browsing] ent)
       (assoc-in [:browse :spin?] true))))

(rf/reg-event-db
 :browse
 (fn [db [_ ent ddb]]
   (let [ddb (or ddb (:ddb db))]
     (rf/dispatch [:browse-0 ent ddb])
     (-> db
         (update-in [:browse :history] conj ent)
         (assoc-in [:browse :index] 0)))))

(rf/reg-sub
 :history
 (fn [db _]
   (get-in db [:browse :history])))

(rf/reg-event-db
 :move-history 
 (fn [db [_ inc]]
   (let [{:keys [history index]} (:browse db)
         new-index (+ index inc)]
     (rf/dispatch [:browse-0 (nth history new-index)])
     (assoc-in db [:browse :index] new-index))))

(rf/reg-sub
 :can-move-history
 (fn [db [_ inc]]
   (let [{:keys [history index]} (:browse db)
         new-index (+ index inc)]
     (and (>= new-index 0)
          (< new-index (count history))))))

;;; CANDEL version
#_
(rf/reg-event-db
 :browse-results
 (fn [db [_ {:keys [results]}]]
   ;; This is still a bit hinky
   (let [object (->  (ffirst results)
                     (results/regularize-entity :browser (get-in db [:idents (:ddb db)]))
                     results/reshape-top-entity)]

     (-> db
         (assoc-in [:browse :data] object)
         (assoc-in [:browse :spin?] false)
         (update-in [:browse :history] conj object)
         ))))

;;; SPARQL version
(defn reshape-sparql
  [results]
  (reduce (fn [obj row]
            (update obj (:p row) conj (or (:o row) (:s row))))
          {}
          results))

(rf/reg-event-db
 :browse-results
 (fn [db [_ {:keys [results]}]]
   (prn :results results)
   ;; This is still a bit hinky
   (let [object (reshape-sparql results)]

     (-> db
         (assoc-in [:browse :data] object)
         (assoc-in [:browse :spin?] false)
         (update-in [:browse :history] conj object)
         ))))

(rf/reg-sub
 :browse
 (fn [db _]
   (:browse db)))

;;; This is called by javascript from ag-grid
(defn ^:export browse
  [id kind]
  (prn :browse id kind)
  (rf/dispatch [:browse-id id kind]))

(defn history
  [data browsing]                       ;TODO Are both these necessary
  [:div.form-inline
   (vu/icon "chevron_left" "Previous"
            #(rf/dispatch [:move-history 1])
            :class "md-big"
            :disabled? (not @(rf/subscribe [:can-move-history 1])))
   [:select#historysel.form-select
    {:name "history"
     :style {:width "50%"}
     :on-change #(rf/dispatch [:browse {:db/id (js/parseInt (-> % .-target .-value))} nil])
     ;; :value (or @(rf/subscribe [:Foo]) "")
     }
    (for [ent @(rf/subscribe [:history])]
      [:option {:key (:db/id ent) :value (:db/id ent)}
       (str (vu/entity-label ent) " (" (if (:kind ent) (name (:kind ent)) "?") ")" )])]
   (vu/icon "chevron_right" "Next"
            #(rf/dispatch [:move-history -1])
            :class "md-big"
            :disabled? (not @(rf/subscribe [:can-move-history -1])))
   (when (blockly/blockifiable? (merge data browsing))
     [:button.btn.btn-primary.btn-sm
      ;; ??? merge
      {:on-mouse-down #(rf/dispatch [:blockify (merge data browsing)])}
      "Blockify"])])

(defn browser-guts
  []
  (let [{:keys [browsing data spin?]} @(rf/subscribe [:browse])
        idents @(rf/subscribe [:idents])]
    (when data ; browsing
      [:div
       [:label {:for "historysel"} "History"]
       [:div.browser_history
        ;; History header / spinner
        (if spin?
          [vu/spinner 2]
          [history data browsing]
          )]
       ;; Object itself
       [:table
        [:tbody
         (let [sorted (sort-by first (dissoc data :kind :label :label/label :db/id))]
           (for [[k v] sorted]
             ^{:key k}
             (let [kind (if (sequential? v)
                          (:kind (first v))
                          (:kind v))
                   css-class (when kind (str (name kind) "-kind"))]
             [:tr {:class (if (= k (ffirst sorted)) "browser_head" "browser_row")}
              [:th {:class (str "browser_row_label" " " css-class)}
               ;; TODO maybe these don't want to be links? Cute though, and sometimes they have interesting attributes of their own
               (vu/render k nil)]
              [:td {:class "browser_row_contents"}
               (vu/render v idents)]])))]]
       ])))

(defn browser
  []
  [vu/card "Browser"
   [browser-guts]
   :default-open true])
