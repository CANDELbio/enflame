(ns org.parkerici.enflame.db
  (:require [org.parkerici.enflame.config :as c]
            [re-frame.core :as rf]))

(def default-db
  {:name "enflame"
   })

(rf/reg-event-db
 :initialize-db
 (fn [db _]
   (when-let [ddb (c/get-local :ddb)]
     (rf/dispatch [:set-ddb ddb]))
   (merge db default-db)))
