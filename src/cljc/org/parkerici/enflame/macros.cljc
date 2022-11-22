(ns org.parkerici.enflame.macros)


;;; TODO These are really for cljs only and probably should be in different file

(defmacro ignore-errors "Execute `body`; if an exception occurs return `nil`. Note: strongly deprecated for production code."
  [& body]
  `(try (do ~@body)
        (catch :default e# nil)))

(defmacro ignore-report "Execute `body`, if an exception occurs, print a message and continue"
  [& body]
  `(try (do ~@body)
        (catch :default e# (str "Ignored error: " (.getMessage e#)))))
