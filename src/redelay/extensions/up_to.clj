(ns redelay.extensions.up-to
  "A small extension that allows stopping a state and its dependents (in
  reverse order).

  Make sure this namespace is loaded before realizing states, in order
  to hook into the core's extension point (`watchpoint`). The internal
  dependency graph is determined by watching which states are referred
  to while starting a state."
  (:require [redelay.core :as core]))

;;; Internals

(defonce ^:private closeables (atom {}))
(defonce ^:private starting (atom ()))

(defn- watch [_ _ change state]
  (case change
    :referring (when-let [start (peek @starting)]
                 (swap! closeables update start (fnil conj (hash-set)) state))
    :starting  (swap! starting conj state)
    :aborted   (reset! starting ())
    :started   (swap! starting pop)
    :stopped   (swap! closeables dissoc state)
    nil))

(add-watch core/watchpoint ::up-to watch)

(defn- transitive [state]
  (let [dependencies @closeables]
    (loop [todo (list state) result (list)]
      (if-let [head (first todo)]
        (let [deps (keep (fn [[state deps]] (when (contains? deps head) state)) dependencies)]
          (recur (reduce conj (pop todo) deps)
                 (cons head result)))
        (distinct result)))))

;;; Extension API

(defn stop
  "Stop the state and its (realized) dependents, in reverse order."
  [state]
  (when (realized? state)
    (let [ordered (transitive state)]
      (doseq [closeable ordered]
        (.close closeable))
      ordered)))
