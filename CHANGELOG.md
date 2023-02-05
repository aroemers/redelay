# Change Log

## Unreleased

### Added

- The `defstate` macro now declares the resulting var as `:dynamic`. This enables the use of `binding` in tests.
- The function `deref?`, which lets you inspect the value of a state, without starting it.

### Changed

- The string representation of an unrealized State object now says `:unrealized` instead of `:not-delivered`.
- The string representation of a realized State object now adheres to a `*print-length*` of 10.

### Fixed

- The `defstate` macro no longer confuses a string and/or map as a docstring and/or metadata if no subsequent expressions are given. For example, this now works correctly: `(defstate foo "bar")`.

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
