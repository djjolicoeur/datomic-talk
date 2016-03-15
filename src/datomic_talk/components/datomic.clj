(ns datomic-talk.components.datomic
  (:require [com.stuartsierra.component :as component]
            [clojure.walk :refer [postwalk]]
            [datomic.api :as d]
            [clojure.java.io :as io]
            [io.pedestal.log :refer [debug info warn error]]))



;; Implement pprint methods
(defn- use-method
  [^clojure.lang.MultiFn multifn dispatch-val func]
  (. multifn addMethod dispatch-val func))


;;-----------------------------
;; DB bootstrapping and setup FNs
;;-----------------------------
;;  Bring up the DB and load the schema.
;; if we are in dev or test, load up the dev facts


(defn bootstrap [conn schema-edn-file]
  (when-let [schema (io/reader schema-edn-file)]
    @(d/transact conn (datomic.Util/readAll schema))))

(defn load-facts [conn facts-edn-file]
  (when-let [facts (io/reader facts-edn-file)]
    @(d/transact conn (datomic.Util/readAll facts))))


;;------------------------------------------------------
;; DatomicDev
;;------------------------------------------------------
;;
;; For Dev and Testing environments.  Creates a fresh datomic
;; database that gets deleted on sys stop.
(defrecord DatomicDev [datomic-uri schema dev-facts conn]
  component/Lifecycle
  (start [this]
    (info "Starting Datomic")
    (if conn
      this
      (let [datomic-uri (str datomic-uri "-" (java.util.UUID/randomUUID))
            db (d/create-database datomic-uri)
            conn (d/connect datomic-uri)]
        (info (format "Loading Schema from %s" schema))
        (bootstrap conn schema)
        (info (format "Loading Facts from %s" dev-facts))
        (load-facts conn dev-facts)
        (assoc this :conn conn :datomic-uri datomic-uri))))
  (stop [this]
    (if conn
      (do
        (info "Stopping Datomic")
        (info (format "Deleting Datomic db %s" datomic-uri))
        (d/delete-database (:datomic-uri this))
        (info "Stopped Datomic" nil)
        (assoc this :conn nil :datomic-uri nil))
      this))
  clojure.lang.IDeref
  (deref [this] (d/db conn)))


(defmethod print-method DatomicDev [v ^java.io.Writer w]
  (.write w (str "#<DatomicDev"
                 {:datomic-uri (:datomic-uri v) :conn (:conn v)} ">")))

(defmethod print-dup DatomicDev [v w]
  (print-method v w))

(use-method clojure.pprint/simple-dispatch DatomicDev pr)

(defn new-dev-datomic
  ([]
   (map->DatomicDev {}))
  ([datomic-uri]
   (map->DatomicDev {:datomic-uri datomic-uri})))

;;-----------------------------------------------------
;; DatomicConnection
;;-----------------------------------------------------
;;
;; For when you just want a connection and the bootsrtapping
;; and maintanence of the datomic DB is being handled elsewhere
;; i.e. by another system
(defrecord DatomicConnection [datomic-uri conn]
  component/Lifecycle
  (start [this]
    (info "Starting Datomic")
    (if conn
      this
      (let [db (d/create-database datomic-uri)
            conn (d/connect datomic-uri)]
        (assoc this :conn conn :datomic-uri datomic-uri))))
  (stop [this]
    (if-not conn this
            (assoc this :conn nil)))
  clojure.lang.IDeref
  (deref [this] (d/db conn)))

(defn new-datomic-conn
  ([]
   (map->DatomicConnection {}))
  ([datomic-uri]
   (map->DatomicConnection {:datomic-uri datomic-uri})))

(defmethod print-method DatomicConnection [v ^java.io.Writer w]
  (.write w (str "#<DatomicConnection"
                 {:datomic-uri (:datomic-uri v) :conn (:conn v)} ">")))

(defmethod print-dup DatomicConnection [v w]
  (print-method v w))

(use-method clojure.pprint/simple-dispatch DatomicConnection pr)


;;-----------------------------------------------------
;; Datomic
;;----------------------------------------------------
;;
;; Datomic Component.  Loads schema, if the db is a new
;; DB it loads the contents of base-facts, and creates
;; a connection

(defrecord Datomic [datomic-uri schema base-facts conn]
  component/Lifecycle
  (start [this]
    (info "Starting Datomic")
    (if conn
      this
      (let [db (d/create-database datomic-uri)
            conn (d/connect datomic-uri)]
        (info (format "Loading Schema from %s" schema))
        (bootstrap conn schema)
        (when db
          (info (format "Loading Base Facts from %s" base-facts))
          (load-facts conn base-facts))
        (assoc this :conn conn :datomic-uri datomic-uri))))
  (stop [this]
    (if-not conn this
            (assoc this :conn nil :datomic-uri nil)))
  clojure.lang.IDeref
  (deref [this] (d/db conn)))

(defn new-datomic
  ([]
   (map->Datomic {}))
  ([datomic-uri]
   (map->Datomic {:datomic-uri datomic-uri})))

(defmethod print-method Datomic [v ^java.io.Writer w]
  (.write w (str "#<Datomic"
                 {:datomic-uri (:datomic-uri v) :conn (:conn v)}
                 ">")))

(defmethod print-dup Datomic [v w]
  (print-method v w))

(use-method clojure.pprint/simple-dispatch Datomic pr)
