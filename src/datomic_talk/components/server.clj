(ns datomic-talk.components.server
  (:gen-class) ; for -main method in uberjar
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as server]
            [datomic-talk.service :as service]
            [io.pedestal.interceptor.helpers :refer [on-request]]
            [io.pedestal.log :refer [debug info warn error]]))


(defn inject-conns-interceptor
  "Inject DB on incoming request context"
  [db]
  (on-request
   ::inject-db
   (fn [context]
     (info "INJECTING DB CONTEXT ON REQUEST" (:conn db))
     (assoc context :db db))))

(defrecord ServiceMap [db service-map]
  component/Lifecycle
  (start [this]
    (info "Building pedestal service map...")
    (info "Injecting db " @db)
    (let [sys-interceptor (inject-conns-interceptor db)
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
