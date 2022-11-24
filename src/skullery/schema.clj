(ns skullery.schema
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.expound]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]] ; Used to get useful spec messages
            [expound.alpha :as expound]
            [skullery.db :as db]
            [io.pedestal.log :as log]
            [skullery.utils :as utils]
            [medley.core :as medley]))

(defn encode-page-info
  [results page-size sort-key prev-cursor]
  (let [curr  (or (:page-num prev-cursor) 0)
        next? (< page-size (count results))
        prev? (< 0 curr)
        enc   utils/edn->base64]
    (->> {:previous_page (and prev?
                              (enc {:before (-> results first sort-key)
                                    :key sort-key
                                    :page-num (dec curr)}))
          :next_page     (and next?
                              (enc {:after (-> results (nth (dec page-size)) sort-key)
                                    :key sort-key
                                    :page-num (inc curr)}))}
         (medley/filter-vals identity))))

(defn decode-cursor
  [cursor-str]
  (when cursor-str (utils/base64->edn cursor-str)))

(defn resolve-paginated-query
  "Generic wrapper that takes a db function that returns a list of
   results. Calls the db function and wraps the result with pagination
   metadata.
   The db function must be of the following shape:
   (db-conn filters-map cursor-map count) -> [results total-count]
   Additionally, takes:
   - an args object from the query, which may contains
     :filter and :page keys
   - A database connection
   - A count of results per page."
  [conn db-fn args count]
  (let [cursor    (decode-cursor (:page args))
        filters   (-> args :filter)
        [res tot] (db-fn conn filters cursor count)]
    {:values (take count res)
     :page_info (encode-page-info res count :id cursor)
     :total_count tot}))

(defn resolve-recipe [conn]
  (fn [_ args _] (db/get-recipe-by-id conn (:id args))))

(defn resolve-recipes [conn]
  (fn [_ args _]
    (log/info :schema/resolve-recipes args)
    (resolve-paginated-query conn db/list-recipes args 25)))

(defn resolve-conversions [conn]
  (fn [_ _ ingredient] (db/list-ingredient-conversions conn ingredient)))

(defn resolve-recipe-ingredients [conn]
  (fn [_ _ recipe] (db/list-recipe-ingredients conn recipe)))

(defn resolve-steps [conn]
  (fn [_ _ recipe] (db/list-recipe-steps conn recipe)))

(defn resolve-step-ingredients [conn]
  (fn [_ _ step] (db/list-step-ingredients conn step)))

(defn resolve-recipe-equipment [conn]
  (fn [_ _ recipe] (db/list-recipe-equipment conn recipe)))

(defn resolve-equipment [conn]
  (fn [_ args _]
    (resolve-paginated-query conn db/list-equipment args 25)))

(defn resolve-ingredients [conn]
  (fn [_ args _]
    (resolve-paginated-query conn db/list-ingredients args 25)))


(defn compile-explain
  "Wraps schema/compile with expound, so that any spec errors
   are printed nicely instead of the mess that spec usually outputs."
  [schema]
  (binding [s/*explain-out* expound/printer]
    (schema/compile schema)))

(defn skullery-schema
  [system]
  (let [conn (-> system :database :conn)]
    (-> (io/resource "schema.edn")
        slurp
        edn/read-string
        (attach-resolvers
         {;; QUERIES
          :resolve-recipe (resolve-recipe conn)
          :resolve-recipes (resolve-recipes conn)
          :resolve-steps (resolve-steps conn)
          :resolve-step-ingredients (resolve-step-ingredients conn)
          :resolve-recipe-ingredients (resolve-recipe-ingredients conn)
          :resolve-conversions (resolve-conversions conn)
          :resolve-recipe-equipment (resolve-recipe-equipment conn)
          :resolve-equipment (resolve-equipment conn)
          :resolve-ingredients (resolve-ingredients conn)
        ;; MUTATIONS
          :mutation-add-recipe (fn [_ _ _] {:foo 0})})
        compile-explain)))

(defrecord Schema [schema database]
  component/Lifecycle
  (start [this] (assoc this :schema (skullery-schema this)))
  (stop [this] (assoc this :schema nil)))

(defn new-schema
  []
  {:schema (-> {} map->Schema (component/using [:database]))})