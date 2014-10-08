(ns addressbook.core
  (:use [clojure.java.jdbc])
  (:require [ring.adapter.jetty :as jetty])
  (:import (java.net URI)))

(use '[clojure.string :only [join, trim, split]])

;;; DB configuration

(def db
  (if (System/getenv "HEROKU_POSTGRESQL_ROSE_URL")
    (let [url (URI. (System/getenv "HEROKU_POSTGRESQL_ROSE_URL"))
          host (.getHost url)
          port (if (pos? (.getPort url)) (.getPort url) 5432)
          path (.getPath url)]
        (merge
          {:subname (str "//" host ":" port path)
           :subprotocol "postgresql"
           :classname "org.postgresql.Driver"}
          (when-let [user-info (.getUserInfo url)]
            {:user (first (split user-info #":"))
             :password (second (split user-info #":"))})))

      {:classname "org.sqlite.JDBC"
       :subprotocol "sqlite"
       :subname "db/database.db"
       }))

(println db)

(defn create-db []
  (try (with-connection db
         (create-table :addresses
           [:name :text]
           [:phone :text]
           ))
    (catch Exception e (println e))))

(create-db)

;;;

(def build-command
  (fn [usage, validate, execute]
    (fn [& args]
      (if (validate args)
        (execute args)
        (println usage)
      ))))

(def commands {
    "add"
        {:usage "<user name> <phone number without spaces>"
         :validate #(>= (count %) 2)
         :build-params #(let [params {"name" (join " " (drop-last %)) "phone" (last %)}] params)
         :execute
          (fn [params]
            (do
              (println (str "adding \"" (params "name") "\" -> \"" (params "phone") "\""))
              (with-connection db
                (insert-records :addresses params))
              params))}
    "delete"
        {:usage "<user name>"
         :validate #(>= (count %) 1)
         :build-params (fn [args] {"name" (join " " args)})
         :execute
           (fn [params]
            (let [username (params "name")]
              (println (str "deleting \"" username "\""))
              (with-connection db
                (delete-rows :addresses ["name = ? " username])))
             {:status "ok"})}
    "find"
        {:usage "<user name>"
         :validate #(>= (count %) 1)
         :build-params (fn [args] {"name" (join " " args)})
         :execute
           (fn [params]
             (let [username (params "name")]
               (def results (with-connection db
                 (with-query-results rs ["select * from addresses where name = ?" username] (doall rs))))
               (println (if (empty? results) "Nothing was found" results)))
               (if (empty? results) {} results))}
    "print"
        {:validate #(empty? %)
         :build-params (fn [args] {})
         :execute
           (fn [args]
             (do
               (def results (with-connection db
                 (with-query-results rs ["select * from addresses"] (doall rs))))
               (println results)
               results)
             )}
    "help"
        {:execute
          (fn [args]
            (println "Usage:")
            (doseq [[name command] commands]
                (println " " name (or (command :usage) "")))) }
})

(defn do-command [command-name args & params]
  (let
    [command-name (if (contains? commands command-name) command-name "help")
     command (commands command-name)]
  (if params
    ((command :execute) (first params))
    (if (or (-> command :validate nil?) ((command :validate) args))
      ((command :execute) ((command :build-params) args))
      (println "Usage:" command-name (or (command :usage) ""))))))

(defn read-eval-print-loop []
  (print "> ")
  (flush)
  (let [line (read-line)]
    (if (not (nil? line))
      (let [tokens (split line #"\s")
            command (first tokens)
            args (rest tokens)]
        (do-command command args)
        (recur)))))

;;; Web App

(use 'ring.middleware.json
  'ring.util.response)

(defn handler [request]
  (if (= (:uri request) "/")
    (response {:status "Hello, world!"})
    (do
      (println (join " " [(request :request-method) (request :uri) (request :query)]))
      (println (request :params))
      (response (do-command (subs (request :uri) 1) [] (request :params))))))

(def app
  (wrap-json-response (wrap-json-params handler)))

;;;

(defn -main [& args]
  (if (< (count args) 1)
    (println "Usage: lein run [console|port]")

    (if (= (first args) "console")
      (read-eval-print-loop)
      (let [port (Integer. (first args))]
        (jetty/run-jetty app {:port port :join? false})))))
