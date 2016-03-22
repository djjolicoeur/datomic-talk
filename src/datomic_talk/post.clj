(ns datomic-talk.post
  (:require [datomic.api :as d]
            [datomic-talk.query :as query]
            [datomic-talk.schema :as schema]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.log :as log]))


(defmacro defpost [postname]
  `(def ~postname identity))


(defn pull  [db e]
  (condp = (:model/type e)
    :user (d/pull db query/user-pattern (:db/id e))
    :todo (d/pull db query/todo-pattern (:db/id e))
    e))

(defn post-entity [{:keys [db json-params] :as request}]
  (let [new-entity (schema/new-entity json-params)
        temp (:db/id new-entity)
        {:keys [db-after tempids]} @(d/transact db [new-entity])]
    (->> temp
        (d/resolve-tempid db-after tempids)
        (d/entity db-after)
        (pull db-after)
        (assoc request :post-result))))


(defpost user)
(defpost todo)

(def route->post
  {:datomic-talk.post/user post-entity
   :datomic-talk.post/todo post-entity})

(def entity-post-interceptor
  (interceptor
   {:name ::inject-post
    :enter
    (fn [{:keys [route request] :as context}]
      (log/info :task ::inject-post
                :route (:route-name route))
      (if-let [p (route->post (:route-name route))]
        (assoc context :request (post-entity request))
        context))}))
