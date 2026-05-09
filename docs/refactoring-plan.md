# Refactoring Plan (Pass 4: Platform-First Reduction)

Date: 2026-05-03
Status: Proposed (new pass after Pass 3 completion)

## Objective

Drive another aggressive reduction pass focused on removing plugin-owned behavior that can be delegated to public IntelliJ Platform primitives.

Rules for this pass:

1. No new internal API usage.
2. No new deprecated API usage.
3. Prefer platform defaults over configurable custom behavior when line-count and maintenance savings are large.
4. If a capability must remain custom, isolate it into one adapter/service with a narrow API.

## Baseline From Current Branch

Already completed in this branch (do not re-plan):

1. Test-only hooks removed from production browser, bridge moved to src/testBridge.
2. NamedScopeWrapper deleted, search scopes use GlobalSearchScopesCore.filterScope.
3. BranchSelectionPanel keeps the explicit search field and custom branch filter flow; native tree speed search is not an acceptable replacement for this UI.
4. Refresh orchestration is centered on VcsChangeListener + ToolWindowStateService, while VfsListenerService remains as the file-system bridge that marks VCS state dirty.
5. Settings migrated to typed LstCrcSettingsService with compatibility mirror.
6. Startup flow consolidated to one refresh path plus one final sync.
7. Existing unavoidable impl references isolated in ToolWindowUiCompatibility.

Largest remaining production files by LOC (current snapshot):

1. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/LstCrcChangesBrowser.kt (~533)
2. src/main/kotlin/com/github/uiopak/lstcrc/services/GitService.kt (~352)
3. src/main/kotlin/com/github/uiopak/lstcrc/services/ToolWindowStateService.kt (~342)
4. src/main/kotlin/com/github/uiopak/lstcrc/gutters/VisualTrackerManager.kt (~316)
5. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/ToolWindowSettingsProvider.kt (~310)

## Community Source Anchors Used In This Analysis

Public platform references in community/ used to define this pass:

1. community/platform/vcs-impl/src/com/intellij/openapi/vcs/changes/ui/AsyncChangesBrowserBase.kt
2. community/platform/vcs-impl/src/com/intellij/openapi/vcs/changes/ui/ChangesBrowserBase.java
3. community/platform/vcs-impl/shared/src/com/intellij/openapi/vcs/changes/ui/ChangesTreeHandlers.kt
4. community/platform/vcs-impl/src/com/intellij/openapi/vcs/changes/ui/SimpleAsyncChangesBrowser.java
5. community/platform/core-ui/src/ui/TreeUIHelper.java

## New Priority Workstreams

## P0) Collapse Custom Click Engine In LstCrcChangesBrowser

Primary files:

1. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/LstCrcChangesBrowser.kt
2. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/ToolWindowSettingsProvider.kt
3. src/main/resources/messages/LstCrcMessages.properties

Problem:

1. LstCrcChangesBrowser still owns a large custom click state machine (per-button click states, delayed single-click scheduling, manual dispatch paths).
2. This duplicates platform interaction infrastructure provided by ChangesTreeHandlers and ChangesBrowserBase.

Platform-first refactor:

1. Restore platform double-click and enter handling (do not disable setDoubleClickAndEnterKeyHandler).
2. Use standard popup integration via createPopupMenuActions and viewer.installPopupHandler behavior.
3. Replace per-button action matrix with a minimal interaction policy:
1. Double-click or Enter -> show diff.
2. Context menu -> open source, show diff, show in project tree.
4. Keep only one custom extension point if needed: additional repo-comparison action.

Expected impact:

1. Remove around 140-240 LOC from LstCrcChangesBrowser.
2. Remove a significant part of click-action settings surface.
3. Reduce flakiness risk in UI tests and event-order edge cases.

Risk:

1. Medium-High because user-configurable click behavior is a visible UX change.
2. Requires explicit product decision to simplify behavior surface.

## P0) Replace Manual Open-Source And Diff Glue With Action-System Data Flow

Primary files:

1. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/LstCrcChangesBrowser.kt
2. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/ShowRepoComparisonInfoAction.kt

Problem:

1. Browser manually maps Change to open source/diff logic and fallback messaging.
2. This increases plugin-owned behavior that platform actions already implement.

Refactor:

1. Prefer invoking existing VCS actions where DataContext provides selected changes.
2. Keep only deleted-revision specific behavior where platform action cannot cover the scenario.
3. Centralize custom fallback in one helper (single file) instead of multiple call paths.

Expected impact:

1. Remove around 60-120 LOC.

Risk:

1. Medium (context wiring and deleted-file edge cases).

## P1) Reduce ToolWindowStateService To Pure State + Intent Orchestration

Primary files:

1. src/main/kotlin/com/github/uiopak/lstcrc/services/ToolWindowStateService.kt
2. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/MyToolWindowFactory.kt

Problem:

1. Service currently owns both persistence and UI-specific coordination (active browser lookup, displayChanges routing, selection + refresh coupling).
2. This mixes state, orchestration, and view concerns.

Refactor:

1. Keep ToolWindowStateService as state + refresh intent coordinator only.
2. Move browser-specific UI update responsibilities behind message bus subscribers in toolwindow layer.
3. Replace direct getActiveChangesBrowser lookup with event-driven consumer(s).

