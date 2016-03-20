(ns datomic-talk.merge
  (:require [datomic.api :as d]
            [clojure.set :as s]))


;;-------------------------------------------
;; Merging
;;-------------------------------------------
;; Recursively merge relying on the attribute
;; definitions to determine the appropriate
;; action to take.  Duplicate tx additions are
;; OK as they will get deduplicated in the
;; peers reconciler, e.g. if you add an attribute
;; that already exists with the same value, it will
;; get transacted by the peer.

(defn retract-val?
  "Use empty string as sentinel value for delete.
  May want to consider nil, here...seems more idiomatic "
    [v]
    (nil? v))

(declare merge-tx)

;; merge attribute based on cardinality
(defmulti merge-attr
  (fn [db id attr-meta old new] (:cardinality attr-meta)))

;; merge value for :db.cardinality/on based on
;; whether it's a component or not
(defmulti merge-value
  (fn [db id attr-meta old new] (:is-component attr-meta)))

;; Either add or retract the value
;; in the case of :db.cardinality/one
;; and ! :is-component
(defmethod merge-value false
  [db id attr-meta old new]
  (when new
    (let [attr (:ident attr-meta)]
      (cond
        (and old new (retract-val? new)) [[:db/retract id attr old]]
        (not (retract-val? new)) [[:db/add id attr new]]
        :default nil))))

;; Either retract the component entity
;; or recursivly handle the child entity
(defmethod merge-value true
  [db id attr-meta old new]
  (when new
    (cond
      (and old new  (retract-val? new)) [[:db.fn/retractEntity (:db/id old)]]
      (and old new) (merge-tx db old new)
      (not (retract-val? new)) [[:db/add id (:ident attr-meta) new]]
      :default nil)))


;; dispatch for :db.cardinality/one (singletons)
(defmethod merge-attr :db.cardinality/one
  [db id attr-meta old new]
  (when new
    (merge-value db id attr-meta old new)))


;; multi-fn for comonent and non-component collections
(defmulti merge-collection
  (fn [db _ attr-meta & args] (:is-component attr-meta)))


(defn coll-tx
  "Create a vector of transaction parts given
    a db/id a fn, atrribute name, and list of values"
  [id db-fn attr vals]
  (vec (map (fn [v] [db-fn id attr v]) vals)))

;; Short circuit on non-enum KW values w/ db/id
(defn extract-ref [val]
  (some identity ((juxt :db/id identity) val)))


(defmulti old-keys
  (fn [attr-meta & args] (:value-type attr-meta)))

(defmethod old-keys :db.type/ref [attr-meta vals]
  (set (map extract-ref vals)))

(defmethod old-keys :default [attr-meta vals]
  (set vals))

;; Merge non-component collections using set semantics
;; * retractions are the set-difference (old - new)
;; * additions are the set-difference (new-old)
;; * the set intersection (new | old) requires no action on our part
(defmethod merge-collection false [db id attr-meta old new]
  (cond
    (retract-val? new)
    (let [retractions (old-keys attr-meta old)
          tx-part (coll-tx id :db/retract (:ident attr-meta) retractions)]
      (when (not (empty? tx-part))
        tx-part))
    new
    (let [set-old (old-keys attr-meta old)
          set-new (set new)
          retractions (s/difference set-old set-new)
          additions (s/difference set-new set-old)
          tx-part (vec (concat
                        (coll-tx id :db/retract (:ident attr-meta) retractions)
                        (coll-tx id :db/add (:ident attr-meta) additions)))]
      (when (not (empty? tx-part))
        tx-part))
    :default nil))



(defn retract-entity-tx
  "Given a list of db/ids, creates a vector of retraction statements"
  [ids]
  (vec (map (fn [id] [:db.fn/retractEntity id]) ids)))

(defn new-components
  "Create a map of the entity w/ new comoponents.
  Datomic will auto-assign db/ids to each one in the
    collection"
  [id attr components]
  (when (not (empty? components))
    [{:db/id id
      attr components}]))

;; Use the same set semantics as non-component collections
;; by pulling the db/id of the child components.  Additions
;; will be in the 'nil' collection after grouping new by
;; db/id.  Retract the child entites, add the new entites,
;; and recursivly merge the existing entities.
(defmethod merge-collection true [db id attr-meta old new]
  (cond
    (retract-val? new)
    (let [old-mapped (group-by :db/id old)
          old-ks (set (keys old-mapped))
          tx-part (retract-entity-tx old-ks)]
      (when (not (empty? tx-part))
        tx-part))
    new
    (let [old-mapped (group-by :db/id old)
          new-mapped (group-by :db/id new)
          old-ks (set (keys old-mapped))
          new-ks (set (keys new-mapped))
          updates (s/intersection old-ks new-ks)
          tl-retractions (s/difference old-ks new-ks)
          tl-retractions-tx (retract-entity-tx tl-retractions)
          tl-additions (new-components id (:ident attr-meta) (get new-mapped nil))
          tx-part (concat tl-retractions-tx
                          tl-additions
                          (vec (mapcat #(merge-tx db
                                                  (first (get old-mapped %))
                                                  (first (get new-mapped %)))
                                       updates)))]
      (when (not (empty? tx-part))
        tx-part))
    :default nil))


;; Top level merge for collections
(defmethod merge-attr :db.cardinality/many
  [db id attr-meta old new]
  (when new
    (merge-collection db id attr-meta old new)))


;; Top level merge function for updating existing
;; entities. We grab the cursor for the old entity
;; here to ensure we can reach all points in the
;; recursive merge. old MUST have a valid db/id.
(defn merge-tx [db old new]
  {:pre [(:db/id old)]}
  (let [entity (d/entity db (:db/id old)) ;;ensure we have the whole entity
        ks (keys (dissoc new :db/id))
        merged (mapcat
                (fn [k]
                  (merge-attr db (:db/id old) (d/attribute db k)
                              (get entity k) (get new k))) ks)]
    (vec (filter identity merged))))
