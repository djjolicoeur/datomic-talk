(ns datomic-talk.put
  (:require [datomic.api :as d]
            [datomic-talk.query :as query]
            [datomic-talk.post :as post]
            [datomic-talk.schema :as schema]
            [datomic-talk.merge :as merge]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.log :as log]))


(defmacro defput
  "For generic puts, we can just bind a symbol to identity
   to be used as a unique handler name"
  [postname]
  `(def ~postname identity))


(defn put-entity
  "Given a put request, grab the current entity,
   merge with the passed in entity, persist, and
   attach to the request"
  [{:keys [dbval db json-params] :as request}]
  (let [update-entity (schema/update-entity json-params)
        old (select-keys update-entity [:db/id])
        updated (merge/merge-tx dbval old update-entity)
        {:keys [db-after]} @(d/transact db updated)]
    (->>  (post/pull db-after old)
          (assoc request :put-result))))


;;Bind user and todo put handlers
(defput user)
(defput todo)


(def route->put
  "Route put requests to generic handler in
   interceptor"
  {:datomic-talk.put/user put-entity
   :datomic-talk.put/todo put-entity})

(def entity-put-interceptor
  "Given a context, if a put route is present,
   associate put-request on the request otherwise
   pass the context unchaged"
  (interceptor
   {:name ::inject-put
    :enter
    (fn [{:keys [route request] :as context}]
      (log/info :task ::inject-post
                :route (:route-name route))
      (if-let [p (route->put (:route-name route))]
        (assoc context :request (put-entity request))
        context))}))
