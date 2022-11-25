(defproject skullery "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.pedestal/pedestal.service "0.5.10"]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.10"]
                 ;; [io.pedestal/pedestal.immutant "0.5.10"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.10"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.26"]
                 [org.slf4j/jcl-over-slf4j "1.7.26"]
                 [org.slf4j/log4j-over-slf4j "1.7.26"]

                 ; Lacinia
                 [com.walmartlabs/lacinia-pedestal "1.1"]
                 [com.walmartlabs/lacinia "1.2-alpha-3"]

                 [expound "0.9.0"]
                 [com.stuartsierra/component "1.1.0"]

                 ; DB
                 [com.h2database/h2 "2.1.214"]
                 [com.github.seancorfield/next.jdbc "1.3.847"]
                 [com.github.seancorfield/honeysql "2.4.947"]
                 [com.mchange/c3p0 "0.9.5.5"]

                 ;; Lucene
                 [clucie "0.4.2"]

                 ;; Utils
                 [medley "1.4.0"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "skullery.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.10"]]}
             :uberjar {:aot [skullery.main]}}
  :main ^{:skip-aot true} skullery.main)
