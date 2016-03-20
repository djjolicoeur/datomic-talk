(ns datomic-talk.components.server
  (:gen-class) ; for -main method in uberjar
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [datomic-talk.service :as service]
            [io.pedestal.http :as server]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.log :as log
             :refer [debug info warn error]]))



(defn inject-db-interceptor
  "Inject db in every request."
  [db]
  (interceptor
   {:name ::inject-db
    :enter
    (fn inject-components
      [context]
      (log/debug :task ::inject-db
                 :db db)
      (update-in
       context [:request]
       merge
       {:dbval (-> db :conn d/db)
        :db (:conn db)}))}))


(defrecord ServiceMap [db service-map]
  component/Lifecycle
  (start [this]
    (info "Building pedestal service map...")
    (info "Injecting db " @db)
    (let [sys-interceptor (inject-db-interceptor db)
          service-map (merge service/service
                             {:env :dev
                              ::server/join? false})
          service-map (-> service-map
                          server/default-interceptors
                          (update-in  [::server/interceptors]
                                      conj sys-interceptor))]
      (assoc this :service-map service-map)))
  (stop [this]
    (update-in this [:service-map ::server/interceptors] pop)))

(defn new-service-map []
  (map->ServiceMap {}))


(defrecord Server [service-map server]
  component/Lifecycle
  (start [this]
    (if server this
        (let [server (server/create-server (:service-map service-map))]
          (info "Starting Service")
          (server/start server)
          (assoc this :server server))))
  (stop [this]
    (if-not server this
            (do (server/stop server)
                (assoc this :server nil)))))

(defn new-server []
    (map->Server {}))
