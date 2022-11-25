(ns skullery.schema-test
  (:require [clojure.test :refer :all]
            [skullery.schema :as schema]
            [skullery.test-utils :refer :all]
            [skullery.utils :as utils]))

(deftest can-encode-page-info
  (testing "Can encode page info when"
    (testing "there is both a next and a previous page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 6)) 5 :i {:page-num 1})
            expected {:previous_page
                      (utils/edn->base64 {:before 0
                                          :key :i
                                          :page-num 0})
                      :next_page
                      (utils/edn->base64 {:after 4
                                          :key :i
                                          :page-num 2})}]
        (is (= expected res))))
    (testing "there is only a next page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 6)) 5 :i nil)
            expected {:next_page
                      (utils/edn->base64 {:after 4
                                          :key :i
                                          :page-num 1})}]
        (is (= expected res))))
    (testing "there is only a previous page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 2 6)) 5 :i {:page-num 1})
            expected {:previous_page
                      (utils/edn->base64 {:before 2
                                          :key :i
                                          :page-num 0})}]
        (is (= expected res))))
    (testing "there is only one page"
      (let [res (schema/encode-page-info
                 (map (fn [i] {:i i}) (range 4)) 5 :i nil)
            expected {}]
        (is (= expected res))))))