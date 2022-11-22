(ns org.parkerici.enflame.datomic
  (:require [re-frame.core :as rf]
            [org.parkerici.multitool.core :as u]
            [org.parkerici.enflame.api :as api]
            [org.parkerici.enflame.results :as results]
            [cljs.reader :as reader]
            ))

;;; TODO this isn't all that Datomic specific, maybe rename


;;; This acts as client for gaslight, that is, it does more or less what wick does
;;; 
;;; The JSON format gaslight expects:
;;; https://github.com/CANDELbio/datalog-json-parser/blob/master/test/resources/single-clause-q.json

;;; Handler gets spurious calls with 0 status and/or empty response, which we ignore. Not sure why that's happening
;;; Returns map with :count, :clipped, and :results
(defn do-query
  [ddb query args limit handler & [options]]
  (api/ajax-get "/api/query"
                (merge
                 {:url-params (u/clean-map {:db ddb :query (str query) :limit limit :args (str args)})
                  :handler handler}
                 (or options {}))))


(rf/reg-event-db
 :set-idents
 (fn [db [_  ddb idents]]
   (assoc-in db [:idents ddb] idents)))

(rf/reg-event-db
 :get-idents
 (fn [db [_ ddb]]
   (let [ddb (or ddb (:ddb db))]
     (do-query ddb
               '{:find (?x ?y), :where ([?x :db/ident ?y])}
               nil
               nil                      ;no limit
               (fn [{:keys [results _count _clipped]}]
                 ;; TODO maybe filter out the Datomic bookkeeping ones
                 (rf/dispatch [:set-idents ddb (zipmap (map first results)
                                                       (map (comp reader/read-string second) results))]))
               ))
   db))

(rf/reg-sub
 :idents
 (fn [db [_ ddb]]
   (let [ddb (or ddb (:ddb db))]
     (get-in db [:idents ddb]))))

(rf/reg-event-db
 :get-ddbs
 (fn [db _]
   (api/ajax-get
    "/api/databases"
    {:url-params {}
     :handler
     (fn [response]
       (rf/dispatch [:set-ddbs response]))})
   db))

(rf/reg-event-db
 :set-ddbs
 (fn [db [_ ddbs]]
   (let [old-ddb (:ddb db)
         ddb (if (get (set ddbs) old-ddb)
               old-ddb
               (first ddbs))]
     (when-not (= ddb old-ddb)
       (rf/dispatch [:set-ddb ddb]))
     (-> db
         (assoc :ddbs ddbs)
         (assoc :ddb ddb)))))

(rf/reg-sub
 :ddbs
 (fn [db _]
   (:ddbs db)))

(def row-limits '(20 100 500 2500 12500 62500))

(rf/reg-sub
 :row-limit
 (fn [db _]
   (:row-limit db 100)))

(rf/reg-event-db
 :set-row-limit
 (fn [db [_ row-limit]]
   (assoc db :row-limit row-limit)))

(rf/reg-event-db
 :do-query
 (fn [db [_ query]]
   (let [ddb (:ddb db)]
     (when-not (get-in db [:idents ddb])
       (rf/dispatch [:get-idents ddb]))
     ;; NOTE: only taking first element of results here
     (do-query
      ddb
      query
      nil                               ;no args
      @(rf/subscribe [:row-limit])
      #(rf/dispatch [:query-results %1])))
   (-> db
       (dissoc :error)
       (assoc :status :querying))))

(rf/reg-event-db
 :interrupt-query
 (fn [db [_]]
   ;; TODO should actually toss the connection and ensure results are ignored
   (-> db
       (dissoc :error)
       (assoc :status :interrupted))))

(rf/reg-event-db
 :query-results
 (fn [db [_ {:keys [results count clipped]}]]
   (let [idents @(rf/subscribe [:idents])
         query-cols (:find @(rf/subscribe [:query]))
         reshaped (results/reshape-results results idents query-cols)]
     (-> db
         (assoc :status :finished
                :results reshaped
                :display-columns (results/result-columns reshaped)
                :count count
                :clipped clipped)))))
