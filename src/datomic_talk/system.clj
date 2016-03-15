(ns datomic-talk.system
  (:require [com.stuartsierra.component :as component]
            [datomic-talk.components.server
             :refer [new-service-map new-server]]
            [datomic-talk.components.datomic :refer [new-dev-datomic]]))


(defn dev-system []
  (component/system-map
   :datomic-uri "datomic:mem://datomic-talk"
   :schema "resources/schema.edn"
   :dev-facts "resources/facts.edn"
   :db (component/using
        (new-dev-datomic)
        [:datomic-uri :schema :dev-facts])
   :service-map (component/using (new-service-map) [:db])
   :server (component/using (new-server) [:service-map])))
