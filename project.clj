(defproject addressbook "0.1.0-SNAPSHOT"
  :description "Address book test"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/java.jdbc "0.0.6"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [ring/ring-jetty-adapter "1.1.6"]
                 [ring/ring-json "0.2.0"]
                 [postgresql "9.1-901.jdbc4"]]
  :uberjar-name "addressbook-standalone.jar"
  :min-lein-version "2.0.0"
  :main addressbook.core
  )
