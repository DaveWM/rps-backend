(ns rps-backend.db
  (:require [datomic.api :as d]
            [mount.core :refer [defstate]]
            [rps-backend.config :as config]))

(def schema
  [{:db/ident       :game/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}
   {:db/ident       :game/state
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :game/current-round
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :game/players
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}
   {:db/ident       :game/winner
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident       :round/index
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one}
   {:db/ident       :round/state
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :round/game
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :round/players
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident :state/playing}
   {:db/ident :state/complete}

   {:db/ident       :player-choice/round
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :player-choice/player
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :player-choice/choice
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident       :player-choice/round+player
    :db/valueType   :db.type/tuple
    :db/tupleAttrs  [:player-choice/round :player-choice/player]
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}

   {:db/ident :choice/rock}
   {:db/ident :choice/paper}
   {:db/ident :choice/scissors}

   {:db/ident       :player/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident       :player/token
    :db/valueType   :db.type/string
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one}])

(defstate conn
  :start (do
           (d/create-database config/db-uri)
           (let [new-conn (d/connect config/db-uri)]
             @(d/transact new-conn schema)
             new-conn)))

(defn tx-queue []
  (d/tx-report-queue conn))

(defn get-db []
  (d/db conn))

(defn transact! [txs]
  @(d/transact conn txs))