(ns datomic-talk.schema
  (:require [schema.core :as s]
            [schema.coerce :as c]
            [schema.macros :as macros]
            [schema.spec.core :as spec]
            [schema.utils :as utils]))


(defn enum?
  [v]
  (and (map? v) (every? #{:db/ident} (keys v))))

(defn ref? [v]
  (and (map? v) (every? #{:db/id} (keys v))))

(defn custom->keyword [v]
  (if (enum? v)
    (:db/ident v)
    (c/string->keyword v)))

(defn custom->long [v]
  (if (ref? v)
    (:db/id v)
    (c/safe-long-cast v)))


(def custom-coercions
  (merge c/+json-coercions+
         {s/Keyword custom->keyword
          s/Int   custom->long
          clojure.lang.Keyword custom->keyword}))

(defn datomic-coercion-matcher
  [schema]
  (or (custom-coercions schema)
      (c/keyword-enum-matcher schema)
      (c/set-matcher schema)))

(defn update-schema [base]
  (merge base {(s/required-key :db/id) s/Int}))

(def User
  {(s/optional-key :model/id) s/Uuid
   (s/optional-key :model/type) s/Keyword
   (s/optional-key :user/firstname) s/Str
   (s/optional-key :user/lastname) s/Str
   (s/optional-key :user/email) s/Str})

(def new-user (c/coercer User c/json-coercion-matcher))

(def update-user (c/coercer (update-schema User) c/json-coercion-matcher))

(def export-user (c/coercer (update-schema User) datomic-coercion-matcher))

(def Todo
  {(s/optional-key :model/id) s/Uuid
   (s/optional-key :model/type) s/Keyword
   (s/optional-key :todo/title) s/Str
   (s/optional-key :todo/status) s/Keyword
   (s/optional-key :todo/user) s/Int})


(def new-todo (c/coercer Todo c/json-coercion-matcher))

(def update-todo (c/coercer (update-schema Todo) c/json-coercion-matcher))

(def export-todo (c/coercer (update-schema Todo) datomic-coercion-matcher))


(defmulti export-entity (fn [e] (:model/type e)))

(defmethod export-entity :user [e] (export-user e))

(defmethod export-entity :todo [e] (export-todo e))

(defmethod export-entity :default [e] e)
