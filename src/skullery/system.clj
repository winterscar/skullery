(ns skullery.system
  (:require [com.stuartsierra.component :as component]
            [skullery.schema :as schema]
            [skullery.server :as server]
            [skullery.db :as db]))

(defn new-system
  []
  (merge (component/system-map)
         (server/new-server)
         (schema/new-schema)
         (db/new-database 1 "skullery")))