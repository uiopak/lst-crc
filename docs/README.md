# LST-CRC Docs

This directory contains the curated internal markdown documentation for the current `src/main` plugin code.

Documents:

- `plugin-capabilities.md` describes the plugin's user-facing behavior and supported workflows.
- `plugin-architecture.md` describes the runtime architecture, service boundaries, refresh flow, persistence, and JetBrains platform dependencies.
- `file-catalog.md` documents each current `src/main` file separately, including its role, dependencies, and why it exists.
- `file-refactor-audit.md` reviews each current `src/main` file against the JetBrains Platform APIs and patterns it relies on and records realistic simplification or refactor opportunities.

The documented `src/main` surface also includes `LstCrcUiTestBridge.kt`, which lives under `src/main` for IDE Starter service discovery but is excluded from ordinary main builds unless `starterUiTest` or `includeTestBridge=true` enables it.