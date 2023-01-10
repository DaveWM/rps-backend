(defproject rps-backend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  ;; you need a username and password to fetch the datomic-pro dependency,
  ;; sign up here and follow the instructions: https://www.datomic.com/get-datomic.html
  :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username [:gpg :env/datomic_username]
                                   :password [:gpg :env/datomic_password]}}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.datomic/datomic-pro "0.9.6045"]
                 [http-kit "2.6.0"]
                 [compojure "1.7.0"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-json "0.5.1"]
                 [jumblerg/ring-cors "3.0.0"]
                 [com.taoensso/sente "1.17.0"]
                 [mount "0.1.16"]
                 [medley "1.4.0"]
                 [com.github.kkuegler/human-readable-ids-java "0.1.1"]
                 [environ "1.2.0"]
                 [org.postgresql/postgresql "42.5.0"]]
  :repl-options {:init-ns rps-backend.core}
  :profiles {:uberjar {:aot :all}}
  :uberjar-name "rps-standalone.jar"
  :min-lein-version "2.0.0"
  :main rps-backend.core)
