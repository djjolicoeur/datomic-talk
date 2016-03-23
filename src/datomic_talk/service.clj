(ns datomic-talk.service
  (:require [datomic-talk.query :as query]
            [datomic-talk.schema :as schema]
            [datomic-talk.post :as post]
            [datomic-talk.put :as put]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.log :as log]
            [ring.util.response :as ring-resp]))


;;Response Interceptor

(defn export-response
  "Given a query response, export it to the appropriate
   schema.  Maps will be treated as single entities,
   sequences as a sequence of entities. resp not
   meeting those conditions will be returned unchanged"
  [resp]
  (cond
    (map? resp) (schema/export-entity resp)
    (seq resp) (mapv schema/export-entity resp)
    true resp))

(defn interceptor-response
  "Pull any interceptor generated responses"
  [request]
  (->> request
       ((juxt :query-result :post-result :put-result))
       (some identity)))

(def entity-response-interceptor
  (interceptor
   {:name ::inject-response
    :leave
    (fn [{:keys [route request] :as context}]
      (log/info :task ::inject-response
                :route (:route-name route))
      (if-let [resp (interceptor-response request)]
        (assoc  context :response (ring-resp/response (export-response resp)))
        (assoc  context :response (ring-resp/not-found {}))))}))


;; Handlers

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
     ^:interceptors [(body-params/body-params
                      (body-params/default-parser-map
                        :json-options {:key-fn keyword})) bootstrap/html-body]
     ["/about" {:get about-page}]
     ["/api"
      ["/v0.1" ^:interceptors [bootstrap/json-body
                               middlewares/keyword-params
                               post/entity-post-interceptor
                               put/entity-put-interceptor
                               query/query-interceptor
                               entity-response-interceptor]
       ["/users" {:get query/users
                  :post post/user}
        ["/:user-id" {:get query/user
                      :put put/user}
         ["/todos" {:get query/todos
                    :post post/todo}
          ["/:todo-id" {:get query/todo
                        :put put/todo}]]]]]]]]])

;; Consumed by datomic-talk.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ;;::bootstrap/host "localhost"
              ::bootstrap/port 8080})
