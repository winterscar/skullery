(ns skullery.search-test
  (:require [clojure.test :refer :all]
            [skullery.search :as search]
            [skullery.test-utils :refer :all]))

(deftest can-search
  (using
   [sys (db+search-sys)
    _   "test-data/test-data.edn"]
   (testing "search for"
     (testing "recipes"
       (let [{:keys [search]} (-> sys :search :api)]
         ; we have to call this here, because on boot, the
         ; test-data hasn't been added to the database yet.
         (search/update-index-on-boot
          (-> sys :database :conn)
          (-> sys :search :index)
          :recipes
          (-> sys :search :analyzer))
         #_(add! [{:id 1 :name "Pancakes"}] :recipes)
         (is (= [{:id "1" :name "Pancakes"}]
                (search {:name "Pancakes"}))))))))