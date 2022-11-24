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
   [skullery.db :as db]
   [medley.core :as medley]) 
  (:import (clojure.lang IPersistentMap)))

(defonce system (system/new-system))

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
  (browse-url "http://localhost:8888/ide")
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