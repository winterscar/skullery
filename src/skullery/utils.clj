(ns skullery.utils
  (:require
   [clojure.walk :as walk])
  (:import
   (clojure.lang IPersistentMap)))

(defn group-by-namespace
  "Takes a map m of namespaced keywords and returns a map where all keywords in
   the same namespace are placed into a map as a value of the keyword namespace.
   Example: {:foo/bar 1, :foo/baz 2, bob: 3} -> {:foo {:bar 1, :baz 2} :bob 3}."
  [m]
  (reduce (fn [a c] (let [[k v] c]
                      (assoc-in a
                                (remove nil?
                                        [(-> k namespace keyword) (-> k name keyword)])
                                v)))
          {} m))

(defn un-namespace
  "Takes a map m of namespaced keywords and removes the namespaces specified in the set n"
  [m s]
  (update-keys m #(if (s (-> % namespace keyword)) (-> % name keyword) %)))

(defn simplify
  "Converts all ordered maps nested within the map into standard hash maps, and
   sequences into vectors, which makes for easier constants in the tests, and eliminates ordering problems."
  [m]
  (walk/postwalk
   (fn [node]
     (cond
       (instance? IPersistentMap node)
       (into {} node)

       (seq? node)
       (vec node)

       :else
       node))
   m))


; from https://rosettacode.org/wiki/Extract_file_extension#Clojure
(defn file-extension [s]
  (second (re-find #"(\.[a-zA-Z0-9]+)$" s)))