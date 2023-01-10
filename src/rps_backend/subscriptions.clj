(ns rps-backend.subscriptions
  (:require [rps-backend.db :as db]
            [rps-backend.db :as db]
            [rps-backend.db :as db]
            [rps-backend.utils :as utils]
            [datomic.api :as d]))

(defn get-winners [player->choice]
  (let [distinct-choices (set (vals player->choice))]
    (if (= 2 (count distinct-choices))
      (let [winning-choice (case distinct-choices
                             #{:choice/rock :choice/paper} :choice/paper
                             #{:choice/paper :choice/scissors} :choice/scissors
                             #{:choice/scissors :choice/rock} :choice/rock)]
        (->> player->choice
             (keep (fn [[player choice]]
                     (when (= choice winning-choice)
                       player)))))
      (keys player->choice))))

(defmulti fetch-sub (fn [[sub-type] _] sub-type))

(defmethod fetch-sub :game [[_ game-name] db]
  (let [player-pattern [:player/token :player/name]
        round-pattern [{:round/players player-pattern}
                       {:round/state [:db/ident]}
                       :round/index
                       {:player-choice/_round [{:player-choice/choice [:db/ident]}
                                               {:player-choice/player player-pattern}]}]]
    (d/pull db
            [:game/name
             {:game/winner player-pattern}
             {:game/state [:db/ident]}
             {:game/current-round round-pattern}
             {:game/players player-pattern}
             {:round/_game round-pattern}]
            [:game/name game-name])))

(defmethod fetch-sub :player [[_ user-id] db]
  (d/pull db
          [:player/name]
          [:player/token user-id]))

(defmulti init-sub! (fn [[sub-type] _ _] sub-type))

(defmethod init-sub! :game [[_ game-name] user-id db]
  (let [game (d/entity db [:game/name game-name])
        player (d/entity db [:player/token user-id])
        player-id (or (:db/id player) "player")
        txs (concat
              (when (nil? player)
                [{:db/id        "player"
                  :player/token user-id
                  :player/name  (utils/human-readable-id)}])
              (if (empty? game)
                [{:db/id              "new-game"
                  :game/name          game-name
                  :game/state         :state/playing
                  :game/current-round "current-round"
                  :game/players       [player-id]}
                 {:db/id         "current-round"
                  :round/index   0
                  :round/state   :state/playing
                  :round/players [player-id]
                  :round/game    "new-game"}]

                [[:db/add [:game/name game-name] :game/players player-id]
                 [:db/add (get-in game [:game/current-round :db/id]) :round/players player-id]]))]
    (:db-after (db/transact! txs))))

(defmethod init-sub! :player [[_ user-id] _ db]
  (let [player (d/entity db [:player/token user-id])
        txs (when (nil? player)
              [{:db/id        "player"
                :player/token user-id
                :player/name  (utils/human-readable-id)}])]
    (:db-after (db/transact! txs))))

(defmulti authorised? (fn [[sub-type] user-id] sub-type))

(defmethod authorised? :player [[_ player-id] user-id]
  (= player-id user-id))

(defmethod authorised? :default [_ _]
  true)

(defn format-response [m]
  (->> m
       (clojure.walk/prewalk (fn [x]
                               (cond
                                 (:db/ident x) (:db/ident x)
                                 (keyword? x) (keyword (name x))
                                 (and (map-entry? x) (= :player/token (key x))) [:player/token-hash (hash (val x))]
                                 :else x)))))

(defmulti format-for-user (fn [[sub-type] _ _] sub-type))

(defmethod format-for-user :game [_ game user-id]
  (let [current-round-index (get-in game [:game/current-round :round/index])]
    (-> {:name                 (:game/name game)
         :winner               (:game/winner game)
         :players              (->> game
                                    :game/players)
         :state                (get-in game [:game/state :db/ident])
         :current-round-index  current-round-index
         :previous-rounds      (->> (:round/_game game)
                                    (remove (fn [round]
                                              (when-not (:game/winner game)
                                                (= (:round/index round) current-round-index)))))
         :user-playing?        (contains? (->> game
                                               :game/current-round
                                               :round/players
                                               (map :player/token)
                                               set)
                                          user-id)
         :current-round-choice (->> (:round/_game game)
                                    (filter #(= (:round/index %) current-round-index))
                                    first
                                    :player-choice/_round
                                    (filter #(= user-id (get-in % [:player-choice/player :player/token])))
                                    first
                                    :player-choice/choice)}
        (format-response))))

(defmethod format-for-user :player [_ player user-id]
  {:name (:player/name player)})


(defn get-tx-game-name [{:keys [db-before db-after]} tx]
  (let [db (if (.added tx) db-after db-before)
        e (d/entity db (:e tx))]
    (or (:game/name e)
        (get-in e [:round/game :game/name])
        (get-in e [:player-choice/round :round/game :game/name]))))


(defmulti affected-subs-of-type (fn [[sub-type] evt txs]
                               sub-type))

(defmethod affected-subs-of-type :game [[_ subs] evt txs]
  (let [game-name (->> txs
                       (keep #(get-tx-game-name evt %))
                       (first))]
    (when game-name
      (->> subs
           (filter (fn [[_ sub-game-name]] (= sub-game-name game-name)))))))

(defmethod affected-subs-of-type :player [[_ subs] evt txs]
  (let [db (:db-after evt)
        player (->> txs
                       (map #(d/entity db (:e %)))
                       (filter :player/name)
                       (first))]
    (when player
      (->> subs
           (filter (fn [[_ sub-player-id]]
                     (= (:player/token player) sub-player-id)))))))

(defn affected-subs [all-subs evt txs]
  (->> all-subs
       (group-by first)
       (mapcat #(affected-subs-of-type % evt txs))))