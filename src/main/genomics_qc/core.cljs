(ns genomics-qc.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdomc]
            [ag-grid-community :refer [ModuleRegistry AllCommunityModule]]
            [ag-grid-react :refer [AgGridReact]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defonce db (r/atom {}))

(def app-version "v0.1.0")

(defn get-selected-rows
  "Given an event e, return the data from the selected rows."
  [e]
  (let [selected-nodes (-> e
                           .-api
                           .getSelectedNodes)
        get-node-data #(js->clj (.-data %) :keywordize-keys true)]
    (map get-node-data selected-nodes)))


(defn load-sequencing-runs
  "Pull list of sequencing runs from the server and add it to the db."
  []
  (go
    (let [response (<! (http/get "data/sequencing-runs.json" {:with-credentials? false}))]
      (swap! db assoc-in [:sequencing-runs] (:body response)))))


(defn load-library-qc
  "Given a run-id, pull the library QC data for that run from the server.
   If the status code on the response is 200 (OK) then add it to the db."
  [run-id]
  (go
    (let [response (<! (http/get (str "data/library-qc/" run-id "-library-qc.json") {:with-credentials? false}))]
      (when (= 200 (:status response))
        (swap! db assoc-in [:library-qc run-id] (:body response))))))


(defn sequencing-run-selected
  "Given an event e, get the ID of the selected run. Use that ID
   to pull the library-qc data from the server, and update the 
   :selected-sequencing-run-id in the db"
  [e]
  (when (not= "rowDataChanged" (.-source e))
    (let [selected-rows (get-selected-rows e)
          selected-row (first selected-rows)
          currently-selected-run-id (:sequencing_run_id selected-row)]
      (load-library-qc currently-selected-run-id)
      (swap! db assoc-in [:selected-sequencing-run-id] currently-selected-run-id))))


(defn header
  "Header component."
  []
  [:header {:style {:display "grid"
                    :grid-template-columns "repeat(2, 1fr)"
                    :align-items "center"
                    :height "48px"}}
   [:div {:style {:display "grid"
                  :grid-template-columns "repeat(2, 1fr)"
                  :align-items "center"}}
    [:h1 {:style {:font-family "Arial" :color "#004a87" :margin "0px"}} "Genomics QC"] [:p {:style {:font-family "Arial" :color "grey" :justify-self "start"}} app-version]]
   [:div {:style {:display "grid" :align-self "center" :justify-self "end"}}
    [:img {:src "images/logo.svg" :height "48px"}]]])

;; AG-Grid Components
(def ag-grid-react (r/adapt-react-class AgGridReact))

(def sequencing-runs-table-column-defs 
  [{:field "sequencing_run_id"
    :headerName "Sequencing Run ID"
    :minWidth 200
    :resizable true
    :filter "agTextColumnFilter"
    :sortable true
    :sort "desc"
    :floatingFilter true}])

(defn sequencing-runs-table
  "Sequencing runs table component."
  []
  (let [sequencing-runs (:sequencing-runs @db)
        column-defs sequencing-runs-table-column-defs
        row-data sequencing-runs]
    [:div {:class "ag-theme-balham"
           :style {}}
     [ag-grid-react
      {:columnDefs column-defs
       :rowData row-data
       :getRowId (fn [params]
                   (let [^js data (.-data params)]
                     (.-sequencing_run_id data)))
       :theme "legacy"
       :pagination false
       :rowSelection {:mode "singleRow"
                      :checkboxes true}
       :enableCellTextSelection true
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged sequencing-run-selected
        }]]))

(def library-sequence-qc-column-defs
  [{:field "library_id" 
    :headerName "Library ID" 
    :filter "agTextColumnFilter" 
    :pinned "left"}
   
   {:field "project_id" 
    :headerName "Project ID" 
    :filter "agTextColumnFilter"}
   
   {:field "inferred_species_name" 
    :headerName "Inferred Species" 
    :filter "agTextColumnFilter"}
   
   {:field "inferred_species_genome_size_mb"
    :headerName "Genome Size (Mb)"
    :maxWidth 140 
    :filter "agNumberColumnFilter" 
    :type "numericColumn"}
   
   {:field "total_bases" 
    :headerName "Total Bases (Mb)" 
    :maxWidth 140
    :filter "agNumberColumnFilter" 
    :type "numericColumn"}
   
   {:field "percent_bases_above_q30"
   :headerName "Bases Above Q30 (%)"  
    :maxWidth 160 
    :filter "agNumberColumnFilter" 
    :type "numericColumn"}
   
   {:field "estimated_depth" 
    :headerName "Est. Depth Coverage" 
    :maxWidth 172 
    :filter "agNumberColumnFilter" 
    :type "numericColumn" }])

(defn library-sequence-qc-table
  "Library sequence QC table component."
  []
  (let [selected-sequencing-run-id (:selected-sequencing-run-id @db)
        selected-sequencing-run-library-qc (get-in @db [:library-qc selected-sequencing-run-id])
        row-data (->> selected-sequencing-run-library-qc
                      (map (fn [x] (update x :inferred_species_genome_size_mb #(when % (.toFixed % 3)))))
                      (map (fn [x] (update x :total_bases #(when % (.toFixed (/ % 1000000) 3)))))
                      (map (fn [x] (update x :percent_bases_above_q30 #(when % (.toFixed % 2))))))]
    [:div {:class "ag-theme-balham"
           :style {}}
     [ag-grid-react
      {:defaultColDef {:sortable  true
                       :resizable true
                       :floatingFilter true
                       :minWidth  80
                       :maxWidth 200}
       :columnDefs library-sequence-qc-column-defs
       :rowData row-data
       :getRowId (fn [params]
                   (let [^js data (.-data params)]
                     (.-library_id data)))
       :pagination false
       :theme      "legacy"
       :enableCellTextSelection true
       :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
       :onSelectionChanged #()}
      ]]))

(defn app
  "Complete app component. Consists of a header and two tables."
  []
  [:div {:style {:display "grid"
                 :grid-template-columns "1fr"
                 :grid-gap "4px 4px"
                 :height "100%"}}
   [header]
   [:div {:style {:display "grid"
                  :grid-template-columns "1fr 4fr"
                  :grid-template-rows "1fr"
                  :gap "4px"
                  :height "800px"}}
    [:div {:style {:display "grid"
                   :grid-column "1"
                   :grid-row "1"}}
     [sequencing-runs-table]]
    [:div {:style {:display "grid"
                   :grid-column "2"
                   :grid-row "1"
                   :gap "4px"}}
     [library-sequence-qc-table]]]])


;;

(defonce root
  (when (exists? js/document)
    (rdomc/create-root (.getElementById js/document "app"))))

(defn render
  "Render the app into the root app div."
  []
  (rdomc/render root [app]))

(defn ^:dev/after-load re-render
  "Hot-reload hook called by shadow-cljs after code changes.
  Re-renders from root so that new component definitions take effect.
  State is preserved because it lives in `defonce` app state db atom."
  []
  (render))

(defn ^:export init
  "Exported entry point called by shadow-cljs on page load."
  []
  (.registerModules ModuleRegistry #js [AllCommunityModule])
  (load-sequencing-runs)
  (render))