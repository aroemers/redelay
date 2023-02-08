# Change Log

## Unreleased

### Added

- The `defstate` macro now declares the resulting var as `:dynamic`. This enables the use of `binding` in tests.
- The function `deref?`, which lets you inspect the value of a state, without starting it.

### Changed

- The string representation of an unrealized State object now says `:unrealized` instead of `:not-delivered`.
- The string representation of a realized State object now adheres to a `*print-length*` of 10.
- [BREAKING] The low-level `state*` function now takes a `:name` key, instead of `:ns-str` and `:name-str`. The new entry must be a value and is still optional.
- [BREAKING] The `defstate` macro no longer supports a docstring or attribute map. It conflicted with start values which were Strings or maps. Metadata on the name symbol is still supported. So instead of `(defstate foo "docstring" ...)` you should now write `(defstate ^{:doc "docstring"} foo ...)`.

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
