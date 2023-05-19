(ns org.parkerici.enflame.library.core
  (:require [org.parkerici.enflame.gcs :as gcs]
            [org.parkerici.enflame.library.dynamodb :as dyna]
            [org.parkerici.enflame.config :as config]
            )
  )

;;; GCS implementation

(def big-keys #{::blockdef ::image})

(defn upload
  [item]
  (case (config/config :library :type)
    :gcs (gcs/upload "EnflameItem" item big-keys)
    :dynamodb (dyna/upload item)
    ))

(defn get-item
  [key]
  (case (config/config :library :type)
    :gcs (gcs/get-item "EnflameItem" (Long. key))
    :dynamodb (dyna/get-item key)
    ))

(defn list-items
  []
  (case (config/config :library :type)
    :gcs (gcs/list-items "EnflameItem")
    :dynamodb (dyna/list-items)
    )  
  )
