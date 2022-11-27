(ns user
  (:require
   [com.walmartlabs.lacinia :as lacinia]
   [clojure.java.browse :refer [browse-url]]
   [skullery.system :as system]
   [clojure.walk :as walk]
   [com.stuartsierra.component :as component]
   [skullery.utils :refer [simplify base64->edn edn->base64]]
   ; imported for repl usage
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as jdbcs]
   [skullery.server :as server]
   [skullery.schema :as schema]
   [skullery.db :as db]
   [medley.core :as medley]) 
  (:import (clojure.lang IPersistentMap)))

(defonce system (system/new-system))
(defonce opened-browser (atom false))

(defn new-sys []
  (merge 
   (component/system-map)
   (server/new-server)
   (schema/new-schema)
   (db/new-database 1 "skullery")))

(defn q
  [query-string]
  (-> system
      :schema-provider
      :schema
      (lacinia/execute query-string nil nil)
      simplify))

(defn start
  []
  (alter-var-root #'system component/start-system)
  (when (not @opened-browser)
    (browse-url "http://localhost:8888/ide")
    (reset! opened-browser true))
  :started)

(defn stop
  []
  (alter-var-root #'system component/stop-system)
  :stopped)

(comment
  (def system (system/new-system))
  (stop)
  (start)
  )


(defn sql> [query]
  (jdbc/execute! (-> system :database :conn) (sql/format query)))

(defn sqlr> [query]
  (jdbc/execute! (-> system :database :conn) [query]))