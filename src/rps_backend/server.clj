(ns rps-backend.server
  (:require [mount.core :refer [defstate]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
            [org.httpkit.server :as http]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [rps-backend.config :as config]
            [jumblerg.middleware.cors :as cors]))

(defstate socket-server
          :start (sente/make-channel-socket-server!
                   (get-sch-adapter)
                   {:csrf-token-fn nil
                    :user-id-fn    (fn [req] (get-in req [:params :user]))}))

(defn chsk-send! [user evt]
  ((:send-fn socket-server) user evt))                               ; ChannelSocket's send API fn
;(def connected-uids (:connected-uids socket-server))        ; Watchable, read-only atom

(defroutes routes
           (GET "/" [] {:alive true})
           (GET "/chsk" req ((:ajax-get-or-ws-handshake-fn socket-server) req))
           (POST "/chsk" req ((:ajax-post-fn socket-server) req))
           (route/not-found {:error "Route not found"}))

(def app
  (-> routes
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params
      ring.middleware.json/wrap-json-body
      ring.middleware.json/wrap-json-params
      ring.middleware.json/wrap-json-response
      (cors/wrap-cors #".*")))

(defstate server
          :start (http/run-server app {:port config/port})
          :stop (server))