(ns org.parkerici.enflame.view.graph
  (:require [org.parkerici.multitool.core :as u]
            [re-frame.core :as rf]
            [oz.core :as oz]
            ))

;;; Block definitions

(defn options
  [key-list]
  (mapv (juxt name name) key-list))

;;; Extremely kludgey trick to allow field settings to be restored even when the query results aren't present
(defonce fake-fields (atom [:?]))

(defn field-options
  "Compute the options for field dropdown at runtime. Value is js arra pf arrapus"
  []
  (let [cols @(rf/subscribe [:display-columns])
        cols (if (empty? cols)
               @fake-fields
               cols)] 
    (to-array (map #(to-array [(name %) (name %)]) cols))))

(def marks
  [:point :bar :line :tick :arc :area :boxplot])

(def attributes                         ;aka channels. There are more; these seemed like the most useful
  [:x :y :size :color :opacity :shape :theta :radius :strokeWidth :facet])

;;; Count is special (no underlying field) so has its own block. See https://vega.github.io/vega-lite/docs/aggregate.html#ops
;;; for complete set, these are the ones that seemed most useful.
(def aggregates
  [:valid :missing :distinct :median :mean :variance :stdev :sum :product :min :max])

(defn visualize-toolbox
  []
  [:category "Visualize" {}
   [:block "layer" {}
    ;; For some reason displayed order is inverse
    [:value "encoding" [:block "count_encoding" {}
                        [:field "attribute" "size"]]]
    [:value "encoding" [:block "encoding" {}
                        [:field "attribute" "y"]]]
    [:value "encoding" [:block "encoding" {}
                        [:field "attribute" "x"]]]

    ]
   [:block "encoding"]
   [:block "count_encoding" {} [:field "attribute" "size"]]
   [:block "aggregate_encoding"]
   ])

(def layer-color "#9e2a2b")
(def encoding-color "#407492")

(defn graph-blockdefs
  "Visualization blocks"
  []
  (list 
   {:type "layer"
    :colour layer-color
    :message0 "mark %1"
    :args0 [{:type "field_dropdown"
             :name "mark"
             :options (options marks)}]
    :message1 "encodings %1"
    :args1 [{:type "input_statement"
             :name "encoding"
             #_ :check #_ (str (name kind) "_constraint")}]
    :message2 "data %1"                 ;this is only for show at the moment
    :args2 [{:type "input_value"
             :name "data"
             }] 
    ;; Not really a query builder, but used for text gen dispatch
    :query-builder :query-builder-layer
    }
   {:type "encoding"
    :colour encoding-color
    :previousStatement "encoding"
    :nextStatement "encoding"
    :message0 "attribute %1 field %2 type %3" ;TODO axis, scale...
    :args0 [{:type "field_dropdown"
             :name "attribute"
             :options (options attributes)
             }
            {:type "field_dropdown" 
             :name "field"
             :options field-options
             }
            {:type "field_dropdown"
             :name "type"
             :options (options [:nominal :ordinal :quantitative]) ;TODO derive dynamically from data
             }
            ]}
   {:type "count_encoding"
    :colour encoding-color
    :previousStatement "encoding"
    :nextStatement "encoding"
    :message0 "attribute %1 count" ;TODO axis, scale...
    :args0 [{:type "field_dropdown"
             :name "attribute"
             :options (options attributes)
             }
            ]}
   {:type "aggregate_encoding"
    :colour encoding-color
    :previousStatement "encoding"
    :nextStatement "encoding"
    :message0 "attribute %1 field %2 %3" ;TODO axis, scale...
    :args0 [{:type "field_dropdown"
             :name "attribute"
             :options (options attributes)
             }
            {:type "field_dropdown" 
             :name "field"
             :options field-options
             }
            {:type "field_dropdown" 
             :name "aggregate"
             :options (options aggregates)}]}

   ))

;;; Generates the Vega spec from blocks and data.
;;; Note: see blockdefs.cljc for 

(defmulti vega-spec (fn [block] (:type block)))

;;; Pseudo block (not used, but a way to support multiple layers/marks)
(defmethod vega-spec "graph" [block]
  (let [layers (map vega-spec (:layers block))]
    (case (count layers)
      0 nil
      1 (first layers)
      {:layer layers})))

(defmethod vega-spec "layer" [block]
  (when (get-in block [:children "data"])
    {:mark {:type (get-in block [:children "mark"])
            :tooltip {:content "data"}}
     :encoding (vega-spec (get-in block [:children "encoding"]))}))

(defmethod vega-spec "encoding" [block]
  (assoc (vega-spec (get-in block [:children :next]))
         (get-in block [:children "attribute"])
         {:field (get-in block [:children "field"])
          :type (get-in block [:children "type"])
          :sort "ascending"
          }))

(defmethod vega-spec "count_encoding" [block]
  (assoc (vega-spec (get-in block [:children :next]))
         (get-in block [:children "attribute"])
         {:aggregate "count"}))

(defmethod vega-spec "aggregate_encoding" [block]
  (assoc (vega-spec (get-in block [:children :next]))
         (get-in block [:children "attribute"])
         {:field (get-in block [:children "field"])
          :aggregate (get-in block [:children "aggregate"])}))

;;; Terminates recursion down :next chain
(defmethod vega-spec nil [_block]
  {})

;;; see views/render
(defn- flatten-row [row]
  (u/map-values (fn [v] (cond (map? v)
                              (or (:label/label v) (:db/id v))
                              (sequential? v)
                              (map #(or (:label/label %) (:db/id %)) v)
                              :else
                              v))
                row))

(defn- flatten-data [data]
    (map flatten-row data))

;;; Not working yet
(defn pop-out-button
  [id]
  [:button {:on-click #(let [elt (.getElementById js/document id)
                             content (.-innerHTML elt)
                             new-window (.open js/window)]
                         (.write (.-document new-window) content))}
   "open"])

(defn- generate-vega-spec
  []
  (let [results @(rf/subscribe [:results])
        blocks @(rf/subscribe [:compact-all])
        vega-block (u/some-thing #(= "layer" (:type %)) blocks)
        spec (vega-spec vega-block)]
    (and (not (empty? results))
         (not (empty? spec))
         (assoc spec
                :data {:values (flatten-data results)}))))
 
(defn render-flex
  [spec]
  (when spec
    [:div#graph
     (oz/view-spec [:vega-lite spec])]))

(defn render
  "React component showing the graph"
  []
  (render-flex (generate-vega-spec)))





