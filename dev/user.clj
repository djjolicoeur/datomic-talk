(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [clj-http.client :as http]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [datomic-talk.system :as app]
            [reloaded.repl
             :refer [go init reset reset-all start stop system]]))


(reloaded.repl/set-init! app/dev-system)


(def post-user-url "http://localhost:8080/api/v0.1/users")

(defn put-user-url [id] (str "http://localhost:8080/api/v0.1/users/" id))

(defn post-todo-url
  [user-id]
  (str "http://localhost:8080/api/v0.1/users/" user-id "/todos"))

(defn post-user [email firstname lastname]
  (http/post post-user-url {:content-type :json
                            :form-params {:user/email email
                                          :user/firstname firstname
                                          :user/lastname lastname
                                          :model/type "user"}}))

(defn put-user [uuid params]
  (http/put (put-user-url uuid) {:content-type :json
                                 :form-params params}))
