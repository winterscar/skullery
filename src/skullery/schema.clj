(ns skullery.schema
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.expound]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]] ; Used to get useful spec messages
            [expound.alpha :as expound]
            [com.stuartsierra.component :as component]
            [skullery.db :as db]))


(defn resolve-recipe [conn]
  (fn [_ args _] (db/get-recipe-by-id conn (:id args))))
(defn resolve-recipes [conn]
  (fn [_ _ _] (db/list-recipes conn)))
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