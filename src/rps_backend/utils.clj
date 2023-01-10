(ns rps-backend.utils
  (:import (com.github.kkuegler PermutationBasedHumanReadableIdGenerator)))

(def id-gen (PermutationBasedHumanReadableIdGenerator.))

(defn human-readable-id []
  (.generate id-gen))