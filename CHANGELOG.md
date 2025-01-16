# Change Log

## 2.0.1

### Added

- Proper clj-kondo support

## 2.0.0

### Added

- The `defstate` macro now declares the resulting var as `:dynamic`. This enables the use of `binding` in tests.
- The State object now implements `IPersistentStack`, thus now supports Clojure's `peek`. This can be used to inspect the value, without starting it.

### Changed

- The string representation of an unrealized State object now says `:unrealized` instead of `:not-delivered`.
- The string representation of a realized State object now adheres to a `*print-length*` of 10.
- ❗️BREAKING: The low-level `state*` function now takes a `:name` key, instead of `:ns-str` and `:name-str`. The new entry must be a symbol and is still optional.
- ❗️BREAKING: The State object now implements `IReference` instead of `IObj`. This means that `with-meta` support has been replaced with `alter-meta!` and `reset-meta!`. This way updating meta data does not result in a new State object anymore.
- ❗️BREAKING: The notifications to the `watchpoint` now receive one of `:starting`, `:started`, `:stopping` or `:stopped` as the third argument, and the State object as the fourth argument.

### Fixed

- The `defstate` macro could sometimes mistake a string or map value for a docstring or attribute map. For example, this would fail `(defstate foo "bar" :stop (println this))`. This is now fixed.

## 1.1.0

### Added

- The function `close!` was added, which closes a State by force by skipping its stop logic.


## 1.0.3

### Added

- Trying to redefine an active (i.e. realized) `defstate` is skipped and yields a warning.


## 1.0.2

### Fixed

- The low-level `state*` function no longer requires a `:stop-fn` function.


## 1.0.1

### Added

- The low-level `state*` function is now public and documented.


## 1.0.0

Initial release
