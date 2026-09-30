# LST-CRC Docs

Internal documentation for the plugin code in `src/main`. Build commands, the rules that must not be broken, and how to run the UI tests are in [`CLAUDE.md`](../CLAUDE.md) at the repository root.

- `plugin-capabilities.md` lists what the plugin does, with capability IDs (`C1.1` ...).
- `plugin-architecture.md` describes the runtime architecture: service boundaries, the refresh flow, persistence, threading, and JetBrains platform dependencies.
- `file-catalog.md` explains each `src/main` file: its role, dependencies, and why it exists.
- `file-refactor-audit.md` lists verified refactoring opportunities and the code that looks removable but must stay.
- `test-capability-matrix.md` maps each capability to its test coverage, case by case.
- `test-to-capability-map.md` maps every test method back to capability IDs.
- `continuous-review-2026-09-30.md` records the ongoing review's coverage, findings, regression evidence and validation.

Keep these in step with the code: a new file needs a catalog entry, a new capability needs an ID and tests, and a new or renamed test needs a row in the test map.

The IDE Starter test bridge lives in `src/testBridge` (`LstCrcUiTestBridge.kt`) and is only compiled into the plugin for Starter tasks, or when `-PincludeTestBridge=true` is set.

`gradlew check` also runs `verifyGradleWrapperVersion`. It checks the wrapper
task's configured version against the checked-in distribution URL, so wrapper
regeneration cannot silently undo a dependency update. Keep `gradleVersion` in
`gradle.properties` aligned when upgrading the wrapper.

`check` enforces at least 66% production line coverage through `koverVerify`.
The floor comes from the 66.7% baseline before the continuous review. Reports
exclude the Starter tests and UI bridge, which are test support rather than
production plugin code. Both UI suites still run independently in CI.
