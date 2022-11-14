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
            [next.jdbc.result-set :as rs]))

(defn test-system
  []
  (merge (component/system-map)
         (schema/new-schema)
         (db/new-database 1 "skullery")))

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
  (let [sys (component/start-system (merge (component/system-map)
                                           (db/new-database 1 "open-db-test")))
        _tables (jdbc/execute! (-> sys :database :conn) ["show tables"] {:builder-fn rs/as-unqualified-kebab-maps})
        tables (reduce #(conj %1 (:table-name %2)) #{} _tables)]
    (is tables expected-tables)))

(deftest can-get-recipe-by-id
  (let [sys (component/start-system (test-system))
        res (q sys "{recipe(id: 1) { id }}" nil)]
    (is (= {:data {:recipe {:id 1}}} res))
    (component/stop-system sys)))