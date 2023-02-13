(ns org.parkerici.enflame.server
  (:require [org.parkerici.enflame.datomic-relay :as datomic]
            [org.parkerici.enflame.datomic-client :as datomic-client]
            [org.parkerici.enflame.download :as download]
            [org.parkerici.enflame.embed-server :as embed]
            [org.parkerici.enflame.admin :as admin]
            [org.parkerici.enflame.schema :as schema]
            [org.parkerici.enflame.config :as config]
            [org.parkerici.enflame.oauth :as oauth]
            [org.parkerici.enflame.gcs :as gcs]
            [org.parkerici.enflame.library.item :as item]
            [org.parkerici.enflame.library.view :as library-view]
            [org.parkerici.multitool.core :as u]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [ring.logger :as logger]
            [clj-http.client :as client]
            [ring.adapter.jetty :as jetty]
            [compojure.core :refer [context routes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as response]
            [ring.middleware.defaults :as middleware]
            [clojure.data.json :as json]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.session.memory :as ring-memory]))

(defn wrap-exception-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        {:status 500 :headers {} :body {:error (ex-message e) :data (ex-data e)}})
      (catch Throwable e
        {:status 500 :headers {} :body {:error (print-str e)}}))))

(defn handle-query
  [req config]
  (let [{:keys [query args limit db]} (:params req)
        _query (read-string query)
        _args (if (u/nullish? args) [] (read-string args))
        _limit (if (u/nullish? limit) nil (Integer. limit))
        candelabra-token (get-in req [:cookies "candelabra-token" :value])
        results #_ (datomic/query db _query _args candelabra-token config)
        (datomic-client/query db _query _args)
        clipped (if _limit (take _limit results) results)]
    (response/response
     {:count (count results) :clipped (count clipped) :results clipped})))

(defn handle-download
  [req config]
  (let [{:keys [query db]} (:params req)
        candelabra-token (get-in req [:cookies "candelabra-token" :value])
        query (read-string query)
        results (datomic/query db query [] candelabra-token config)]
    (log/infof "Download db:%s q:%s" db query)
    (response/response 
     (download/export-results db results query candelabra-token config))))

(defn handle-save
  [req]
  (let [item (get-in req [:params :item])
        response (gcs/upload "EnflameItem" item item/big-keys)]
    (response/response                  ;response^4
     {:response response})))

(defn handle-get
  [key]
  (let [item (item/localize-item (gcs/get-item "EnflameItem" (Long. key)))]
    ;; TODO handle not found
    (response/response item)))


;;; TODO move to more candelabra-specific file
(defn handle-candelabra-login
  [req config]
  (let [config (:source config)
        code (get-in req [:params :code])
        candelabra-req {:unexceptional-status #{200 403 404}
                        :body (json/json-str {:redirect-url (oauth/oauth-redirect req)
                                              :user-auth-code code})
                        :insecure? (:insecure-https config)}
        resp (client/post (str (:candelabra-endpoint config) "/login") candelabra-req)
        {:keys [status body]} resp]
    (cond
      (= status 403)
      (throw (ex-info
              "Unknown user or invalid code"
              {:candelabra/access {:message "Unknown user or invalid code"}}))

      :else
      (let [user-creds (-> body json/read-str (get "token"))
            original-page (get-in req [:cookies "original_page" :value])
            respon (response/set-cookie
                    (response/redirect (if (empty? original-page) "/" original-page))
                    "candelabra-token" user-creds)]
        respon))))

;;; Old CANDEL
#_
(defn handle-databases
  [req config]
  (let [candelabra-token (get-in req [:cookies "candelabra-token" :value])]
    (datomic/dbs candelabra-token config)))

;;; Open CANDSL
(defn handle-databases
  [req config]
  (datomic-client/dbs))

(defn app-routes
  [config]
  (routes
   (GET "/" [] (response/redirect "index.html"))
   (GET "/health" [] (-> (response/response "ok")
                         (response/content-type "text/plain")))
   (GET "/login" [] ; Users would be redirected here because their token is invalid, so clear it and redirect
     (let [resp (response/set-cookie
                 (response/redirect "/") ; For some reason this redirect fails due to cross-site origin, so the page must be reloaded manually
                 "candelabra-token" "invalid"
                 {:max-age -1 :same-site :lax :path "/"})]
       resp))
   (GET "/authenticated" req (handle-candelabra-login req config))
   (GET "/download" req (handle-download req config))
   (GET "/embed" [db query]
        (embed/embed-iframe-contents db query))
   (GET "/admin" req (admin/view req))
   (GET "/library" [] (library-view/view))
   (context "/api" []
     (GET "/config" req (response/response (config/config)))
     (GET "/databases" req              ;TODO candel specific. Fold into schema
       (response/response (handle-databases req config)))
     (GET "/schema" [version]     
       (response/response (config/read-schema version)))
     (GET "/query" req (handle-query req config))
     (context "/library" []
       (GET "/get" [key]
         (handle-get key))
       (POST "/save" request
         (handle-save request))))
  ;; TODO could provide the schema to client?
   (route/not-found "Not Found")))

;;; Ensure API and site pages use the same store, so authentication works for API.
(def common-store (ring-memory/memory-store))

(def site-defaults
  (-> middleware/site-defaults
      (assoc-in [:security :anti-forgery] false)          ;interfering with save?
      #_ (assoc-in [:session :cookie-attrs :same-site] :lax) ;for oauth
      (assoc-in [:session :store] common-store)
      (assoc-in [:static :resources] nil)))                 ;this needs to go after oauth


(def no-log #{"/health"})

(defn app
  [config]
  (routes
   (-> (app-routes config)
       (logger/wrap-with-logger          ;hook Ring logger to Timbre
        {:request-keys [:request-method :uri :params :remote-addr]
         :transform-fn identity
         :log-fn (fn [{:keys [level throwable message]}]
                   (when-not (some #(= % (:uri message)) no-log)
                     (log/log level throwable message)))})
       (wrap-resource "public" {:allow-symlinks? true})
       #_ (oauth/wrap-oauth config)
       (middleware/wrap-defaults site-defaults)
       wrap-exception-handling
       wrap-restful-format
       wrap-gzip
       wrap-cookies)))

;;; Server

(defonce server (atom nil))

(defn stop
  []
  (when @server
    (.stop @server)))

(defn start
  ([] (start (app (config/config))))
  ([handler]
   (stop)
   (reset! server (jetty/run-jetty handler {:port (config/config :port) :join? false}))))



