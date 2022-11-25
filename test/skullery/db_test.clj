(ns skullery.db-test
  (:require [clojure.test :refer :all]
            [skullery.test-utils :refer :all]
            [honey.sql :as sql] 
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [skullery.db :as db]))

(def expected-tables
  '#{RECIPEINGREDIENTS RECIPES STEPINGREDIENTS
     INGREDIENTS DATABASEVERSION STEPS
     EQUIPMENT CONVERSIONS RECIPEEQUIPMENT})

(deftest migrate!
  (using
   [sys (db-sys)]
   (let [_tables (jdbc/execute! (->conn sys) ["show tables"] {:builder-fn rs/as-unqualified-kebab-maps})
         tables (reduce #(conj %1 (:table-name %2)) #{} _tables)]
     (is tables expected-tables))))

(deftest edn->sql
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