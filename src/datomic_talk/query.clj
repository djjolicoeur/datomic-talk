(ns datomic-talk.query
  (:require [datomic.api :as d]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.log :as log]
            [ring.util.response :as ring]))


;;; Generic Query Functions

(defn generate-query-symbol [qname]
  (symbol (str (name qname) "*")))

(defmacro defquery
  "Define a named query, pull pattern, and
   key word params to build a query context.
   params should be ordered as they should be
   injected into a 'datomic.api/q' call"
  [qname query pull & params]
  (let [default-pattern# '[*]]
   `(do
      (def ~qname identity)
      (def ~(generate-query-symbol qname) {:query ~query
                                           :pull ~pull
                                           :params (quote ~params)}))))

(defn path-params->uuids
  "Parse string path params to UUIDs.  It may be
   necessary to filter this if you have non UUID
   path parameters."
  [path-params]
  (->> path-params
       (map (fn [[k v]] {k (java.util.UUID/fromString v)}))
       (into {})))

(defn param-values
  "Create an ordered list of values from keyword value
   map of params, given a param spec"
  [param-spec req-params]
  (-> req-params
      (select-keys param-spec)
      (vals)))

(defn query
  "Given a query context and a request, apply the query
   to the request parameters."
  [{:keys [query pull params]}
   {:keys [path-params query-params dbval] :as req}]
  (let [req-params (merge (path-params->uuids path-params) query-params)
        base-query [query dbval (or pull '[*])]
        q-params (param-values params req-params)]
    (apply d/q (concat base-query q-params))))

;; User pull pattern
(def user-pattern '[*])

;; query for one user
(defquery user
  '[:find (pull ?u pattern) .
    :in $ pattern ?id
    :where [?u :model/id ?id]]
  user-pattern
  :user-id)

;; query for all users
(defquery users
  '[:find [(pull ?u pattern) ...]
    :in $ pattern
    :where [?u :model/type :user]]
  user-pattern)

(def todo-pattern
  '[* {:todo/status [:db/ident]
       :todo/user [:db/id :user/firstname :user/lastname]}])

(defquery todo
  '[:find (pull ?t pattern) .
    :in $ pattern ?uid ?tid
    :where
    [?u :model/id ?uid]
    [?t :model/id ?tid]
    [?t :todo/user ?u]]
  todo-pattern
  :user-id :todo-id)

(defquery todos
  '[:find [(pull ?t pattern) ...]
    :in $ pattern ?uid
    :where [?u :model/id ?uid]
           [?t :todo/user ?u]]
  todo-pattern
  :user-id)

;; Map of routes to queries
(def route->query
  {:datomic-talk.query/user user*
   :datomic-talk.query/users users*
   :datomic-talk.query/todo todo*
   :datomic-talk.query/todos todos*})


;; Pedestal interceptor. If a route has an associated
;; query, apply the query to the request params and
;; assoc the result in the request context as :query-result
(def query-interceptor
  (interceptor
   {:name ::inject-query
    :enter
    (fn [{:keys [route request] :as context}]
      (log/info :task ::inject-query
                :route (:route-name route))
      (if-let [q (route->query (:route-name route))]
        (assoc-in context [:request :query-result] (query q request))
        context))}))


(def entity-response-interceptor
  (interceptor
   {:name ::inject-response
    :leave
    (fn [{:keys [route request] :as context}]
      (log/info :task ::inject-response
                :route (:route-name route))
      (if-let [resp (:query-result request)]
        (assoc-in context [:response] (ring/response resp))
        (assoc-in context [:response] (ring/not-found {}))))}))
