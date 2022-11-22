
(ns org.parkerici.enflame.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [org.parkerici.multitool.browser :as browser]
   [org.parkerici.enflame.blockly :as blockly]
   [org.parkerici.enflame.view.aggrid :as ag]
   [org.parkerici.enflame.views :as views]
   [org.parkerici.enflame.db]
   [org.parkerici.enflame.config :as config]
   org.parkerici.enflame.embed
   org.parkerici.enflame.schema-client
   ))

;;; TODO: I've been a bad user of re-frame, since a lot of event handlers do their own side effects.
;;; See https://github.com/day8/re-frame/blob/master/docs/EffectfulHandlers.md for the right way, maybe fix someday

(defn dev-setup
  []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

(defn mount-root
  []
  (rf/clear-subscription-cache!)
  (rdom/render [views/rh-panel]
    (.getElementById js/document "app"))
  (rdom/render [views/results]
    (.getElementById js/document "results"))
  )

(defn ^:export re-frame-init
  []
  (rf/dispatch-sync [:initialize-db])
  (dev-setup)
  (mount-root))

;;; This implements a kind of wait function. Trigger is a fn that takes db as argument, Event is an event.
;;; Dispatch event if trigger is true, otherwise go into a wait loop.
(rf/reg-event-fx
 :dispatch-when
 (fn [cofx [_ trigger event]]
   (if (trigger (:db cofx))
     {:dispatch event}
     {:dispatch-later [{:ms 100 :dispatch [:dispatch-when trigger event]}]})))

(defmulti custom-init (comp :type :source))

(defmethod custom-init :default
  [_]
  )

(defmethod custom-init :candel
  [_]
  (rf/dispatch [:get-ddbs])            
  (let [{:keys [library ddb query] :as _params} (browser/url-params)]
    (if-let [ddb (or ddb (config/get-local :ddb))]
      (rf/dispatch [:set-ddb ddb])
      (rf/dispatch [:set-schema]))
    (when query
      ;; Wait for schema to be set
      (rf/dispatch [:dispatch-when :schema [:set-query query]]))
    ;; NOt really CANDEL specifica
    (when library
      ;; Wait for schema to be set
      (rf/dispatch [:dispatch-when :schema [:library-load library]])))
  )


(defn ^:export init
  []
  (config/init #(do
                  (custom-init (config/config))
                  (re-frame-init)
                  (blockly/init :id "blocklyArea")
                  ))
  (ag/init)
  )

