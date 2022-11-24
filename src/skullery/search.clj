(ns skullery.search
  (:require [clojure.core.async :refer [<! <!! >! chan close! go go-loop]]
            [clucie.analysis :as analysis]
            [clucie.core :as clucie]
            [clucie.store :as store]
            [com.stuartsierra.component :as component]
            [io.pedestal.log :as log]
            [skullery.db :as db]))

(def indexed-keys
  "An array of keys to be indexed by lucene."
  {:recipes [:id :name]})

(defn make-add!
  [chan]
  (fn add! [vals table]
    (log/info :search/add! {:chan chan :vals vals})
    (go (>! chan {:type :add
                  :table table
                  :vals vals
                  :keys (table indexed-keys)}))))

(defn make-edit!
  [chan]
  (fn edit! [val table]
    (go (>! chan {:type :edit
                  :table table
                  :val val
                  :keys (:table indexed-keys)}))))

(defn make-delete!
  [chan]
  (fn delete! [id table]
    (go (>! chan {:type :delete
                  :table table
                  :id id}))))

(defn make-search
  [index]
  (fn search [query]
    (log/info :search/execute query)
    (clucie/search index query 5)))

(defn -update-db
  [conn table id]
  (db/execute! {:update table,
                :set {:index_updated [:CURRENT_TIMESTAMP]},
                :where [:= :id id]}
               conn))

(defn -add-to-index
  [vals keys db-conn table index analyzer]
  (log/info :search/add-to-index {:vals vals})
  ; Add the value(s) to the index
  (clucie/add! index
               (map #(select-keys % keys) vals)
               keys
               analyzer)
  (log/info :search/add-to-index {:state "index updated"})
  ; For each value we added, set the index_updated field in the database
  (doseq [val vals]
    (-update-db db-conn table (:id val)))
  (log/info :search/add-to-index {:state "database updated"}))

(defn -drop-from-index
  [id index analyzer]
  (log/info :search/drop-from-index {:id id})
  (clucie/delete! index :id id analyzer))

(defn -edit-in-index
  [val keys db-conn table index analyzer]
  (log/info :search/edit-in-index {:val val})
  (let [id (:id val)]
    (clucie/update! index
                    (select-keys val keys)
                    keys
                    id
                    analyzer)
    (-update-db db-conn table id)))

(defn -keep-index-updated
  "Watches the provided channel for add, edit or delete messages,
   and updates the search index accordingly."
  [chan db-conn index analyzer]
  (go-loop []
    (if-let [msg (<! chan)]
      (do
        (log/info :search/keep-index-updated msg)
        (case (:type msg)
          :add    (-add-to-index (:vals msg) (:keys msg) db-conn (:table msg) index analyzer)
          :edit   (-edit-in-index (:val msg) (:keys msg) db-conn (:table msg) index analyzer)
          :delete (-drop-from-index (:id msg) index analyzer)
          (log/warn :search/recieved-unknown-msg-type msg))
        (recur))
      (do
        (log/warn :search/keep-index-updated {:state "exiting"})
        :done))))

(defn update-index-on-boot
  [db-conn index table analyzer]
  (log/info :update-index-on-boot {:db-conn db-conn
                                   :index index
                                   :table table})
  (let [to-update
        (db/execute! {:select :*
                      :from table
                      :where [:and [:!= :index_updated nil]
                              [:< :index_updated :updated_at]]}
                     db-conn)
        to-add
        (db/execute! {:select :*
                      :from :recipes}
                     db-conn)]
    (log/info :update-on-start/results {:to-update to-update
                                        :to-add    to-add})
    (doseq [update-record to-update]
      (-edit-in-index update-record
                      (table indexed-keys)
                      db-conn
                      table
                      index
                      analyzer))
    (-add-to-index to-add
                   (table indexed-keys)
                   db-conn
                   table
                   index
                   analyzer)))

(defrecord Search [search database channel done-chan api]
  component/Lifecycle

  (start [this]
    ; make a channel, start a go-loop that watches the channel     
    (let [analyzer (analysis/standard-analyzer)
          index-store (store/memory-store)
          chan (chan)
          api {:add! (make-add! chan)
               :edit! (make-edit! chan)
               :delete! (make-delete! chan)
               :search (make-search index-store)}
          done-chan (-keep-index-updated chan (:conn database) index-store analyzer)]
      (update-index-on-boot (:conn database) index-store :recipes analyzer)
      (log/info :component/search {:state "started"})
      (log/trace :component/search {:analyzer analyzer, :index-store index-store})
      (assoc this
             :analyzer analyzer
             :index index-store
             :channel chan
             :api api
             :done-chan done-chan)))

  (stop [this]
    ; close the channel    
    (log/info :component/search {:state "stopped"})
    (close! channel) ; close the channel, then
    (<!! done-chan) ; wait for all indexing to be complete before stopping
    (assoc this :analyzer nil :index nil :channel nil :done-chan nil :api :nil)))

(defn new-search []
  {:search (-> {} map->Search (component/using [:database]))})


(comment 
  "We need to maintain a search index.
   The way I plan on doing this by using the database to keep track of what has been indexed, and when.
   That way, we can query the database for any un-indexed records and just index them on startup.
   
   To keep the index up-to-data during operation, the search component will expose a channel
   which can be fed with add/edit/delete requests.
   
   To ensure the index integrity, on boot, we will do the following:
   - SELECT * FROM <TABLE> where indexed_at IS NULL OR indexed_at 'is before' updated_at
     > This will give us all records that need to be indexed
   
   One potential issue springs to mind: What happens if a row is dropped from the database while the system is offline?
   > The results will remain in the index.
   >> Because the index will simply return the IDs of the matches, when trying to actually resolve the value itself,
   if we get nothing back for a specific ID, we can issue a request to drop that value from the index. 
   ")