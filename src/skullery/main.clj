(ns skullery.main
  "The entrypoint of the application when run."
  (:gen-class) ; for -main method in uberjar
  (:require [skullery.system :as sys]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]))



(defn -main
  "The entry-point for 'lein run'"
  [& args]
  (log/info :skullery {:state "starting..."})
  (component/start-system (sys/new-system))
  (log/info :skullery {:state "started."}))