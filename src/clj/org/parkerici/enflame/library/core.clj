(ns org.parkerici.enflame.library.core
  (:require [org.parkerici.enflame.gcs :as gcs]
            [org.parkerici.enflame.config :as config]
            )
  )

;;; GCS implementation

(def big-keys #{::blockdef ::image})

(defn upload
  [item]
  (case (config/config [:library :type])
    :gcs (gcs/upload "EnflameItem" item big-keys)))

(defn get-item
  [key]
  (case (config/config [:library :type])
    :gcs (gcs/get-item "EnflameItem" (Long. key))))
