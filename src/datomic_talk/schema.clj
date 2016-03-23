(ns datomic-talk.schema
  (:require [datomic.api :as d]
            [datomic-talk.merge :as merge]
            [schema.core :as s]
            [schema.coerce :as c]
            [schema.macros :as macros]
            [schema.spec.core :as spec]
            [schema.utils :as utils]))


(defn enum?
  "Enums are pulled as :db/ident only by convention,
   is every key :db/ident?"
  [v]
  (and (map? v) (every? #{:db/ident} (keys v))))

(defn ref?
  "References are :db/id only by default, is every
   key :db/id?"
  [v]
  (and (map? v) (every? #{:db/id} (keys v))))

(defn custom->keyword
  "enums should be keywords when exported, as they would be passed in.
   add check for enum when casting to keyword"
  [v]
  (if (enum? v)
    (:db/ident v)
    (c/string->keyword v)))

(defn custom->long
  "References should be longs when exported,
   add check for ref? to Int transforms"
  [v]
  (if (ref? v)
    (:db/id v)
    (c/safe-long-cast v)))


(def custom-coercions
  "Merge our custom coercions with the supplied json
   coercions from schema"
  (merge c/+json-coercions+
         {s/Keyword custom->keyword
          s/Int   custom->long
          clojure.lang.Keyword custom->keyword}))

(defn datomic-coercion-matcher
  [schema]
  (or (custom-coercions schema)
      (c/keyword-enum-matcher schema)
      (c/set-matcher schema)))

(defn update-schema
  "Updates require a :db/id"
  [base]
  (merge base {(s/required-key :db/id) s/Int}))

;;Schema definition

(def Model
  {(s/optional-key :model/id) s/Uuid
   (s/required-key :model/type) s/Keyword})

(def User
  (merge Model
         {(s/optional-key :user/firstname) s/Str
          (s/optional-key :user/lastname) s/Str
          (s/optional-key :user/email) s/Str}))

(def Todo
  (merge Model
         {(s/optional-key :todo/title) s/Str
          (s/optional-key :todo/status) s/Keyword
          (s/optional-key :todo/user) s/Int}))


;; Coercions

(def new-user (c/coercer User c/json-coercion-matcher))

(def update-user (c/coercer (update-schema User) c/json-coercion-matcher))

(def export-user (c/coercer (update-schema User) datomic-coercion-matcher))

(def new-todo (c/coercer Todo c/json-coercion-matcher))

(def update-todo (c/coercer (update-schema Todo) c/json-coercion-matcher))

(def export-todo (c/coercer (update-schema Todo) datomic-coercion-matcher))


;; Top level multimethods for handling entity I/O

(defmulti export-entity (fn [e] (:model/type e)))

(defmethod export-entity :user [e] (export-user e))

(defmethod export-entity :todo [e] (export-todo e))

(defmethod export-entity :default [e] e)

(defmulti new-entity (fn [e] (keyword (:model/type e))))

(defmulti update-entity (fn [e] (keyword (:model/type e))))

(defn base-attrs [e]
  (assoc e :db/id (d/tempid :db.part/user) :model/id (d/squuid)))

(defmethod new-entity :user [e]
  (base-attrs (new-user e)))

(defmethod update-entity :user [e]
  (update-user e))

(defmethod new-entity :todo [e]
  (base-attrs (new-todo e)))

(defmethod update-entity :todo [e]
  (update-todo e))
