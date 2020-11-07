# Change Log

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
