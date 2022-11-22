(ns org.parkerici.enflame.blockly
  (:require [clojure.data.xml :as xml]
            [org.parkerici.blockoid.core :as bo]
            cljsjs.blockly.msg.en
            [org.parkerici.enflame.schema :as schema] ;TODO probably shouldn't be here
            [org.parkerici.enflame.blockdefs :as blockdefs]
            [org.parkerici.enflame.view.graph :as graph]
            [org.parkerici.enflame.config :as config]            
            [org.parkerici.multitool.core :as u]
            [clojure.string :as str]
            [goog.crypt.base64 :as b64]
            [org.parkerici.multitool.browser :as browser]
            [re-frame.core :as rf]
            ))
  
(rf/reg-event-db
 :blocks-changed
 (fn [db _]
   (let [xml (bo/workspace-selected-xml)
         compact (bo/compact xml)
         compact-all (bo/compact (bo/workspace-xml))
         query (config/funcall :query-generator compact)
         ]
     (assoc db
            :xml xml
            :compact compact
            :compact-all compact-all
            :query query
            ;; :query-text nil             ;this is only set when user hand-edits query
            ;; :text text                  ;TODO
            ))))

(rf/reg-event-db
 :error
 (fn [db [_ status response]]
   (assoc db 
          :error (vector status response)
          :status :error)))

;;; Remember if we are embedded, for reinit
(def hide-toolbox? (atom false))

;;; Some possible options
#_
{:zoom {:controls true :wheel true :startScale 1.0 :maxScale 3 :minScale 0.3 :scaleSpeed 1.2} :renderer "thrasos"}

;;; standard Blockly init
(defn init
  [& {:keys [id workspace-options embedded?] :or {workspace-options {}}}]
  (reset! hide-toolbox? embedded?)
  (bo/define-blocks (blockdefs/schema-defs))
  (bo/define-workspace
    id
    (bo/toolbox (blockdefs/toolbox-def)) ;TODO can omit for embedded probably?
    workspace-options
    (fn [_]
      (rf/dispatch [:blocks-changed]))
    )
  (when-not embedded?
    (bo/auto-resize-workspace id)))

(defn reinit
  []
  (when @bo/workspace
    (bo/define-blocks (blockdefs/schema-defs))
    (when-not @hide-toolbox?
      (bo/update-toolbox (bo/toolbox (blockdefs/toolbox-def)))
      (.hide (.getFlyout @bo/workspace))    ;hide the flyout which for some reason gets exposed (TODO move to blockoid, if it can't be handled better)
      )))

;;; Save/restore

(defn base64-string []
  (b64/encodeString (bo/workspace-xml-string)))


(defn save-fake-fields!
  [xml-string]
  (->> xml-string
       xml/parse-str
       (u/walk-collect #(when (= "field" (get-in % [:attrs :name]))
                          (keyword (first (get % :content)))))
       (reset! graph/fake-fields)))

(defn restore-from-saved
  [xml-string]
  (save-fake-fields! xml-string)
  (let [dom (.textToDom js/Blockly.Xml xml-string)]
    (bo/clear-workspace)
    (.domToWorkspace js/Blockly.Xml dom @bo/workspace)))

(rf/reg-event-db
 :set-query
 (fn [db [_ base64]]
   (restore-from-saved (b64/decodeString base64))
   (rf/dispatch [:blocks-changed])
   db))

;;; Blockify support

(defn blockifiable?
  "True if entity-block can be applied to entity"
  [entity]
  (let [kind (:kind entity)
        unique-id (schema/kind-unique-id kind)]
    (and unique-id (unique-id entity))))

;;; TODO note that the so-called unique-id is not always unique, so this won't always do the
;;; expected thing. Could search for an attribute that is actually unique and use that....
(defn entity-block
  "Given an entity, generate the query block XML that will produce it. "
  [entity]
  (let [kind (:kind entity)
        unique-id (schema/kind-unique-id kind)
        query-block-type (str (name kind) "_query")
        constraint-block-type (str (name kind) "_" (name unique-id))
        attribute-value (unique-id entity)
        attribute-value (if (seq? attribute-value) ; kludge for tuple values
                          (str/join "|" attribute-value)
                          attribute-value)
        ]
    `{:tag :xmlns.https%3A%2F%2Fdevelopers.google.com%2Fblockly%2Fxml/xml
      :attrs {}
      :content
      ({:tag
        :xmlns.https%3A%2F%2Fdevelopers.google.com%2Fblockly%2Fxml/block
        :attrs
        {:type ~query-block-type}
        :content
        ({:tag
          :xmlns.https%3A%2F%2Fdevelopers.google.com%2Fblockly%2Fxml/field
          :attrs {:name "output"}
          :content ("include")}
         {:tag :xmlns.https%3A%2F%2Fdevelopers.google.com%2Fblockly%2Fxml/statement
          :attrs {:name "constraint"},
          :content
          ({:tag
            :xmlns.https%3A%2F%2Fdevelopers.google.com%2Fblockly%2Fxml/block,
            :attrs {:type ~constraint-block-type},
            :content
            ({:tag
              :xmlns.https%3A%2F%2Fdevelopers.google.com%2Fblockly%2Fxml/field,
              :attrs {:name "comp"},
              :content ("is")}
             {:tag
              :xmlns.https%3A%2F%2Fdevelopers.google.com%2Fblockly%2Fxml/field,
              :attrs {:name "V"}, 
              :content (~attribute-value)})})})})}))

(defn entity->xml
  [entity]
  (-> entity
      entity-block
      xml/emit-str))                     ;this is necessary to get proper xml entit

;;; Now in multitool/cljs
(defn open-in-browser-tab
  [url name]
  (let [win (.open js/window name)]
    (.assign (.-location win) url)))

;;; Duplicate (mostly) from views
(defn make-share-url [ddb xml-str]
  (str "http://"
       (browser/host)
       "/index.html?"                     ;TODO better page name
       (browser/->url-params {:ddb ddb  
                              :query xml-str})))

(rf/reg-event-db
 :blockify
 (fn [db [_ entity]]
   (let [xml (entity->xml entity)
         location (.-pathname (.-location js/window))]
     ;; Enflame main: add block to existing workspace
     (bo/add-workspace-xml (xml/parse-str xml)))
   db))




     


