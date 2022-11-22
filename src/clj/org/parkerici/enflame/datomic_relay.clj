(ns org.parkerici.enflame.datomic-relay
  (:require [clj-http.client :as client]
            [taoensso.timbre :as log]
            [org.parkerici.multitool.core :as u]
            [clojure.walk :as walk]
            [clojure.data.json :as json]
            [org.parkerici.enflame.config :as config]
            ))

;;; This acts as a client for gaslight, that is, it does more or less what wick does.
;;; 
;;; The format gaslight expects:
;;; https://github.com/CANDELbio/datalog-json-parser/blob/master/test/resources/single-clause-q.json

(defn process-query
  [q]
  (walk/postwalk
   #(if (keyword? %) (str %) %)
   q))

(defn keywordify
  [s]
  (let [[_ ns name] (re-matches #":(.*)/(.*)" s)]
    (if ns
      (keyword ns name)
      s)))

(def default-options
  {"improve" true
   "timeout" 120000})  ;double the default. TODO give user some control

(defn add-access-token
  "Adds the access token to the clj-http request map required when
   accessing Candelabra"
  [req token insecure?]
  (merge req (cond-> {:headers {"Authorization" (str "Token " token)}}
               insecure?
               (assoc :insecure? true))))

(defn authenticated-candelabra-request
  ([req-fun candelabra-token config endpoint req-map]
   (let [{:keys [candelabra-endpoint insecure-https]} (:source config)
         request (-> {:content-type :json}
                     (add-access-token candelabra-token insecure-https)
                     (merge req-map))
         url (str candelabra-endpoint endpoint)]
     (req-fun url request)))
  ([req-fun candelabra-token config endpoint]
   (authenticated-candelabra-request req-fun candelabra-token config endpoint {})))

(defn candelabra-get
  [url candelabra-token config]
  (let [response (authenticated-candelabra-request
                  client/get candelabra-token config
                  url
                  {:as :json})]
    (:body response)))

(defn dbs-raw
  [candelabra-token config]
  (candelabra-get "/list-dbs?listall=true" candelabra-token config))

(defn datasets-raw
  [candelabra-token config]
  (candelabra-get "/list-dbs?listall=true&bydataset=true" candelabra-token config))

(defn dbs
  [candelabra-token config]
  (->> (dbs-raw candelabra-token config)
       (:databases)
       (map :database)
       (sort)))

(defn query
  [db query args candelabra-token config]              
  (let [jsonified-query (json/write-str (merge
                                         {"query" (process-query query)
                                          "args" args}
                                         default-options)
                                        :key-fn str :escape-slash false)
        endpoint (str "/query/" db)
        response (authenticated-candelabra-request
                  client/post
                  candelabra-token
                  config
                  endpoint
                  {:body jsonified-query})
        _ (when-not (= (:status response) 200)
            (throw (ex-info (str "Gaslight exception: " (:reason-phrase response))
                            {:status (:status response)
                             :body (or (u/ignore-errors (json/read-str (:body response)))
                                       (:body response))})))
        query-id (:query-id (json/read-str (:body response) :key-fn keyword))
        query-status-endpoint (str "/query-status/" query-id)]
    ;; TODO this should be more intelligent for better UX
    (loop []
      (let [{:keys [status results-url] :as response}
            (json/read-str (:body (authenticated-candelabra-request client/get candelabra-token config query-status-endpoint))
                           :key-fn keyword)]
        (cond (or (= status "success") (= status "success-cached"))
              (let [results (json/read-str (:body (client/get results-url)) :key-fn keywordify)]
                (log/infof "Results: %s rows" (count results))
                results)
              (or (= status "waiting")
                  (= status "processing"))
              (recur)
              :else
              (throw (ex-info "Query failed"
                              {:response response})))
        ))))


;;; Called from download.
;;; Duplicates the same query client does, for download purposes
;;; (see the :get-idents handler in datomic.cljs)
;;; Awkwardly memoized
(def ^:dynamic *token* nil)

(u/defn-memoized idents-1
  [db]
  (into {} (query db '{:find (?x ?y), :where ([?x :db/ident ?y])} [] *token* (config/config))))

(defn idents
  [db token config]
  (with-bindings {(var *token*) token}
    (idents-1 db)))

(defn attribute-census
  [db attributes]
  ;; Would be good to combine into a single query, but that doesn't work, Datomic barfs
  (zipmap attributes
             (map (fn [attribute]
                    (u/ignore-errors
                     (ffirst (query db `{:find [(count ?x)] :where [[?x ~attribute _]]} []))))
                  attributes)))


;;; Not actually called now, client does the query.
#_
(defn db-version [db]
  (u/ignore-errors                      ;will fail on early dbs without version attribute TODO make this more specific
   (ffirst (query db '{:find [?version] :where [[_ :candel.schema/version ?version]]} []))))

