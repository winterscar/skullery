(ns skullery.server
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [io.pedestal.http :as http]
            [skullery.utils :refer [ ==> ]]
            [io.pedestal.log :as log]))


(defrecord Server [schema server]
  component/Lifecycle
  (start [this]
    (==> (assoc this :server (-> schema
                                 :schema
                                 (lp/default-service {:host "0.0.0.0"})
                                 (http/create-server)
                                 (http/start)))
         (log/info :component/server {:state "started"})))

  (stop [this]
    (==> (do (when server
               (http/stop server))
             (assoc this :server nil))
         (log/info :component/server {:state "stopped"}))))

(defn new-server []
  {:server (component/using (map->Server {})
                            [:schema])})