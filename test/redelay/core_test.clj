(ns redelay.core-test
  (:require [redelay.core :refer [state status stop state? defstate state* close!]]
            [clojure.test :as test :refer [deftest is]]))

(defn ensure-stop [f]
  (stop)
  (try
    (f)
    (finally
      (stop))))

(test/use-fixtures :each ensure-stop)

(deftest simple-test
  (let [foo     (state 1)
        bar     (state :start (inc @foo) :name bar)
        stopped (promise)]

    (defstate ^:private baz
      :start (dec @bar) (inc @bar)
      :stop  (deliver stopped this)
      :meta  {:dev true})

    (is (false? (realized? foo)))
    (is (state? foo))
    (is (= () (status)))
    (is (nil? (peek foo)))

    (is (= 3 @baz))
    (is (realized? foo))
    (is (= [foo bar baz] (status)))
    (is (= 3 (peek baz)))

    (is (= [baz bar foo] (stop)))
    (is (= 3 (deref stopped 0 :wrong)))
    (is (= () (status)))
    (is (false? (realized? foo)))

    (is (= 3 (force baz)))
    (stop)

    (is (= "redelay.core-test" (namespace foo)))
    (is (nil? (namespace bar)))
    (is (= "redelay.core-test" (namespace baz)))
    (is (= "bar" (name bar)))
    (is (= "baz" (name baz)))

    (is (= {:private true, :dynamic true, :defstate true}
           (select-keys (meta #'baz) [:private :dynamic :defstate])))
    (is (= {:dev true} (meta baz)))
    (is (= {:dev false} (alter-meta! baz update :dev not)))
    (is (= {:answer 42} (reset-meta! baz {:answer 42})))))

(deftest low-level-test
  (let [state (state* {:start-fn (fn [] true)})]
    (is @state)
    (stop))

  (let [stopped (promise)
        state   (state* {:start-fn (fn [] 42)
                         :stop-fn  (fn [this] (deliver stopped this))
                         :name     'my-name
                         :meta     {:low-level true}})]
    (is (= 42 @state))
    (stop)
    (is (= 42 @stopped))
    (is (nil? (namespace state)))
    (is (= "my-name" (name state)))
    (is (= {:low-level true} (meta state)))))

(deftest force-close-test
  (let [buggy-stop (state "hi" :stop (inc this))]
    (is (= @buggy-stop "hi"))
    (is (thrown? Exception (stop)))
    (close! buggy-stop)
    (is (= () (stop)))))
