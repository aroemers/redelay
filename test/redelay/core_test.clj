(ns redelay.core-test
  (:require [redelay.core :refer [state status stop state? defstate]]
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
      "My docstring"
      {:extra :attribute}
      :start (dec @bar) (inc @bar)
      :stop  (deliver stopped this)
      :meta  {:dev true})

    (is (false? (realized? foo)))
    (is (state? foo))
    (is (= () (status)))

    (is (= 3 @baz))
    (is (realized? foo))
    (is (= [foo bar baz] (status)))

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

    (is (= {:doc "My docstring", :private true, :extra :attribute}
           (select-keys (meta #'baz) [:doc :private :extra])))
    (is (= {:dev true} (meta baz)))))

(deftest low-level-test
  (let [just-start (state* {:start-fn (fn [] true)})]
    (is @just-start)
    (stop)))
