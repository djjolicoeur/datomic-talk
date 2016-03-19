(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [datomic-talk.system :as app]
            [reloaded.repl
             :refer [go init reset reset-all start stop system]]))


(reloaded.repl/set-init! app/dev-system)
