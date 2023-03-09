(defproject enflame "0.0.29-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-http "3.12.3"
                  :exclusions [commons-codec]]
                 [cheshire "5.11.0"]

                 [com.datomic/client-cloud "1.0.122"]

                 ;; Ring and its large family
                 [org.eclipse.jetty/jetty-client "9.4.48.v20220622"] ;has to match ring version of jetty
                 [org.eclipse.jetty/jetty-server "9.4.48.v20220622"]
                 [org.eclipse.jetty/jetty-http "9.4.48.v20220622"]
                 [org.eclipse.jetty/jetty-util "9.4.48.v20220622"]

                 [ring "1.9.6"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [ring/ring-defaults "0.3.4"]
                 [ring-logger "1.1.1"]
                 [ring-oauth2 "0.2.0" :exclusions [org.apache.httpcomponents/httpcore]]
                 [org.slf4j/slf4j-simple "1.7.26"]                   ;required to turn off warning
                 [io.aviso/pretty "1.3"]
                 [com.taoensso/timbre "4.10.0"
                  :exclusions [io.aviso/pretty]]
                 [org.clojure/data.csv "0.1.4"]
                 [compojure "1.6.1" :exclusions [ring.core ring.codec]]
                 [ring-middleware-format "0.7.5" :exclusions [javax.xml.bind/jaxb-api]]
                 [bk/ring-gzip "0.3.0"]
                 [trptcolin/versioneer "0.2.0"]

                 ;; Note: the gcs stuff is not going to used post PICI and could be removed. So annoying that it is hard to have different configs.
                 [com.google.cloud/google-cloud-datastore "1.105.7"
                  ;; TODO have Cognitect or someone review this
                  :exclusions [com.google.errorprone/error_prone_annotations
                               com.google.oauth-client/google-oauth-client
                               org.apache.httpcomponents/httpclient 
                               com.google.guava/guava
                               com.fasterxml.jackson.core/jackson-core]]

                 [com.cognitect.aws/api "0.8.652"]
                 [com.cognitect.aws/endpoints "1.1.12.415"]
                 [com.cognitect.aws/dynamodb  "825.2.1262.0"]

                 [environ "1.1.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.parkerici/multitool "0.0.26"]
                 [com.cemerick/url "0.1.1"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/clojurescript "1.10.520"]

                 [aristotle/aristotle "0.1.0"
                  :exclusions [org.apache.jena/apache-jena-libs]] ;asking for trouble
                 [org.apache.jena/apache-jena-libs "3.16.0" :extension "pom"]
                 [metasoarous/oz "1.6.0-alpha6" ; warning: later versions seem to have broken dependencies
                  :exclusions [cljsjs/vega      ; we insert a later version of Vega to fix some bugs
                               cljsjs/vega-lite
                               cljsjs/vega-embed
                               cljsjs/vega-tooltip
                               com.taoensso/encore]]
                  
                 [cljsjs/vega "5.20.2-0"]
                 [cljsjs/vega-lite "5.1.1-0"]
                 [cljsjs/vega-embed "6.19.0-0"]
                 [cljsjs/vega-tooltip "0.27.0-0"]

                 [org.parkerici/blockoid "0.3.6"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.9"
                  :exclusions [org.clojure/clojurescript]]
                 [cljsjs/ag-grid-react "25.0.1-2"]
                 [cljsjs/ag-grid-enterprise "25.3.0-1"]
                 [inflections "0.13.2"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [cljs-ajax "0.8.0"]]
  :repositories [["github" {:url "https://maven.pkg.github.com/ParkerICI/mvn-packages"
                            :sign-releases false
                            :username :env/github_user
                            :password :env/github_password}]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-set-version "0.4.1"]]

  :min-lein-version "2.5.3"
  :main ^:skip-aot org.parkerici.enflame.core

  :aliases {"launch" ["do"
                      "clean"
                      ["cljsbuild" "once" "prod"]
                      ["run" "deploy/launch-config.edn"]]}

  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj" "test/cljs" "test/cljc"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :figwheel {:server-port 3457
             :css-dirs ["resources/public/css"]}

  :uberjar-name "enflame-standalone.jar"
  :resource-paths ["resources"]

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.9.10"]
                   [mock-clj "0.2.1"]
                   [figwheel-sidecar "0.5.19"
                    :exclusions [org.clojure/clojurescript]]
                   [day8.re-frame/re-frame-10x "0.4.2"
                    :exclusions [cljsjs/create-react-class
                                 org.clojure/clojurescript]]]
    :plugins      [[lein-figwheel "0.5.19"]]}
   :prod {}
   :uberjar
   {
    :prep-tasks ["compile" ["cljsbuild" "once" "prod"]]
    :omit-source true
    :aot :all
    :main ^:skip-aot org.parkerici.enflame.core}}


  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs" "src/cljc"]
     :figwheel     {:on-jsload "org.parkerici.enflame.core/mount-root"}
     :compiler     {:main                 org.parkerici.enflame.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload
                                           day8.re-frame-10x.preload]
                    :optimizations        :none
                    :infer-externs        true
                    :external-config      {:devtools/config {:features-to-install :all}}
                    :closure-defines      {"re_frame.trace.trace_enabled_QMARK_" true
                                           goog.DEBUG true}}}
    
    {:id           "prod"
     :source-paths ["src/cljs" "src/cljc"]
     :compiler     {:main            org.parkerici.enflame.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :output-dir      "resources/public/js/compiled/outprod"
                    :optimizations   :simple
                    :infer-externs  true
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}]})