Expected impact:

1. Remove around 70-130 LOC from service and factory combined.
2. Cleaner architecture boundary between state and UI.

Risk:

1. Medium (ordering and race conditions on startup/selection changes).

## P1) Split ToolWindowSettingsProvider Into Declarative Setting Specs

Primary files:

1. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/ToolWindowSettingsProvider.kt
2. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/LstCrcSettingsService.kt

Problem:

1. Settings menu building is verbose and repetitive (many near-identical toggle declarations).
2. It is still one of the largest files in production surface.

Refactor:

1. Introduce a small declarative model for setting items (id, label key, default, callbacks, visibility predicates).
2. Generate ToggleAction groups from descriptors.
3. Keep only special-case builders for click-action radio groups.

Expected impact:

1. Remove around 80-140 LOC.
2. Lower maintenance for future settings additions/removals.

Risk:

1. Low-Medium.

## P1) Simplify GitService HEAD Path Via ChangeListManager For Local State

Primary files:

1. src/main/kotlin/com/github/uiopak/lstcrc/services/GitService.kt

Problem:

1. HEAD/local path still does custom Git command orchestration for untracked + tracked state.
2. For local working tree status, platform VCS caches may already provide the needed view.

Refactor:

1. Evaluate replacing HEAD local-change path with ChangeListManager snapshot for tracked/untracked status.
2. Keep GitChangeUtils path only for explicit non-HEAD comparisons.
3. Keep overlayUnsavedDocumentChanges if still needed for unsaved editor semantics.

Expected impact:

1. Potential reduction around 40-90 LOC.
2. Less low-level git command maintenance.

Risk:

1. Medium-High (must preserve multi-root and comparison semantics).

## P2) Compress VisualTrackerManager With Strategy Split

Primary files:

1. src/main/kotlin/com/github/uiopak/lstcrc/gutters/VisualTrackerManager.kt

Problem:

1. Single file combines interception lifecycle, target-resolution policy, content loading, and restoration logic.
2. Complexity is high even though behavior is stable.

Refactor:

1. Split into small collaborators:
1. TargetRevisionResolver
2. TrackerLifecycleCoordinator
3. RevisionContentLoader
2. Keep public service API unchanged.

Expected impact:

1. Net LOC may be neutral initially.
2. Complexity reduction and easier future deletion if platform catches up.

Risk:

1. Medium.

## P2) Re-evaluate ExpandNewNodesStateStrategy Against Platform TreeState Strategies

Primary files:

1. src/main/kotlin/com/github/uiopak/lstcrc/toolWindow/ExpandNewNodesStateStrategy.kt

Problem:

1. Custom path-keying and reflection-based node identification are complex and fragile.

Refactor:

1. Compare behavior against platform strategies already used in DVCS UI (KEEP_NON_EMPTY and custom swap-state examples in community).
2. If platform strategy + one small hook can satisfy C3.9 semantics, replace current custom strategy.
3. If not, retain strategy but remove reflection path extraction by introducing stable explicit node keys.

Expected impact:

1. Remove around 30-90 LOC depending on chosen variant.

Risk:

1. Medium (tree-state UX regressions are subtle).

## P3) Full Test Bridge Module Split (Still Pending)

Primary files:

1. build.gradle.kts
2. src/testBridge/kotlin/com/github/uiopak/lstcrc/testing/LstCrcUiTestBridge.kt
3. src/uiTest/kotlin/com/github/uiopak/lstcrc/starter/remote/LstCrcUiTestBridgeRemote.kt

Problem:

1. testBridge is conditionally included in main source set; ownership separation is improved but not complete.

Refactor:

1. Move bridge into separate module/artifact loaded only in Starter test runs.
2. Keep production plugin artifact fully unaware of bridge code.

Expected impact:

1. Strong boundary and safer release pipeline.

Risk:

1. Medium-High due to build and test harness wiring.

## Recommended Execution Order

1. P0 click-engine collapse in LstCrcChangesBrowser.
2. P0 action-system replacement for open source/diff glue.
3. P1 ToolWindowStateService boundary cleanup.
4. P1 declarative settings refactor.
5. P1 GitService HEAD-path simplification study + implementation.
6. P2 ExpandNewNodesStateStrategy simplification.
7. P2 VisualTrackerManager strategy split.
8. P3 separate bridge module.

Why this order:

1. Maximizes line reduction in the two biggest files first.
2. Reduces custom event-handling risk before broader architectural cleanup.
3. Defers highest wiring risk (module split) until runtime behavior is stabilized.

## Validation Plan

For each completed workstream:

1. Run .\gradlew.bat test
2. Run targeted Starter UI tests for touched behavior
3. Run .\gradlew.bat starterUiTest
4. Run .\gradlew.bat verifyPlugin

Additional gates for this pass:

1. Check for new internal API imports under src/main (must be zero new usages).
2. Check for new deprecated API usages in compile output (must be zero new warnings).
3. Ensure capability docs stay aligned when interaction behavior is intentionally simplified.

## Exit Criteria

1. No new internal or deprecated API usage introduced.
2. Material net reduction in production LOC for top-5 largest files.
3. Behavior parity for core capabilities unless explicitly accepted UX simplifications are documented.
4. Full green on test, starterUiTest, and verifyPlugin.
