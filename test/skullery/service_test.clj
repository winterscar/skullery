(ns skullery.service-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia :as lacinia]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.test :refer :all]
            [skullery.db :as db]
            [skullery.schema :as schema]
            [skullery.utils :refer [simplify]]
            [next.jdbc :as jdbc]
            [honey.sql :as sql]
            [next.jdbc.result-set :as rs]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn db-sys
  []
  (merge (component/system-map)
         (db/new-database 1 "" false)))

(defn db+schema-sys
  []
  (merge (component/system-map)
         (schema/new-schema)
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
    `(let [~sys  (component/start-system ~system-map)
           ~data-sym (when ~data-file (db/exec-file! (->conn ~sys) ~data-file))]
       (try
         ~@body
         (finally (component/stop ~sys))))))

(defn ^:private q
  "Extracts the compiled schema and executes a query."
  [system query variables]
  (-> system
      (get-in [:schema :schema])
      (lacinia/execute query variables nil)
      simplify))

(def expected-tables
  '#{RECIPEINGREDIENTS RECIPES STEPINGREDIENTS
     INGREDIENTS DATABASEVERSION STEPS
     EQUIPMENT CONVERSIONS RECIPEEQUIPMENT})

(deftest can-open-database-and-apply-migrations
  (using
   [sys db-sys]
   (let [_tables (jdbc/execute! (->conn sys) ["show tables"] {:builder-fn rs/as-unqualified-kebab-maps})
         tables (reduce #(conj %1 (:table-name %2)) #{} _tables)]
     (is tables expected-tables))))

(deftest can-get-recipe-by-id
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{recipe(id: 1) { id }}" nil)
         expected {:data {:recipe {:id 1}}}]
     (is (= res expected)))))

(deftest can-get-all-recipes
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{recipes { id }}" nil)
         expected {:data {:recipes [{:id 1} {:id 2} {:id 3} {:id 4}]}}]
     (is (= res expected)))))

(deftest can-map-edn-to-sql
  (let [single-table-edn       '{recipes {columns [foo bar] values [[1 2] [3 4]]}}
        single-table-honey-sql '{insert-into recipes columns [foo bar] values [[1 2] [3 4]]}
        single-table-sql       (first (sql/format single-table-honey-sql {:inline true}))
        ;; ----
        multi-table-edn       '{recipes {columns [foo bar] values [[1 2] [3 4]]}
                                ingredients {columns [a b c d e f] values [["a" "b" "c" "d" "e" "f"]]}}
        multi-table-sql        "INSERT INTO recipes (foo, bar) VALUES (1, 2), (3, 4); INSERT INTO ingredients (a, b, c, d, e, f) VALUES ('a', 'b', 'c', 'd', 'e', 'f')"]
    (testing "Can map single table to SQL"
      (is (= single-table-sql (db/edn->sql single-table-edn))))
    (testing "Can map multi-table objects to SQL" 
      (is (= multi-table-sql  (db/edn->sql multi-table-edn))))))