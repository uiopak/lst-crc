# Copilot instructions for `lst-crc`

## Build, test, and lint

- Build the plugin ZIP: `.\gradlew.bat buildPlugin`
- Verify plugin structure and compatibility: `.\gradlew.bat verifyPlugin`
- Run non-UI tests: `.\gradlew.bat test`
- Run a single non-UI test: `.\gradlew.bat test --tests "com.github.uiopak.lstcrc.<ClassName>"`
- Remote Robot UI tests require a separately started IDE:
  1. `.\gradlew.bat runIdeForUiTests`
  2. `.\gradlew.bat uiTestReady`
  3. `.\gradlew.bat uiTest`
- Run a single Remote Robot UI class or method: `.\gradlew.bat uiTest --tests "com.github.uiopak.lstcrc.plugin.LstCrcInteractionUiTest"` or `.\gradlew.bat uiTest --tests "com.github.uiopak.lstcrc.plugin.LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions"`
- IDE Starter UI tests launch and manage their own IDE: `.\gradlew.bat starterUiTest`
- Run a single IDE Starter UI class or method: `.\gradlew.bat starterUiTest --tests "com.github.uiopak.lstcrc.starter.LstCrcInteractionStarterUiTest"` or `.\gradlew.bat starterUiTest --tests "com.github.uiopak.lstcrc.starter.LstCrcInteractionStarterUiTest.testStatusWidgetAndRevisionActions"`
- Run Qodana inspections locally: `.\gradlew.bat qodanaScan`
- Clean IDE Starter artifacts that accumulate outside `build\`: `.\gradlew.bat cleanStarterArtifacts`

## High-level architecture

- This is an IntelliJ Platform plugin that adds a custom `GitChangesView` tool window, custom search scopes, and a status bar widget. The entry points are registered in `src\main\resources\META-INF\plugin.xml`.
- `MyToolWindowFactory` restores the permanent `HEAD` tab plus persisted comparison tabs on project open. Persisted UI state lives in `ToolWindowStateService`, which stores tab metadata in `gitTabsIdeaPluginState.xml`.
- `ToolWindowStateService` is the orchestration layer for tab selection and refreshes. When the active tab changes, it calls `GitService.getChanges(...)`, updates `ProjectActiveDiffDataService`, refreshes the active `LstCrcChangesBrowser`, and broadcasts state changes.
- `GitService` owns all Git4Idea and low-level git access. It handles multi-repo projects, computes categorized changes, and tracks per-repository comparison targets through `TabInfo.comparisonMap`.
- `ProjectActiveDiffDataService` is the single source of truth for the currently active diff. It caches the categorized file sets and comparison context, then publishes `DIFF_DATA_CHANGED_TOPIC`.
- Scopes and gutter rendering consume that active diff cache rather than querying Git directly. `LstCrcScopeProvider` / `LstCrcSearchScopeProvider` expose the current comparison as IDE scopes, while `LstCrcGutterTrackerService` and `VisualTrackerManager` refresh line status markers and tab/editor coloring when diff data changes.
- `PluginStartupActivity` eagerly initializes listeners (`VfsListenerService`, `VcsChangeListener`, gutter services, visual tracker manager), waits for smart mode, then triggers the first refresh so scopes, widgets, and gutter state are ready early.
- There are two separate UI-testing stacks:
  - Remote Robot tests live under `src\test\kotlin\com\github\uiopak\lstcrc\plugin` and run through the `uiTest` task against an IDE started by `runIdeForUiTests`.
  - IDE Starter tests live under `src\uiTest\kotlin\com\github\uiopak\lstcrc\starter` and run through `starterUiTest`. They use a test bridge in `src\main\kotlin\com\github\uiopak\lstcrc\testing` that is excluded from the published plugin unless `starterUiTest` is in the task graph or `-PincludeTestBridge=true` is set.

## Key conventions

- Treat `ToolWindowStateService` as the authoritative source for tab state and refresh sequencing. Avoid bypassing it with ad-hoc UI mutations; it already handles queuing, persistence, notifications, and the `HEAD` special case.
- `HEAD` is represented implicitly: `selectedTabIndex == -1` means the permanent `HEAD` tab, and `openTabs` only contains closable comparison tabs.
- Use the message bus topics in `src\main\kotlin\com\github\uiopak\lstcrc\messaging\LstCrcTopics.kt` for cross-component updates. Diff consumers are expected to react to published state instead of calling each other directly.
- Keep Git and VCS work off the EDT. The existing pattern is background work in `Task.Backgroundable` / `CompletableFuture`, then UI updates via `ApplicationManager.invokeLater` or `Dispatchers.EDT`.
- Store plugin settings in `PropertiesComponent` through `ToolWindowSettingsProvider`; components such as the status widget and gutter services expect settings changes to flow through that provider and the related message-bus notifications.
- Localize all user-facing strings through `LstCrcBundle` and `messages\LstCrcMessages.properties`. `plugin.xml` also points at that resource bundle for action and notification text.
- The plugin description shipped in `plugin.xml` is extracted from the `<!-- Plugin description -->` block in `README.md` during the Gradle build. Update that README section when changing marketplace-facing description text.
- The maintained automated coverage is UI-focused. Remote Robot tests are tagged `ui`, IDE Starter tests are tagged `starter`, and JUnit parallel execution is disabled in `src\test\resources\junit-platform.properties`.
