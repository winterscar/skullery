(ns skullery.db
  #_{:clj-kondo/ignore [:refer-all]}
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [skullery.db :as db]))

(def as-lower "Just a convenient name for the builder function most of the queries use."
  {:builder-fn rs/as-unqualified-lower-maps})


(defn execute-one! [q conn] (as-> (sql/format q) query (jdbc/execute-one! conn query as-lower)))
(defn execute!
  "Convenience wrapper around jdbc/execute, that provides honeysql formatting and sensible defaults.
   Expects a honey-sql compatible query-map, and a something that can produce a db connection.
   Optionally, provide format-opts to overwrite the defaults for honey-sql, and query-opts to overwrite the
   defaults for jdbc/execute!."
  ([q conn] (execute! q conn {} as-lower))
  ([q conn format-opts execute-opts] (as-> (sql/format q format-opts) query (jdbc/execute! conn query execute-opts))))

(defn exec-file!
  "Given a database connection and path to a sql resource file, executes the contents
   of that file against the database."
  [conn file]
  (let [sql (-> file io/resource slurp)]
    (jdbc/execute-one! conn [sql])))

(defn apply-migrations
  "Applies all specified database migrations, where migrations is a seq of migration numbers."
  [conn migrations]
  (doseq [migration migrations]
    (exec-file! conn (str "migrations/migration-" migration ".sql"))
    (jdbc/execute-one! conn (sql/format {:insert-into [:databaseversion]
                                         :values [{:version migration}]}))))
(defn calculate-migrations
  "Creates a migration path from the current database version to the desired version.
   Returns a list of migration numbers to apply."
  [conn desired-version]
  (let [version-table-query '{select [table_name] from [information_schema.tables]
                              where [= table_name "DATABASEVERSION"]}
        version-table-exists (jdbc/execute-one! conn (sql/format version-table-query))]
    (if version-table-exists
      ; There have already been some migrations, lets find out what our migration path looks like
      (let [query {:select [:version] :from [:databaseversion] :order-by [[:version :desc]]}
            res (jdbc/execute-one! conn (sql/format query) as-lower)
            current-version (or (:version res) 0)]
        (range (inc current-version) (inc desired-version)))
      ; There is no migration table, apply all available migraitions
      (range 1 (inc desired-version)))))

(defn migrate!
  "Tries to migrate the database to version 'to'. no-op if the database is already up to date."
  [conn to]
  (let [required-migrations (calculate-migrations conn to)]
    (apply-migrations conn required-migrations)))


(defrecord Database [version name]
  component/Lifecycle
  (start [this]
    (let [conn (-> {:dbtype "h2" :dbname name}
                   jdbc/get-datasource
                   jdbc/get-connection)]
      (migrate! conn version)
      (assoc this :conn conn)))

  (stop [this] (assoc this :conn nil)))

(defn new-database [version name]
  {:database (map->Database {:version version :name name})})

;; Makes CHARACTER LARGE OBJECT fields return strings.
;; If performance starts to lag, consider streaming the
;; CLOBs instead of reading them fully to memory.
(extend-protocol rs/ReadableColumn
  java.sql.Clob
  (read-column-by-label [^java.sql.Clob v _]
    (rs/clob->string v))
  (read-column-by-index [^java.sql.Clob v _2 _3]
    (rs/clob->string v)))

(defn get-recipe-by-id
  [conn id]
  (-> {:select [:id :name]
       :from   [:recipes]
       :where  [:= :recipes.id id]}
      (execute-one! conn)))

(defn list-recipes
  [conn]
  (-> {:select [:id :name]
       :from   [:recipes]}
      (execute! conn)))

(defn list-ingredient-conversions
  [conn ingredient]
  ; because the result contains illegal aliases ('from' and 'to')
  ; we need to use :quoted mode for this query, hence the need for uppercase keywords everywhere.
  (-> {:select [:ID [:CONVERT_FROM :from] [:CONVERT_TO :to] :TO_NOTE :FROM_NOTE :MULTIPLIER]
       :from   [:CONVERSIONS]
       :where  [:= :INGREDIENT_ID (ingredient :id)]}
      (execute! conn {:quoted true} as-lower)))

(defn list-recipe-ingredients
  [conn recipe]
  (let [res (-> {:select [[:recipeingredients.id :rid] :quantity :unit
                          :ingredients/id :ingredients/name :ingredients/unit_name :ingredients/unit_name_plural]
                 :from   [:recipeingredients]
                 :join   [:ingredients [:= :recipeingredients.ingredient_id :ingredients.id]]
                 :where  [:= :recipe_id (:id recipe)]}
                (execute! conn))]
    (when res
      (map (fn [row] {:id           (:rid row)
                      :quantity     (:quantity row)
                      :unit         (:unit row)
                      :ingredient   {:id               (:id row)
                                     :name             (:name row)
                                     :unit_name        (:unit_name row)
                                     :unit_name_plural (:unit_name_plural row)}}) res))))

(defn list-recipe-steps
  [conn recipe]
  (-> {:select   [:id :name :body :step_order]
       :from     [:steps]
       :where    [:= :recipe_id (recipe :id)]
       :order-by [:step_order]}
      (execute! conn)))


(defn list-step-ingredients
  [conn step]
  (let [res (-> {:select [:stepingredients/quantity
                          :recipeingredients/unit
                          :ingredients/id :ingredients/name :ingredients/unit_name :ingredients/unit_name_plural]
                 :from   [:stepingredients]
                 :join   [:recipeingredients [:= :stepingredients.recipe_ingredient_id :recipeingredients.id]
                          :ingredients       [:= :recipeingredients.ingredient_id :ingredients.id]]
                 :where  [:= :step_id (:id step)]}
                (execute! conn))]
    (when res
      (map (fn [row] {:quantity     (:quantity row)
                      :unit         (:unit row)
                      :ingredient   {:id               (:id row)
                                     :name             (:name row)
                                     :unit_name        (:unit_name row)
                                     :unit_name_plural (:unit_name_plural row)}}) res))))

(defn list-recipe-equipment
  [conn recipe]
  (-> {:select [:id :name]
       :from   [:recipeequipment]
       :join   [:equipment [:= :equipment.id :equipment_id]]
       :where  [:= :recipe_id (:id recipe)]}
      (execute! conn)))