(ns rps-backend.config
  (:require [environ.core :refer [env]]))

(def port
  (Integer. (env :port 8082)))

(def db-uri
  (env :db-uri "datomic:mem://rps"))