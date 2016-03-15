(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [reloaded.repl
             :refer [go init reset reset-all start stop system]]
            [datomic-talk.system :as app]))


(reloaded.repl/set-init! app/dev-system)
