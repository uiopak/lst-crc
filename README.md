# LST-CRC

![Build](https://github.com/uiopak/lst-crc/workflows/Build/badge.svg)

LST-CRC is an IntelliJ Platform plugin that keeps one active Git comparison in sync across a dedicated changes tool window, IDE named scopes, search scopes, a status bar widget, and custom gutter overlays.

<!-- Plugin description -->
LST-CRC adds a comparison-focused Git workflow to the IDE.

With it, you can compare the current working tree against `HEAD`, branches, or specific revisions in a dedicated tool window, keep multiple comparison tabs open, and expose the active comparison through IDE scopes, search scopes, a status bar widget, and custom gutter markers.

The plugin is built for branch-heavy and multi-repository work where the current question is not only "what changed from HEAD?" but "what changed against this exact branch or revision target?"
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
