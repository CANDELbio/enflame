(ns org.parkerici.enflame.schema-client
  (:require [re-frame.core :as rf]
            [org.parkerici.enflame.api :as api]
            [org.parkerici.enflame.schema :as schema]
            [org.parkerici.enflame.blockly :as blockly]
            ))

;;; Some of this used to be in datomic.cljs,

(rf/reg-event-db
 :set-schema
 (fn [db [_ version]]                   ;TODO deversion
   (api/ajax-get "/api/schema"
                 {:url-params {:version version}
                  :handler (fn [response]
                             (rf/dispatch [:schema-set response]))})
   (assoc db :schema-version version)))

(rf/reg-sub
 :schema-version
 (fn [db _]
   (:schema-version db)))

(rf/reg-event-db
 :schema-set
 (fn [db [_ schema]]
   (if (= schema @schema/the-schema)
     db
     (do (schema/set-schema schema)
         (blockly/reinit)
         (assoc db :schema schema)))))
