(ns rps-backend.core
  (:require [datomic.api :as d]
            [jumblerg.middleware.cors]
            [mount.core :refer [defstate]]
            [ring.middleware.json]
            [ring.middleware.keyword-params]
            [ring.middleware.params]
            [ring.middleware.session]
            [rps-backend.db :as db]
            [rps-backend.subscriptions :as subs]
            [rps-backend.server :as server]
            [taoensso.sente :as sente])
  (:gen-class))

(declare sub->users*)

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :event/subscribe [{sub :?data reply-fn :?reply-fn user-id :uid :as event}]
  (when (subs/authorised? sub user-id)
    (let [db (subs/init-sub! sub user-id (db/get-db))
          result (subs/fetch-sub sub db)]
      (swap! sub->users* update sub (comp set conj) user-id)
      (when reply-fn
        (reply-fn {:data (subs/format-for-user sub result user-id)
                   :sub  sub})))))

(defmethod event-msg-handler :event/unsubscribe [{sub-pattern :?data user-id :uid :as event}]
  (swap! sub->users* (fn [sub->users]
                       (->> sub->users
                            (map (fn [[sub users]]
                                   [sub
                                    (if (= sub sub-pattern)
                                      (disj users user-id)
                                      users)]))
                            (into {})))))

(defmethod event-msg-handler :chsk/ws-ping [{user-id :uid :as event}]
  (server/chsk-send! user-id [:channel/ws-pong]))

(defmethod event-msg-handler :chsk/uidport-close [{user-id :uid}]
  (swap! sub->users*
         (fn [sub->users]
           (->> sub->users
                (keep (fn [[sub users]]
                        (when-let [new-users (->> users (remove #{user-id}) seq)]
                          [sub (set new-users)])))
                (into {})))))

(defmethod event-msg-handler :game/select-choice [{user-id :uid {:keys [game-name choice]} :?data reply-fn :?reply-fn}]
  (let [db (db/get-db)
        game (d/entity db [:game/name game-name])
        existing-choice (d/q '[:find (pull ?e [*]) .
                               :where
                               [?u :player/token ?token]
                               [?e :player-choice/player ?u]
                               [?e :player-choice/round ?current-round]
                               :in $ ?token ?current-round]
                             db user-id (get-in game [:game/current-round :db/id]))
        {db :db-after} (db/transact! [{:db/id                (or (:db/id existing-choice) "new-choice")
                                       :player-choice/choice choice
                                       :player-choice/player [:player/token user-id]
                                       :player-choice/round  (get-in game [:game/current-round :db/id])}])

        player->choice (->> (d/q '[:find ?p ?choice
                                   :keys player choice
                                   :where
                                   [?g :game/name ?game]
                                   [?g :game/current-round ?round]
                                   [?round :round/players ?p]
                                   [?pc :player-choice/round ?round]
                                   [?pc :player-choice/choice ?c]
                                   [?pc :player-choice/player ?p]
                                   [?c :db/ident ?choice]
                                   :in $ ?game]
                                 db game-name)
                            (map (juxt :player :choice))
                            (into {}))
        current-round (:game/current-round game)
        current-round-players (:round/players current-round)]
    (when (and (< 1 (count current-round-players))
               (= (count player->choice) (count current-round-players))) ;; all players made a choice
      (when-let [winners (subs/get-winners player->choice)] ;; there are winners
        (db/transact! [[:db/add (:db/id (:game/current-round game)) :round/state :state/complete]])
        (if (= 1 (count winners))
          (db/transact! [[:db/add [:game/name game-name] :game/winner (first winners)]
                         [:db/add [:game/name game-name] :game/state :state/complete]])
          (db/transact! [{:db/id         "new-round"
                          :round/game    [:game/name game-name]
                          :round/state   :state/playing
                          :round/players winners
                          :round/index   (inc (:round/index current-round))}
                         [:db/add [:game/name game-name] :game/current-round "new-round"]]))))))

(defmethod event-msg-handler :channel/ws-pong [event]
  nil)

(defmethod event-msg-handler :default [event]
  (println "Unknown event: " (prn-str event))
  nil)

(defstate socket-server-router
  :start (sente/start-server-chsk-router! (:ch-recv server/socket-server) event-msg-handler)
  :stop (socket-server-router))

(defstate sub->users*
  :start (atom {}))

(defstate tx-watcher
  :start (doto
           (Thread.
             ^Runnable
             (fn []
               (println "STARTING")
               (try
                 (while (not (Thread/interrupted))
                   (println "WAITING...")
                   (try
                     (let [{:keys [db-after tx-data] :as evt} (.take (db/tx-queue))
                           sub->users @sub->users*
                           subs-to-update (subs/affected-subs (keys sub->users) evt tx-data)]
                       (doseq [sub subs-to-update]
                         (let [users (sub->users sub)
                               result (subs/fetch-sub sub db-after)]
                           (doseq [user users]
                             (println "Pushing to " user)
                             (server/chsk-send! user [:rps.server/push {:data (subs/format-for-user sub result user)
                                                                        :sub  sub}])))))
                     (catch InterruptedException e
                       (throw e))
                     (catch Exception e
                       (println "Caught error in tx watcher thread: " e))))
                 (catch InterruptedException e
                   (println "Thread interrupted")))))
           (.start))
  :stop (.interrupt tx-watcher))


(defn -main [& args]
  (mount.core/start))


(comment

  (mount.core/start)
  (mount.core/stop)

  @(d/transact
     conn
     [{:db/id      123
       :game/state :state/playing}])

  @(d/transact
     conn
     [{:player-choice/player 17592186045435
       :player-choice/choice :choice/paper
       :player-choice/round  17592186045424}])

  )
