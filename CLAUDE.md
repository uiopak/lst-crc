# LST-CRC

IntelliJ Platform plugin (Kotlin, Git4Idea) that keeps one active Git comparison (working tree vs `HEAD`,
a branch or a revision) in sync across a tool window, named/search scopes, a status bar widget, gutter
markers and editor tab colors. Deeper notes are in `docs/` (see `docs/README.md`). When you change behavior, add or
remove a file, or add or rename a test, update the matching doc in the same PR.

## Commands

Run from the repo root on Linux. The first build downloads the IntelliJ IDEA 2026.2 distribution
(1.6 GB, about 6 GB in `~/.gradle` once extracted) and takes several minutes.

- Build the plugin ZIP: `./gradlew buildPlugin` (output in `build/distributions/`)
- Unit tests: `./gradlew test`
- One test class: `./gradlew test --tests "com.github.uiopak.lstcrc.services.GitServiceLineStatsTest"`
- One test method: `./gradlew test --tests "com.github.uiopak.lstcrc.services.GitServiceLineStatsTest.testCalculateLineStatsForSingleLineReplacement"`
- Compile the UI test suites without running them:
  `./gradlew compileTestKotlin compileUiTestKotlin -PincludeTestBridge=true`
  (`uiTest` needs a Java 25 toolchain, which the foojay resolver downloads on first use)
- `./gradlew verifyPlugin` runs the IntelliJ Plugin Verifier against six IDE versions, from 2025.1 up.
  It downloads several GB, so run it only when you touch platform API usage (see the rules below).

Do not run `runIdeForUiTests`, `uiTest`, `starterUiTest` or `starterPerformanceTest` on a cloud VM: they
need a display. Use the GitHub workflows instead (see "Testing UI changes").

In cloud sessions a SessionStart hook (`.claude/hooks/warm-gradle.sh`) starts
`./gradlew compileKotlin` in the background so dependencies download while you work. Its output is in
`/tmp/gradle-warmup.log`. If a build seems slow at the start of a session, check that log first.

Network failures in cloud VMs come from the environment, not the code. Report them and don't work
around them in the build scripts:
- HTTP 429 from `repo.maven.apache.org` means Maven Central is rate-limiting the shared IP. Retry later.
- HTTP 403 from a `*.cloudfront.net` host or `artifacts-caching-proxy.aws.intellij.net` means the host
  is missing from the environment's network allowlist.

## Rules that must not be broken

- The plugin targets Java 21 with `pluginSinceBuild = 251` (2025.1). Only the `uiTest` source set
  (IDE Starter) uses Java 25. Do not raise either.
- Keep both UI test suites. They find different bugs:
  - Remote Robot in `src/test/kotlin/com/github/uiopak/lstcrc/plugin`, which runs on Linux, Windows
    and macOS;
  - IDE Starter in `src/uiTest`, which runs on Linux.
- The `*ForTest` methods in production code (`LstCrcChangesBrowser`, `BranchSelectionPanel`,
  `RepoNodeRenderer`, ...) are called from the Remote Robot JavaScript. They look unused. Keep them.
- Plugin code that the Remote Robot JavaScript calls (through `loadClass`, reflection or method
  names) must not be Kotlin `internal`. The JVM name of an `internal` member is mangled, so the
  JavaScript can't find it.
- Several platform classes are Java in 2025.1/2025.2 but Kotlin from 2025.3, for example
  `ProjectLevelVcsManager`.
  - Never call their `getInstance` from Kotlin. It compiles against a companion object that the
    older versions don't have, so it fails at runtime there.
  - Look them up with `project.service<T>()` instead (see `PluginStartupActivity`).
  - Only `verifyPlugin` catches this, because all tests run against 2026.2.
- Log tracing with the lazy `logger.debug { "..." }` (import `com.intellij.openapi.diagnostic.debug`), so
  nothing is built or written unless debug logging is on. Use `warn`/`error` only for real problems, and
  don't log at `INFO` on paths that run per refresh, per keystroke or per file.
- User-facing strings go through `LstCrcBundle` and `src/main/resources/messages/LstCrcMessages.properties`.
- The plugin description in `plugin.xml` comes from the `<!-- Plugin description -->` block in
  `README.md` at build time. Edit the README block, not `plugin.xml`.

## Testing UI changes

Cloud VMs have no display, so UI tests run as GitHub workflows on the pushed branch:

- `gh workflow run "Run UI Tests" --ref <branch> -f tests='<gradle --tests pattern>'` runs the Remote
  Robot suite on Linux, Windows and macOS, for example `-f tests='*LstCrcRealRepositoryUiTest'`.
  Leave out `-f tests` to run the full suite, which takes about 40 minutes.
