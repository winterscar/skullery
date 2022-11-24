(ns skullery.server
  (:gen-class) ; for -main method in uberjar
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [io.pedestal.http :as http]
            [skullery.schema :refer [skullery-schema]]))
(defrecord Server [schema server]
  component/Lifecycle
  (start [this]
    (assoc this :server (-> schema
                            :schema
                            (lp/default-service nil)
                            (http/create-server)
                            (http/start))))
  (stop [this]
    (when server
      (http/stop server))
    (assoc this :server nil)))

(defn new-server []
  {:server (component/using (map->Server {})
                            [:schema])})


(def service (lp/default-service skullery-schema nil))
(defonce runnable-service (http/create-server service))

(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (println "\nCreating your server...")
  (http/start runnable-service))