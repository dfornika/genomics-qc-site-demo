(ns user
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as server]
            [portal.api :as p]))

(defn cljs-repl
  "Connects to a given build-id. Defaults to `:app`."
  ([]
   (cljs-repl :app))
  ([build-id]
   (server/start!)
   (shadow/watch build-id)
   (shadow/nrepl-select build-id)))

(comment 
  (cljs-repl)
  @genomics-qc.core/db
  ,)

