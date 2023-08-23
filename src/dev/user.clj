(ns user
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as server]))

(defn shadow-repl
  "Connects to a given build-id. Defaults to `:app`."
  ([]
   (shadow-repl :app))
  ([build-id]
   (server/start!)
   (shadow/watch build-id)
   (shadow/nrepl-select build-id)))
