# LST-CRC Docs

This directory contains the curated internal markdown documentation for the current `src/main` plugin code.

Documents:

- `plugin-capabilities.md` describes the plugin's user-facing behavior and supported workflows.
- `plugin-architecture.md` describes the runtime architecture, service boundaries, refresh flow, persistence, and JetBrains platform dependencies.
- `file-catalog.md` documents each current `src/main` file separately, including its role, dependencies, and why it exists.
- `file-refactor-audit.md` reviews each current `src/main` file against the JetBrains Platform APIs and patterns it relies on and records realistic simplification or refactor opportunities.
- `test-capability-matrix.md` maps each plugin capability to its test coverage, organized by capability ID and decision path.
- `test-to-capability-map.md` provides the reverse mapping from each active test method to the capability IDs it covers.

The IDE Starter test bridge lives in `src/testBridge` (`LstCrcUiTestBridge.kt`) and is only included in the plugin build for Starter tasks (or when `-PincludeTestBridge=true` is set).