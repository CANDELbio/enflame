;;; Copying from Rawsugar, making more general in the process

(ns org.parkerici.enflame.view.aggrid
  (:require [reagent.core :as reagent]
            [cljsjs.ag-grid-react]
            [cljsjs.ag-grid-enterprise]
            [org.parkerici.multitool.core :as u]
            [reagent.dom.server]
            )
  (:require-macros
   [org.parkerici.enflame.macros :refer (ag-grid-license)]))

(def ag-adapter (reagent/adapt-react-class (.-AgGridReact js/agGridReact)))

(def license-key (ag-grid-license))

(defn init
  []
  (when license-key
    (prn :aggrid-set-license-key (str "..." (subs license-key (- (count license-key) 10))))
    (try
      (.setLicenseKey (.-LicenseManager js/agGrid) license-key)
      (catch :default e
        (prn :error e)))))

;;; Keep pointers to API objects for various purposes. This maps a keyword id (:sheet, :files) to
;;; the appropriate API object. Note: this doesn't survive a figwheel reload. Maybe store in the
;;; re-frame db instead?
(def ag-apis (atom {}))

;;; What I really want is CLOS dispatch and call-next-method
(defmulti ag-col-def (fn [table-id col-id] [table-id col-id]))

;;; Can't get defaulting I want from defmulti, so this is weird
(defmethod ag-col-def :default [_table-id _col-id]
  {})

(defn- default-col-def
  [col-id]
  {:headerName (name col-id)
   :field (name col-id)
   })

;;; Col-def can be keyword or an ag-grid map, :field is interpreted specially
(defn- expand-ag-col-def
  [table-id col-def]
  (if (keyword col-def)
    (u/merge-recursive
     (default-col-def col-def)
     (ag-col-def table-id col-def))
    (u/merge-recursive
     (default-col-def (:field col-def))
     (dissoc col-def :field))))

(defn ag-table
  "table-id: a keyword to identify this table
  column-defs: a seq of column defs, which can be:
     a simple keyword (see default-col-def and ag-col-def) OR
     an ag-grid column def (map with :field being the 'key') with some additional properties:
  data: A seq of maps, typically from a rf subscripe call.
  ag-grid-options: a map of values passed to ag-grid
  "
  [table-id column-defs data ag-grid-options]
  (let [column-defs (mapv (partial expand-ag-col-def table-id) column-defs)]
    [:div.ag-container {:style {:height "100%"}}
     [:div {:class "ag-theme-alpine"  ;TODO theme should be argument
            :style {:height "100%"}} 
      (let [grid-options
            (u/merge-recursive                     ;merges grid options, optional detail grid options, and user supplied options
             {:defaultColDef {:sortable true
                              :filter "agTextColumnFilter"
                              :resizable true
                              }
              :onGridReady (fn [params]
                             (swap! ag-apis assoc table-id (.-api params)))
        

;;; Off for now, need to unify with "Showing n of n"
;                :pagination true
;                :paginationAutoPageSize true

              :columnDefs column-defs
              :suppressFieldDotNotation true
              :rowData data
              :sideBar  {:hiddenByDefault false ; visible but closed
                         :toolPanels [{:id "columns"
                                       :labelDefault "Columns"
                                       :labelKey "columns"
                                       :iconKey "columns"
                                       :toolPanel "agColumnsToolPanel"
                                       ;; Turning these off for now, might want to revisit in the future. Possibly incompatible with the master/detail feature?
                                       :toolPanelParams {:suppressRowGroups true
                                                         :suppressValues true
                                                         :suppressPivots true 
                                                         :suppressPivotMode true}
                                       }
                                      {:id "filters"
                                       :labelDefault "Filters"
                                       :labelKey "filters"
                                       :iconKey "filter"
                                       :toolPanel "agFiltersToolPanel"
                                       }]
                         }
              :animateRows true
              :statusBar {:statusPanels [{:statusPanel "agTotalAndFilteredRowCountComponent"
                                          :align "left"}]}
              }
             ag-grid-options)]
        #_ (clojure.pprint/pprint grid-options)
        ;; debug tool, for reporting config to ag-grid.com
        ;;         (print (.stringify js/JSON (clj->js grid-options)))
        [ag-adapter grid-options])
      ]]))
