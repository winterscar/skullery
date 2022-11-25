(ns skullery.integration-test
  (:require [clojure.test :refer :all]
            [skullery.test-utils :refer :all]))



(deftest can-get-recipe-by-id
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{recipe(id: 1) { id }}" nil)
         expected {:data {:recipe {:id 1}}}]
     (is (= expected res)))))

(deftest can-get-all-recipes
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{ recipes { values {id}, page_info {next_page, previous_page}, total_count}}" nil)
         expected {:data
                   {:recipes
                    {:values [{:id 1} {:id 2} {:id 3} {:id 4}]
                     :page_info {:previous_page nil :next_page nil}
                     :total_count 4}}}]
     (is (= expected res)))))

(deftest can-get-ingredients
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (testing "Can get basic ingredient info"
     (let [res (q sys "{ ingredients { values {id}, page_info {next_page, previous_page}, total_count}}" nil)
           expected {:data
                     {:ingredients
                      {:values [{:id 1} {:id 2} {:id 3} {:id 4}]
                       :page_info {:previous_page nil :next_page nil}
                       :total_count 4}}}]
       (is (= expected res))))
   (testing "Can get all ingredient info"
     (let [res (q sys "{ ingredients { values { id, name, unit_name, unit_name_plural }}}" nil)
           expected {:data
                     {:ingredients
                      {:values (vec (test-data->map "test-data/test-data.edn" 'ingredients))}}}]
       (is (= expected res))))))

(deftest can-get-equipment
  (using
   [sys (db+schema-sys)
    _    "test-data/test-data.edn"]
   (let [res (q sys "{ingredients { values { id }, page_info {next_page, previous_page}, total_count}}" nil)
         expected {:data
                   {:ingredients
                    {:values [{:id 1} {:id 2} {:id 3} {:id 4}]
                     :page_info {:previous_page nil :next_page nil}
                     :total_count 4}}}]
     (is (= expected res)))))