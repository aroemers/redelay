(ns redelay.core-test
  (:require [redelay.core :refer [state status stop state? defstate state* close! watchpoint]]
            [clojure.test :as test :refer [deftest is]]))

(defn ensure-stop [f]
  (stop)
  (try
    (f)
    (finally
      (stop))))

(test/use-fixtures :each ensure-stop)

(defn submap? [a b]
  (= a (select-keys b (keys a))))

(deftest simple-test
  (let [foo     (state 1)
        bar     (state :start (inc @foo) :name bar)
        stopped (promise)]

    (defstate ^:private baz "docstring" {:extra "attr"}
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

    (is (submap? {:private  true
                  :dynamic  true
                  :defstate true
                  :doc      "docstring"
                  :extra    "attr"}
                 (meta #'baz)))
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

(deftest watchpoint-test
  (let [two           (state 2)
        forty-two     (state (+ 40 @two))
        notifications (atom [])]
    (add-watch watchpoint ::test #(swap! notifications conj %&))
    (try
      (is (= 42 @forty-two))
      (stop)
      (is (= [[::test watchpoint :starting forty-two]
              [::test watchpoint :starting two]
              [::test watchpoint :started two]
              [::test watchpoint :started forty-two]
              ;; stopping
              [::test watchpoint :stopping forty-two]
              [::test watchpoint :stopped forty-two]
              [::test watchpoint :stopping two]
              [::test watchpoint :stopped two]]
             @notifications))
      (finally
        (remove-watch watchpoint ::test)))))

(deftest defstate-doc-attr-test
  (defstate state-0)
  (defstate state-1 "val")
  (defstate state-2 "doc" "val")
  (defstate state-3 12345 "val")
  (defstate state-4 {:my :val})
  (defstate state-5 {:attr true} {:my :val})
  (defstate state-6 "doc" {:my :val})
  (defstate state-7 "doc" {:attr true} {:my :val})
  (defstate state-8 "val" :stop)
  (defstate state-9 "doc" {:attr true} :start "val")

  (is (= nil @state-0))

  (is (= "val" @state-1))
  (is (= nil (-> state-1 var meta :doc)))

  (is (= "val" @state-2))
  (is (= "doc" (-> state-2 var meta :doc)))

  (is (= "val" @state-3))
  (is (= nil (-> state-3 var meta :doc)))

  (is (= {:my :val} @state-4))
  (is (= nil (-> state-4 var meta :attr)))

  (is (= {:my :val} @state-5))
  (is (= true (-> state-5 var meta :attr)))

  (is (= {:my :val} @state-6))
  (is (= "doc" (-> state-6 var meta :doc)))

  (is (= {:my :val} @state-7))
  (is (= "doc" (-> state-7 var meta :doc)))
  (is (= true (-> state-7 var meta :attr)))

  (is (= "val" @state-8))
  (is (= nil (-> state-8 var meta :doc)))

  (is (= "val" @state-9))
  (is (= "doc" (-> state-9 var meta :doc)))
  (is (= true (-> state-9 var meta :attr))))
