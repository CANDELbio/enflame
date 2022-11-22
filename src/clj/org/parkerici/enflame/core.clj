(ns org.parkerici.enflame.core
  (:gen-class)
  (:require [org.parkerici.enflame.server :as server]
            [org.parkerici.enflame.config :as config]
            ))

(defn open-url
  [url]
  (when (java.awt.Desktop/isDesktopSupported)
    (.browse (java.awt.Desktop/getDesktop)
             (java.net.URI/create url))))

(defn -main
  [config-path & args]
  (config/load-config config-path)
  (server/start)
  (when (config/config :dev?)
    (open-url (format "http://localhost:%s/index.html" (config/config :port)))))

