(ns wip "Doodles, scratches and discarded code")

(def recipe-schema
  (m/schema [:map
             [:name string?]
             [:ingredients [:sequential [:map [[:ingredient_id int?]
                                               [:quantity      double?]
                                               [:unit          [:enum "MASS" "VOLUME" "LENGTH" "UNIT"]]]]]]
             [:equipment   [:sequential [:map [:equipment_id   int?]]]]
             [:steps       [:sequential [:map [[:name string?]
                                               [:body string?]
                                               [:ingredients [:sequential]]]]]]]))