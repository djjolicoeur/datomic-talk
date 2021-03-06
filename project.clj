(defproject datomic-talk "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [io.pedestal/pedestal.service "0.4.1"]
                 [io.pedestal/pedestal.jetty "0.4.1"]
                 [ch.qos.logback/logback-classic "1.1.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.12"]
                 [org.slf4j/jcl-over-slf4j "1.7.12"]
                 [org.slf4j/log4j-over-slf4j "1.7.12"]
                 [com.datomic/datomic-free "0.9.5350"]
                 [com.stuartsierra/component "0.2.3"]
                 [reloaded.repl "0.2.1"
                  :exclusions [org.clojure/tools.namespace
                               suspendable]]
                 [suspendable "0.1.1"]
                 [prismatic/schema "1.0.5"]
                 [clj-http "2.1.0"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[io.pedestal/pedestal.service-tools "0.4.1"]]}})
