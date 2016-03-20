(ns datomic-talk.service
  (:require [datomic-talk.query :as query]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

(defn home-page
  [request]
  (ring-resp/response "Hello World!"))


(defn slide-show
  [request]
  (ring-resp/resource-response "index.html" {:root "public"}))



(defroutes routes
  ;; Defines "/" and "/about" routes with their associated :get handlers.
  ;; The interceptors defined after the verb map (e.g., {:get home-page}
  ;; apply to / and its children (/about).
  [[["/" {:get home-page}
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/about" {:get about-page}]
     ["/api"
      ["/v0.1" ^:interceptors [bootstrap/json-body
                               middlewares/keyword-params
                               query/query-interceptor
                               query/entity-response-interceptor]
       ["/users" {:get query/users}
        ["/:user-id" {:get query/user}
         ["/todos" {:get query/todos}
          ["/:todo-id" {:get query/todo}]]]]]]]]])

;; Consumed by datomic-talk.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ;;::bootstrap/host "localhost"
              ::bootstrap/port 8080})
