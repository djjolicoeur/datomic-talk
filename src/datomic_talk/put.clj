(ns datomic-talk.put
  (:require [datomic.api :as d]
            [datomic-talk.query :as query]
            [datomic-talk.post :as post]
            [datomic-talk.schema :as schema]
            [datomic-talk.merge :as merge]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.log :as log]))


(defmacro defput [postname]
  `(def ~postname identity))


(defn put-entity [{:keys [dbval db json-params] :as request}]
  (let [update-entity (schema/update-entity json-params)
        old (select-keys update-entity [:db/id])
        updated (merge/merge-tx dbval old update-entity)
        {:keys [db-after]} @(d/transact db updated)]
    (->>  (post/pull db-after old)
          (assoc request :put-result))))


(defput user)
(defput todo)

(def route->post
  {:datomic-talk.put/user put-entity
   :datomic-talk.put/todo put-entity})

(def entity-put-interceptor
  (interceptor
   {:name ::inject-put
    :enter
    (fn [{:keys [route request] :as context}]
      (log/info :task ::inject-post
                :route (:route-name route))
      (if-let [p (route->post (:route-name route))]
        (assoc context :request (put-entity request))
        context))}))
