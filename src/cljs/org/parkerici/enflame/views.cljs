(ns org.parkerici.enflame.views
  (:require
   [re-frame.core :as rf]
   [org.parkerici.enflame.view.utils :as vu]
   [org.parkerici.enflame.view.browser :as obrowser]
   [org.parkerici.enflame.view.graph :as graph]
   (org.parkerici.enflame.view.aggrid :as ag)
   [reagent.dom.server]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [org.parkerici.multitool.core :as u]
   [org.parkerici.multitool.browser :as browser]
   [org.parkerici.enflame.config :as c]
   [org.parkerici.enflame.candel.query :as query] ;TOODO
   [org.parkerici.enflame.datomic :as datomic]
   [org.parkerici.enflame.view.candel-cards :as candel]
   [org.parkerici.enflame.view.library :as library]
   )
  )

;;; TODO this file should be split up (done in part)

(rf/reg-sub
 :query
 (fn [db _]
   (if (:query-text db)
     nil #_ (ignore-errors (cljs.reader/read-string (:query-text db)))
     (:query db))))

(rf/reg-sub
 :query-text
 (fn [db _]
   (or (:query-text db)
       (with-out-str (pprint/pprint (:query db))))))

(rf/reg-sub
 :query-edited?
 (fn [db _]
   (not (nil? (:query-text db)))))

(rf/reg-event-db
 :set-query-text
 (fn [db [_ text]]
   (assoc db :query-text text)))

(rf/reg-sub
 :compact
 (fn [db _]
   (:compact db)))

(rf/reg-sub
 :compact-all
 (fn [db _]
   (:compact-all db)))

(rf/reg-sub
 :xml
 (fn [db _]
   (:xml db)))

(rf/reg-sub
 :query-block
 (fn [db _]
   (let [compact (:compact db)]
     (if (= (:type compact) "layer")
       (get-in compact [:children "data"])
       compact))))

(rf/reg-sub
 :text
 (fn [db _]
   (:text db)))

(rf/reg-sub
 :clipped
 (fn [db _]
   (:clipped db)))

(rf/reg-sub
 :count
 (fn [db _]
   (:count db)))

(rf/reg-sub
 :results
 (fn [db _]
   (:results db)))

(rf/reg-sub
 :status
 (fn [db _]
   (:status db)))

(rf/reg-sub
 :display-columns
 (fn [db _]
   (:display-columns db)))








(def list-limit 3)


;;; Note: thing is in #js form for efficiency
;;; Produce text for filtering (that is, it should be textually same as display)
(defn render-text
  [thing]
  (cond (string? thing) thing
        (nil? thing) nil
        (array? thing)
        (str/join " " (render-text (map render-text thing)))
        (.-label thing) (.-label thing)
        :else                           ;shouldn't happen but good to have backstop
        (str thing)))

(defn no-matches
  []
  [:h3 "No matching data"])