- `gh workflow run "Run Starter UI Tests" --ref <branch>` runs the IDE Starter suite (`starterUiTest`
  and `starterPerformanceTest`) on Linux, in about 40 minutes.

If `gh` isn't available, trigger the same workflows through the GitHub MCP tools.

Real-repository scenario tests use shared fixtures in `src/test/kotlin/com/github/uiopak/lstcrc/fixtures`,
which both suites use (the `uiTest` source set compiles against the `test` output):

- `GsonFixture` sets up google/gson at four release commits pinned by SHA (`gson-2.10.1` to
  `gson-2.13.1`, one local branch each). It fetches them once with `--depth=1` into
  `build/test-repos/` and copies the result into each test project.
- `GitDiffOracle` computes the expected comparison, line stats and scope contents with the git CLI,
  using the same diff options as the plugin. Ask it after a step's action, not before.
- `LstCrcRealRepositoryUiTest` (Remote Robot) and `LstCrcRealRepositoryStarterUiTest` (IDE Starter)
  are the two test classes that run these scenarios.
- `LstCrcPerformanceReport` writes step timings to `build/reports/lstcrc-performance/results.jsonl`.
  Each workflow turns that file into a table in its job summary.
- The timings are report only and never fail a test. The baseline numbers are in the workflow runs
  linked from PR #82.

The Build workflow (`.github/workflows/build.yml`: Build, Test, Inspect code (Qodana), Verify plugin)
runs on every PR and must pass before merging.

## Architecture

Entry points are registered in `src/main/resources/META-INF/plugin.xml`. The code is under
`src/main/kotlin/com/github/uiopak/lstcrc/`.

- `services/ToolWindowStateService` owns tab state and refresh sequencing. It persists open tabs in
  `gitTabsIdeaPluginState.xml`. `HEAD` is implicit: `selectedTabIndex == -1` means the permanent `HEAD`
  tab, and `openTabs` holds only the closable comparison tabs. `refreshDataForCurrentSelection()`
  merges concurrent requests into one coroutine refresh cycle. Don't bypass it with ad-hoc UI changes.
- `services/GitService` does all Git4Idea and git CLI work. It handles multi-repo projects and
  per-repository comparison targets (`TabInfo.comparisonMap`). `getChanges` is a suspend function that
  runs on `Dispatchers.IO`.
- `services/ProjectActiveDiffDataService` caches the active diff (categorized files, comparison
  context) and publishes `DIFF_DATA_CHANGED_TOPIC`. Tab state changes go out on
  `TOOL_WINDOW_STATE_TOPIC`. Both topics are in `messaging/LstCrcTopics.kt`.
- Consumers read that cache instead of querying Git:
  - scopes (`scopes/LstCrcProvidedScopes`, `LstCrcScopeProvider`, `LstCrcSearchScopeProvider`,
    `FileStatusScopes.kt`);
  - `gutters/VisualTrackerManager` (gutter line status markers and editor tab colors);
  - `toolWindow/LstCrcStatusWidget`;
  - `toolWindow/LstCrcChangesBrowser` (the tree in each tab).
- `listeners/VcsChangeListener` is the only source of automatic refreshes. It listens to
  `ChangeListManager` updates, `GIT_REPO_CHANGE`, document saves and unsaved document edits, and
  triggers one refresh after a 300 ms debounce. A burst of edits only triggers an edit-only
  refresh, which reuses the last git result and only overlays unsaved documents.
- `listeners/PluginStartupActivity` initializes `VcsChangeListener` and `VisualTrackerManager` and
  refreshes the tab colors. Then it waits for VCS initialization (not smart mode, so it doesn't wait for
  indexing) and runs the first diff load.
- `toolWindow/MyToolWindowFactory` builds the `GitChangesView` tool window: the `HEAD` tab plus the
  persisted tabs.
- Settings live in `toolWindow/LstCrcSettingsService`, an app-level `PersistentStateComponent`
  saved in `lstCrcSettings.xml`. It imports legacy `PropertiesComponent` values once.
  `ToolWindowSettingsProvider` builds the gear menu.
- Keep Git/VCS work off the EDT. Use coroutines, with `Dispatchers.EDT` for UI updates.
- The IDE Starter test bridge is in `src/testBridge`. It is compiled into the plugin only for
  `starterUiTest`/`starterPerformanceTest` or with `-PincludeTestBridge=true`.
- Unit tests extend `testsupport/LstCrcTestCase`, which ignores one known startup error from the
  2026.2 IDE. Use it instead of `BasePlatformTestCase`. JUnit parallel execution is disabled.
