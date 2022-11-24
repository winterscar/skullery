(ns skullery.service-test
  (:require [clojure.core.async :refer [>! go]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia :as lacinia]
            [honey.sql :as sql]
            [io.pedestal.test :refer :all]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [skullery.db :as db]
            [skullery.schema :as schema]
            [skullery.search :as search]
            [skullery.utils :refer [simplify]]
            [skullery.utils :as utils]))

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

(defn ^:private q
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

(def expected-tables
  '#{RECIPEINGREDIENTS RECIPES STEPINGREDIENTS
     INGREDIENTS DATABASEVERSION STEPS
     EQUIPMENT CONVERSIONS RECIPEEQUIPMENT})

(deftest can-open-database-and-apply-migrations
  (using
   [sys (db-sys)]
   (let [_tables (jdbc/execute! (->conn sys) ["show tables"] {:builder-fn rs/as-unqualified-kebab-maps})
         tables (reduce #(conj %1 (:table-name %2)) #{} _tables)]
     (is tables expected-tables))))

(deftest can-get-recipe-by-id
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{recipe(id: 1) { id }}" nil)
         expected {:data {:recipe {:id 1}}}]
     (is (= expected res)))))

(deftest can-get-all-recipes
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{ recipes { values {id}, page_info {next_page, previous_page}, total_count}}" nil)
         expected {:data 
                   {:recipes 
                    {:values [{:id 1} {:id 2} {:id 3} {:id 4}]
                     :page_info {:previous_page nil :next_page nil}
                     :total_count 4}}}]
     (is (= expected res)))))

(deftest can-get-ingredients
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (testing "Can get basic ingredient info"
     (let [res (q sys "{ ingredients { values {id}, page_info {next_page, previous_page}, total_count}}" nil)
           expected {:data
                     {:ingredients
                      {:values [{:id 1} {:id 2} {:id 3} {:id 4}]
                       :page_info {:previous_page nil :next_page nil}
                       :total_count 4}}}]
       (is (= expected res))))
   (testing "Can get all ingredient info"
     (let [res (q sys "{ ingredients { values { id, name, unit_name, unit_name_plural }}}" nil)
           expected {:data 
                     {:ingredients 
                      {:values (vec (test-data->map "test-data/test-data.edn" 'ingredients))}}}]
       (is (= expected res))))))

(deftest can-get-equipment
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{ingredients { values { id }, page_info {next_page, previous_page}, total_count}}" nil)
         expected {:data 
                   {:ingredients
                    {:values [{:id 1} {:id 2} {:id 3} {:id 4}]
                     :page_info {:previous_page nil :next_page nil}
                     :total_count 4}}}]
     (is (= expected res)))))

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

(deftest can-search
  (using
   [sys (db+search-sys)
    _   "test-data/test-data.edn"]
   (testing "search for"
     (testing "recipes"
       (let [{:keys [add! search]} (-> sys :search :api)]
         ; we have to call this here, because on boot, the
         ; test-data hasn't been added to the database yet.
         (search/update-index-on-boot
          (-> sys :database :conn)
          (-> sys :search :index)
          :recipes
          (-> sys :search :analyzer))
         #_(add! [{:id 1 :name "Pancakes"}] :recipes)
         (is (= [{:id "1" :name "Pancakes"}]
                (search {:name "Pancakes"}))))))))

(deftest can-encode-page-info
  (testing "Can encode page info when"
    (testing "there is both a next and a previous page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 6)) 5 :i {:page-num 1})
            expected {:previous_page 
                      (utils/edn->base64 {:before 0
                                          :key :i
                                          :page-num 0})
                      :next_page
                      (utils/edn->base64 {:after 4
                                          :key :i
                                          :page-num 2})}]
        (is (= expected res))))
    (testing "there is only a next page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 6)) 5 :i nil)
            expected {:next_page
                      (utils/edn->base64 {:after 4
                                          :key :i
                                          :page-num 1})}]
        (is (= expected res))))
    (testing "there is only a previous page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 2 6)) 5 :i {:page-num 1})
            expected {:previous_page
                      (utils/edn->base64 {:before 2
                                          :key :i
                                          :page-num 0})}]
        (is (= expected res))))
    (testing "there is only one page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 4)) 5 :i nil)
            expected {}]
        (is (= expected res))))))