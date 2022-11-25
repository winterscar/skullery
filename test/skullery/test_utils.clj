(ns skullery.test-utils
  "Utility functions and systems required by the unit and integration
   tests."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [com.stuartsierra.component :as component]
   [com.walmartlabs.lacinia :as lacinia]
   [io.pedestal.test :refer :all]
   [skullery.db :as db]
   [skullery.schema :as schema]
   [skullery.search :as search]
   [skullery.utils :refer [simplify]]))

(defn db-sys
  []
  (merge (component/system-map)
         (db/new-database 1 "" false)))

(defn db+schema-sys
  []
  (merge (component/system-map)
         (schema/new-schema)
         (db/new-database 1 "" false)))

(defn db+search-sys
  []
  (merge (component/system-map)
         (search/new-search)
         (db/new-database 1 "" false)))

(defmacro ->conn "Extracts the database connection from a system"
  [sys]
  `(-> ~sys :database :conn))

(defmacro using
  "Let-like binding that initiates a component/system
   and optionally a data-file to apply to the started database.
   The started system is bound to sym."
  [[sys system-map data data-file] & body]
  (let [data-sym (if data data (gensym))]
    `(try
       (let [~sys  (component/start-system ~system-map)
             ~data-sym (when ~data-file (db/exec-file! (->conn ~sys) ~data-file))]
         ~@body
         (component/stop-system ~sys))
       (catch Exception e#
         (if (component/ex-component? e#)
           (let [err-sys# (-> e# ex-data :system)]
             (println "Error during system start or stop: ")
             (component/stop-system err-sys#)
             (throw (.getCause e#)))
           (throw e#))))))

(defn q
  "Extracts the compiled schema and executes a query."
  [system query variables]
  (-> system
      (get-in [:schema :schema])
      (lacinia/execute query variables nil)
      simplify))

(defn test-data->map
  "Takes a test data file and a table name defined in that file,
   and returns a map of that data."
  [file table]
  (let [ing (-> file
                io/resource
                slurp
                edn/read-string
                (get table))
        keys (repeat (map keyword (get ing 'columns)))
        vals (get ing 'values)]
    (map #(zipmap %1 %2) keys vals)))