(defn table
  []
  ;; Sort is not quite what you want, but without it the columns of different entities get jumbled
  (let [cols (sort @(rf/subscribe [:display-columns]))
        idents @(rf/subscribe [:idents])
        ;; Note: data can only contain prims, maps, vectors
        ;; Keywords get turned into strings
        data @(rf/subscribe [:results])
        ;; First non-null value in column
        sample-value (fn [col] (some #(col %) data))]
    [:div {:style {:height "600px" :width "100%"}}
     (ag/ag-table
      :data
      (map (fn [col-key]
             (let [sample (sample-value col-key)]
               (merge
                {:field col-key
                 ;; Specify background color
                 :headerClass (and (namespace col-key) (str (namespace col-key) "-kind"))}
                (cond (number? sample)
                      {:filter "agNumberColumnFilter"}
                      (string? sample)
                      {}
                      (nil? sample)
                      {}            ;could turn off filter I suppose
                      ;; It's an entity or list, so go through the complicated process
                      :else
                      {:cellRenderer
                       (fn [params]
                         (let [value (.-value params)]
                           (reagent.dom.server/render-to-string
                            (vu/render (js->clj value :keywordize-keys true) idents))))
                       :comparator (fn [v0 v1 _ _ invert]
                                     (compare (render-text v0) (render-text v1)))
                       :filterParams
                       {:textFormatter render-text
                        }}))))
           cols)
      data
      { })]))

(rf/reg-sub
 :error
 (fn [db _]
   (:error db)))

(rf/reg-sub
 :ddb
 (fn [db _]
   (:ddb db)))

(defn error
  [[status response]]
  [:div {:style {:color "red"}}
   (str status ": " response)])

(defn download-link
  []
  [:a {:href (str "/download?"
                  (browser/->url-params {:db @(rf/subscribe [:ddb])
                                         :query (str @(rf/subscribe [:query]))}))
       :download "enflame-results.tsv"} 
   "Download"])

;;; Called from core
(defn results
  []
  (if-let [err @(rf/subscribe [:error])]
    [error err]
    [:div
     (when-not (empty? @(rf/subscribe [:results]))
       [:div
        [:span (str "Showing " @(rf/subscribe [:clipped]) " of " @(rf/subscribe [:count]) " ")]
        [download-link]])                    ;hide if no data
     [table]
     ]))


;;; TODO probably belongs elsewhere? datomic.cljs?
(rf/reg-event-db
 :set-ddb
 (fn [db [_ ddb]]
   (c/put-local :ddb ddb) ;save in local storage to use as default
   (rf/dispatch [:get-idents ddb])
   ;; TODO deversion
   ;; Get the version
   (datomic/do-query ddb
                     '{:find [?version] :where [[_ :candel.schema/version ?version]]}
                     []
                     nil
                     #(rf/dispatch [:set-schema (ffirst (:results %))])
                     {:error-handler #(rf/dispatch [:set-schema "1.2.1"])}) ;TODO should get from default-schema
   (assoc db :ddb ddb)
   ))

(defn graph-pane
  []
  (when-let [guts (graph/render)]
    [vu/card "Graph"        
     guts
     :default-open true]))

(defn toplink
  [label url]
  [:a.toplink {:href url
               :target "_blank"}
   label])

(defn interrupter
  []
  (vu/icon "stop_circle" "interrupt" #(rf/dispatch [:interrupt-query])))

(defn query-button
  [{:keys [row-limit? label left?] :or {row-limit? true label "Go"}}]
  (let [query @(rf/subscribe [:query])]
    (if (= :querying @(rf/subscribe [:status]))
      [:span {:class (if left? "float-left")}
       [:i "querying"]
       [:div.loader]
       (interrupter)]
      ;; TODO would rather do this through a block...cmon
      [:span
       (when row-limit?
         [:select#limitsel.form-select-sm
          {:name "limit"
           :style {:display "inline"
                   :width "inherit"
                   :margin-right "5px"
                   :font-size "16pt"
                   :vertical-align "middle"
                   }
           :on-change #(rf/dispatch [:set-row-limit (js/parseInt (-> % .-target .-value))])
           :value (or @(rf/subscribe [:row-limit]) "")}
          (for [limit '("Row limit" 20 100 500 2500 12500 62500)]
            [:option {:key limit :disabled (string? limit)} (str limit)])])
       [:button.btn.btn-primary.m-1
        {:class (if left? "float-left" "float-right")
         :on-mouse-down #(rf/dispatch [:do-query query])
         ;; TODO get this working again
         ;; :disabled   (boolean @(rf/subscribe [:query-invalid?]))
         :data-toggle "tooltip"
         ;; TODO add the js necessary to make this pretty (not sure it's worth it) (also see tooltip in Library pane)
         ;;          :data-placement "top"
         :title (or @(rf/subscribe [:query-invalid?]) "")
         }
        label]])))

(defn query-card
  []
  [vu/card "Query"
   [:pre {:style {:text-size "small"}}
    @(rf/subscribe [:query-text])]
   :default-open true
   :header-extra
   [:span.float-right2
    [query-button]
    ]])

(defn compact-card
  []
  ;; Can turn these on for debugging purposes
  ;; TODO put under config
  [vu/card "Compacted"
   [:pre
    (with-out-str (pprint/pprint @(rf/subscribe [:compact])))]])

(defn xml-card
  []
  [vu/card "XML"
   [:pre
    (with-out-str (pprint/pprint @(rf/subscribe [:xml])))]])

;;; Returns nil (valid) or a string explaining why invalid.
;;; TODO feels like this logic should be elsewhere
(rf/reg-sub
 :query-invalid?
 (fn [_ _]
  false
   #_                                   ;TODO CANDEL Specific
   (let [query @(rf/subscribe [:query])
         query-block @(rf/subscribe [:query-block])]
     (cond 
       (not (:output (query/spec-block-def query-block)))
       "Outermost block must have output pin (on left)" 
       (nil? query)
       "Query unparsable"
       (empty? (:find query))
       "No query results specified"
       ))))


;;; Hide column logic (stolen from Rawsugar)

(rf/reg-event-db
 :hide-col
 (fn [db [_ col]]
   (update-in db [:display-columns] #(u/remove= col %))))

(def card-defs
  {:candel/db candel/db-card
   :candel/wick candel/wick-card
   :query query-card
   :compact compact-card
   :compacted compact-card              ;temp
   :xml xml-card
   :share library/share-card
   :browser obrowser/browser
   :graph graph-pane})

(defn rh-panel
  []
  [:div {:style {:margin-top "10px"}}
   [:div
    ;; TODO customize these
    (toplink [:span [:b "Enflame "] [:img {:src "favicon.ico" :width "24px"}]] "http://github.com/ParkerICI/enflame")
    (toplink "Tutorial" "/doc/tutorial.html")
    (toplink "Doc" "/doc/guide.html")
    (toplink "Schema" (str "/schema/" "" "/index.html")) ;TODO version
    (toplink "Library" "/library")]

   [:div#accordian.accordian
    (for [card (c/config :rh-cards)
          :let [cdef (get card-defs card)]]
      (if cdef
        ^{:key card}[cdef]
        (throw (ex-info "No card defined for" {:card card}))
        ))
    ]])
