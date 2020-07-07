(ns redelay.core
  "Core API for creating and managing first class state objects."
  {:clojure.tools.namespace.repl/load   false
   :clojure.tools.namespace.repl/unload false}
  (:require [clojure.pprint :refer [simple-dispatch]]))

;;; Watchpoint.

(defonce ^{:doc "Add watches to this var to be notified of
  new (started) or old (closed) states."}
  watchpoint (var watchpoint))

;;; State object.

(defonce ^:private unrealized `Unrealized)

(deftype State [name start-fn stop-fn value meta]
  clojure.lang.IDeref
  (deref [this]
    (when-not (realized? this)
      (locking this
        (when-not (realized? this)
          (let [result (start-fn)]
            (reset! value result)
            (.notifyWatches watchpoint nil this)))))
    @value)

  clojure.lang.IPending
  (isRealized [this]
    (not= @value unrealized))

  java.io.Closeable
  (close [this]
    (locking this
      (when (realized? this)
        (stop-fn @value)
        (reset! value unrealized)
        (.notifyWatches watchpoint this nil))))

  clojure.lang.Named
  (getNamespace [_]
    (namespace name))
  (getName [_]
    (clojure.core/name name))

  clojure.lang.IMeta
  (meta [_]
    meta)

  clojure.lang.IObj
  (withMeta [this meta]
    (State. name start-fn stop-fn value meta))

  Object
  (toString [this]
    (let [addr (Integer/toHexString (System/identityHashCode this))
          val  (if (realized? this)
                 (if (some? @value)
                   (str @value)
                   "nil")
                 :not-delivered)]
      (str "#<State@" addr "[" name "]: " val ">"))))

(defmethod print-method State [state ^java.io.Writer writer]
  (.write writer (str state)))

(defmethod simple-dispatch State [state]
  (.write *out* (str state)))

(defn- name-with-exprs [name [arg1 arg2 & argx :as args]]
  (let [[attrs args]
        (cond (and (string? arg1) (map? arg2)) [(assoc arg2 :doc arg1) argx]
              (string? arg1)                   [{:doc arg1} (cons arg2 argx)]
              (map? arg1)                      [arg1 (cons arg2 argx)]
              :otherwise                       [{} args])]
    [(with-meta name (merge (meta name) attrs {:defstate true})) args]))

(defn- qualified-exprs [qualifiers exprs]
  (loop [qualifiers (set qualifiers)
         exprs      exprs
         qualifier  nil
        qualified  {}]
    (if (seq exprs)
      (let [expr (first exprs)]
        (if-let [qualifier (qualifiers expr)]
          (recur (disj qualifiers qualifier) (rest exprs) qualifier qualified)
          (recur qualifiers (rest exprs) qualifier (update qualified qualifier (fnil conj []) expr))))
      qualified)))

;;; Public API

(defn  state*
  "Low-level function to create a State object. All keys are optional.
  The `:start-fn` value must be a 0-arity function. The `:stop-fn`
  value must be a 1-arity function. The `:meta` value must be a map."
  [{:keys [ns-str name-str start-fn stop-fn meta]
    :or   {start-fn (fn [])
           stop-fn  (fn [_])}}]
  (let [name (symbol ns-str (or name-str (str (gensym "state--"))))]
    (with-meta (State. name start-fn stop-fn (atom unrealized) nil)
      meta)))

(defmacro state
  "Create a state object, using the optional :start, :stop, :name
  and :meta expressions. The first forms are implicitly considered as
  the :start expression, if not qualified otherwise. Returned State
  object implements IDeref, IPending, Closeable, Named, IMeta and
  IObj."
  [& exprs]
  (let [{:keys [start stop] names :name metas :meta implicit-start nil}
        (qualified-exprs [:start :stop :name :meta] exprs)]
    (assert (not (and start implicit-start)) "start expression must be explicit or implicit")
    (assert (< (count names) 2) "name expression must be a single symbol")
    (assert (< (count metas) 2) "meta expression must be a single map")
    (let [start (or start implicit-start)
          name  (first names)
          meta  (first metas)]
      (assert (or (nil? name) (symbol? name)) "name must be symbol")
      `(state* {:ns-str   ~(if name (namespace name) (str *ns*))
                :name-str ~(when name (clojure.core/name name))
                :start-fn (fn [] ~@start)
                :stop-fn  (fn [~'this] ~@stop)
                :meta     ~meta}))))

(defn state?
  "Returns true if obj is a State object."
  [obj]
  (instance? State obj))

(defmacro defstate
  "Create a State object, using the optional :start, :stop and :meta
  expressions, and bind it to a var with the given name in the current
  namespace. Supports metadata on the name, a docstring and an
  attribute map."
  {:arglists '([name doc-string? attr-map? body])}
  [name & exprs]
  (let [[name exprs] (name-with-exprs name exprs)]
    `(def ~name
       (state ~@exprs :name ~(symbol (str *ns*) (str name))))))

;;; Default management.

(defonce ^:private closeables (java.util.LinkedHashSet.))

(defn- default-watch [_ state old new]
  (if new
    (.add closeables new)
    (.remove closeables old)))

(add-watch watchpoint ::default default-watch)

(defn status
  "Returns a list of active states, in realization order."
  []
  (apply list closeables))

(defn stop
  "Stop the active states in the reverse order they were realized."
  []
  (let [ordered (reverse (status))]
    (doseq [closeable ordered]
      (.close closeable))
    ordered))

;;; Enhance clojure.core/force

(defonce ^:private wrapped-force
  (alter-var-root #'clojure.core/force
   (fn [original]
     (fn [x]
       (if (state? x)
         (deref x)
         (original x))))))
