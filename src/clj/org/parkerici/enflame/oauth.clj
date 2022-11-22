(ns org.parkerici.enflame.oauth
  (:require [clojure.data.json :as json]
            [environ.core :as env]
            [ring.util.request :as request]
            [ring.util.response :as response]
            [clj-http.client :as client]
            [org.parkerici.enflame.config :as config]
            ))


;;; Set or bind this false to bypass oauth and use local info instead
(def ^:dynamic *oauth?* true)

(defn dev-mode?
  []
  (:dev? config/config))

(defn host
  "Return the host from the request"
  [request]
  (str
   (if (dev-mode?)
     "http://"        
     "https://")
   (get-in request [:headers "host"])))

(defn oauth-redirect
  [req]
  (str (host req) "/authenticated"))

(defn login-page
  [config req]
  (let [config (:source config)
        redirect-uri (oauth-redirect req)
        resp (client/get (str (:candelabra-endpoint config) "/client-oauth-url") {:unexceptional-status #{200 403 404}
                                                                                  :insecure? (:insecure-https config)})
        body (json/read-str (:body resp) :key-fn keyword)]
    (str (:client-oauth-url body) redirect-uri)))



;;; Urls that do not require login. 
(def open-uris #{"/authenticated"
                 "/health"
                 "/enflame.css"
                 "/login"
                 "/login.css"
                 "/favicon.ico"})


(defn wrap-enforce-login
  [handler responder]
  (fn [request]
    (let [candelabra-token (if *oauth?*
                             (get-in request [:cookies "candelabra-token" :value])
                             (env/env :candelabra-token))]
      (cond (or candelabra-token (open-uris (:uri request)))  ; Open (allowed) URI
            (try
              (handler request)
              (catch Exception e
                (if (= (:status (ex-data e)) 401)
                  (response/redirect "/login")
                  (throw e))))
            :else                        ; No id
            (responder request)))))         ; call the responder (which can (eg) return an error response)





(defn wrap-oauth [handler config]
  (-> handler
      (wrap-enforce-login (fn [req]
                            (let [login-pg (login-page config req)]
                              (response/set-cookie
                               (response/redirect login-pg)
                               "original_page"
                               (request/request-url req)
                               {:same-site :lax :path "/"}))))))


;;; Not used
(defn wrap-oauth-off
  "Include as wrapper to disable Oauth."
  [handler]
  (fn [request]
    (binding [*oauth?* false]
      (handler request))))
