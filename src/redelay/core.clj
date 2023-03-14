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

(defprotocol ^:no-doc StateFunctions
  (force-close [this]))

(deftype State [name start-fn stop-fn value meta]
  clojure.lang.IDeref
  (deref [this]
    (when-not (realized? this)
      (locking this
        (when-not (realized? this)
          (try
            (let [result (start-fn)]
              (reset! value result)
              (.notifyWatches watchpoint nil this))
            (catch Exception e
              (throw (ex-info "Exception thrown when starting state" {:state this} e)))))))
    @value)

  clojure.lang.IPending
  (isRealized [_]
    (not= @value unrealized))

  java.io.Closeable
  (close [this]
    (locking this
      (when (realized? this)
        (try
          (stop-fn @value)
          (reset! value unrealized)
          (.notifyWatches watchpoint this nil)
          (catch Exception e
            (throw (ex-info "Exception thrown when closing state" {:state this} e)))))))

  clojure.lang.IPersistentStack
  (peek [this]
    (locking this
      (when (realized? this)
        @value)))

  StateFunctions
  (force-close [this]
    (reset! value unrealized)
    (.notifyWatches watchpoint this nil))

  clojure.lang.Named
  (getNamespace [_]
    (namespace name))
  (getName [_]
    (clojure.core/name name))

  clojure.lang.IMeta
  (meta [_]
    @meta)

  clojure.lang.IReference
  (alterMeta [_ f args]
    (apply swap! meta f args))
  (resetMeta [_ m]
    (reset! meta m))

  Object
  (toString [this]
    (let [addr (Integer/toHexString (System/identityHashCode this))
          val  (if (realized? this)
                 (binding [*print-length* 10]
                   (pr-str @value))
                 :unrealized)]
      (str "#<State@" addr "[" name "]: " val ">"))))

(defmethod print-method State [state ^java.io.Writer writer]
  (.write writer (str state)))
(defmethod simple-dispatch State [state]
  (.write *out* (str state)))

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

(declare state?)

(defn- skip-defstate? [ns name]
  (let [state (some-> (ns-resolve ns name) deref)]
    (and (state? state) (realized? state))))


;;; Public API

(defn state*
  "Low-level function to create a State object. All keys are optional.
  The `:start-fn` value must be a 0-arity function. The `:stop-fn`
  value must be a 1-arity function. The `:meta` value must be a map."
  [{:keys [name start-fn stop-fn meta]
    :or   {name     (gensym "state--")
           start-fn (fn [])
           stop-fn  (fn [_])}}]
  (assert (symbol? name) "value of :name must be a symbol")
  (assert (fn? start-fn) "value of :start-fn must be a function")
  (assert (fn? stop-fn) "value of :stop-fn must be a function")
  (assert (or (nil? meta) (map? meta)) "value of :meta must be a map")
  (State. name start-fn stop-fn (atom unrealized) (atom meta)))

(defmacro state
  "Create a state object, using the optional :start, :stop, :name
  and :meta expressions. The first forms are implicitly considered as
  the :start expression, if not qualified otherwise.

  Returned State object implements IDeref (`deref`),
  IPending (`realized?`), Closeable (`.close`),
  IPersistentStack (`peek`), Named (`name`, `namespace`),
  IMeta (`meta`) and IReference (`alter-meta!`, `reset-meta!`)."
  [& exprs]
  (let [{:keys [start stop] names :name metas :meta implicit-start nil}
        (qualified-exprs [:start :stop :name :meta] exprs)]
    (assert (not (and start implicit-start)) "start expression must be explicit or implicit")
    (assert (< (count names) 2) "name expression must be a single symbol")
    (assert (< (count metas) 2) "meta expression must be a single map")
    (let [start        (or start implicit-start)
          name         (first names)
          meta         (first metas)
          default-name (symbol (str *ns*) (str (gensym "state--")))]
      (assert (or (nil? name) (symbol? name)) "name must be symbol")
      `(state* {:name     '~(or name default-name)
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
  namespace. Trying to redefine an active (i.e. realized) defstate
  is skipped."
  {:arglists '([name doc-string? attr-map? body])}
  [name & exprs]
  (if (skip-defstate? *ns* name)
    (binding [*out* *err*]
      (println "WARNING: skipping redefinition of active defstate" name))
    (let [default-meta   {:dynamic true, :defstate true}
          name-with-meta (with-meta name (merge default-meta (meta name)))
          qualified-name (symbol (str *ns*) (str name))]
      `(def ~name-with-meta
         (state ~@exprs :name ~qualified-name)))))

(defn close!
  "Close the State, skipping the stop logic."
  [state]
  (force-close state))


;;; Default management.

(defonce ^:private closeables (java.util.LinkedHashSet.))

(defn- default-watch [_ _ old new]
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

(alter-var-root #'clojure.core/force
  (fn [original]
    (fn [x]
      (if (state? x)
        (deref x)
        (original x)))))
