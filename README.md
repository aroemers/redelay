[![Clojars Project](https://img.shields.io/clojars/v/functionalbytes/redelay.svg)](https://clojars.org/functionalbytes/redelay)
[![cljdoc badge](https://cljdoc.org/badge/functionalbytes/redelay)](https://cljdoc.org/d/functionalbytes/redelay/CURRENT)
[![Clojure CI](https://github.com/aroemers/redelay/workflows/Clojure%20CI/badge.svg?branch=master)](https://github.com/aroemers/redelay/actions?query=workflow%3A%22Clojure+CI%22)
[![Clojars Project](https://img.shields.io/clojars/dt/functionalbytes/redelay?color=blue)](https://clojars.org/functionalbytes/redelay)
![Time](https://img.shields.io/badge/time-delayed-brightgreen)

# 🏖 redelay

A Clojure library for state lifecycle-management using resettable delays, inspired by [mount-lite](https://github.com/aroemers/mount-lite), decomplected from any methodology.

![Banner](banner.png)

## Usage

### The basics

With this library you create **State** objects.
Think of them as Clojure's [Delay](https://clojuredocs.org/clojure.core/delay) objects, but **resettable** and **tracked**.
Because of the resetting feature, a State object can take _two_ expressions; a `:start` expression and a `:stop` expression.
You create State objects using the `state` macro.

Let's create two State objects first:

```clj
(require '[redelay.core :refer [state status stop]])

(def config (state (println "Loading config...")
                   (edn/read-string (slurp "config.edn")))
;=> #'user/config

(def db (state :start  ; <-- optional in this position
               (println "Opening datasource...")
               (hikari/make-datasource (:jdbc @config))

               :stop
               (println "Closing datasource...")
               (hikari/close-datasource this)))
;=> #'user/db

config
;=> #<State@247136[user/state--312]: :not-delivered>

(realized? config)
;=> false
```

There are several things to note here.

- The `:stop` expression is optional. Actually, all expressions to `state` are optional.
- An expression can consist of multiple forms, wrapped in an implicit do.
- The first forms to `state` are considered to be the `:start` expression, if not qualified otherwise.
- The `:stop` expression has access to a `this` parameter, bound to the State value.
- You can call `realized?` on a State object, just like you can on a Delay.

Now let's use our states.
Just like a Delay, the first time a State is consulted by a deref (or force), it is realized.
This means that the `:start` expression is executed and its result is cached.

```clj
@db
Loading config...
Opening datasource...
;=> org.postgresql.ds.PGSimpleDataSource@267825

@db
;=> org.postgresql.ds.PGSimpleDataSource@267825

(realized? config)
;=> true
```

You can see that the `:start` expressions of the states are only executed once.
Subsequent derefs return the cached value.

A State implements Java's `Closeable`, so you _could_ call `.close` on it.
This will execute the `:stop` expression and clear its cache.
**Under water however, redelay keeps track of which states are realized and thus active.**
You can see which states are active by calling `(status)`:

```clj
(status)
;=> (#<State@247136[user/state--312]: {:jdbc-url "jdbc:postgresql:..."}>
;=>  #<State@329663[user/state--315]: org.postgresql.ds.PGSimpleDataSource@267825>)
```

Because the active states are tracked, you can easily stop all of them by calling `(stop)`.
All the active states are stopped (i.e. closed), in the reverse order of their realization.
Afterwards, they are ready again to be realized.

```clj
(stop)
Closing datasource...
;=> (#<State@329663[user/state--315]: :not-delivered>
;=>  #<State@247136[user/state--312]: :not-delivered>)
```

### Naming and defstate

Next to the `:start` and `:stop` expressions, you can also pass a `:name` to the `state` macro.
This makes recognizing the State objects easier.

```clj
(def config (state (load-config) :name user/config))
;=> #'user/config

config
;=> #<State@19042[user/config]: :not-delivered>
```

Because it is common to have the name to be equal to the var it is bound to, above can also be written as follows:

```clj
(defstate config (load-config))
```

The `defstate` macro fully supports metadata on the name, docstrings and attribute maps.
The metadata is both set on the var as well as on the State object.

Next to metadata support, Clojure's `namespace` and `name` functions also work on State objects.
For example, this yields a somewhat easier to read status list:

```clj
(map name (status))
;=> ("config")
```

### Testing

Since state in redelay is handled as first class objects, there are all kinds of testing strategies.
It all depends a bit on where you keep your State objects (see next section).

For the examples above you can simply use plain old `with-redefs` to your hearts content.
We can redefine "production" states with other states, or even with a plain `delay`.
There is no need for a special API here.
For example:

```clj
(deftest test-in-memory
  (with-redefs [config (delay {:jdbc-url "jdbc:derby:..."})]
    (is (instance? org.apache.derby.jdbc.ClientDataSource @db))))
```

It might be a good idea to add a fixture to your tests, ensuring `(stop)` is always called before and/or after a test.

### Global versus local state

Although the examples above have bound the State objects to global vars, this is certainly not required.
State objects can live anywhere and can be passed around like any other object.
If you prefer a map of states for example, be it unrealized, realized or dereferenced, then that's perfectly fine as well.
As such, this library supports the whole spectrum of [mount](https://github.com/aroemers/mount-lite)-like global states to [Component](https://github.com/stuartsierra/component)-like system maps to [Integrant](https://github.com/weavejester/integrant)-like data-driven approaches.

By the way, if you prefer system maps, have a look at the [rmap](https://github.com/aroemers/rmap) library, as it combines well with redelay.

### Extending redelay

The redelay library is minimal on purpose.
It offers a powerful first class State object and the two basic management functions `(status)` and `(stop)`.
Those two functions are actually implemented using the library's extension point: the watchpoint.

The library contains a public `watchpoint` var.
You can watch this var by using Clojure's `add-watch`.
The registered watch functions receive realized State objects as "new" or stopped (closed) State object as "old".

You can do all kinds of things with this watchpoint, such as logging or keeping track of States yourself.
You want to have more sophisticated stop logic with separate buckets/systems of states using their metadata for example?
Go for it, be creative and use the library's building blocks to fit your perfect workflow!

_That's it for simple lifecycle management around the stateful parts of your application. Have fun!_ 🚀

## License

Copyright © 2020 Functional Bytes

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
