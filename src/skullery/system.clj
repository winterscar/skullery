(ns skullery.system
  (:require [com.stuartsierra.component :as component]
            [skullery.schema :as schema]
            [skullery.server :as server]
            [skullery.db :as db]))

(def final-version
  "Defines the current database schema version, as used by the migrations
   process. For whatever version number n is defined here, the 
   application expects the classpath to contain a folder (migrations/) of sequencial migration
   files named migration-0.sql, migration-1.sql, ... migration-n.sql"
  1)

(def database-name
  "Defines the name of the database-file on disk. May be either a simple name, or an absolute path.
   Relative paths are not permitted."
  "skullery")

(defn new-system
  []
  (merge (component/system-map)
         (server/new-server)
         (schema/new-schema)
         (db/new-database final-version database-name)))