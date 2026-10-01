# LST-CRC

![Build](https://github.com/uiopak/lst-crc/workflows/Build/badge.svg)

LST-CRC is an IntelliJ Platform plugin that keeps one active Git comparison in sync across a dedicated changes tool window, IDE named scopes, search scopes, a status bar widget, and custom gutter overlays.

<!-- Plugin description -->
LST-CRC compares your Git working tree with `HEAD`, a branch, or a commit selected in Git Log. The selected tool-window tab defines the active comparison.

- Keep multiple comparison tabs with aliases and separate targets for each repository. Tabs and targets survive IDE restarts.
- Browse created, modified, moved and deleted files, open diffs, and optionally show added and removed line counts.
- Follow changes as you type, including unsaved edits and optional untracked files.
- Use comparison scopes in Find/Search and for editor tab colors, and show changes against the selected target in the editor gutter.
- Switch comparisons from the status bar.

To use scopes and custom gutters on the `HEAD` tab, enable **Include HEAD tab changes in file scopes** in the tool-window settings. Requires the IDE's Git integration.
<!-- Plugin description end -->

## Highlights

- Compare the working tree against `HEAD`, branch tips, or Git Log revisions.
- Keep multiple comparison tabs open, including per-tab aliases and per-repository overrides.
- Reuse the active comparison in named scopes, Find/Search scopes, the status bar widget, and gutter overlays.
- Surface repository-specific comparison context directly in the changes tree.
- Preserve comparison state across IDE restarts.

## Build And Test

Use `./gradlew` on Linux and macOS, `gradlew.bat` on Windows.

- Build the plugin ZIP: `./gradlew buildPlugin` (in `build/distributions/`)
- Run unit tests: `./gradlew test`
- Verify compatibility with IDE 2025.1 and later: `./gradlew verifyPlugin`
- UI tests (Remote Robot and IDE Starter) need a display. See [CLAUDE.md](./CLAUDE.md) for how to run them locally or as GitHub workflows.

Architecture notes and the test mapping live in [docs/README.md](./docs/README.md).

## Installation

- Build a plugin ZIP with `./gradlew buildPlugin` and install it from disk via <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙</kbd> > <kbd>Install plugin from disk...</kbd>
- Or download the latest packaged artifact from the repository releases page when a release is published.

## Development Notes

- The plugin description in `src/main/resources/META-INF/plugin.xml` is extracted from the marked README block above during the Gradle build.
- Internal design and capability documentation lives in [docs/](./docs/).
- IDE Starter bridge code is isolated under `src/testBridge` and included only for Starter test tasks or explicit `-PincludeTestBridge=true` runs.
