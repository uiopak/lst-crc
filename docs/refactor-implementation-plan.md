# Refactor Implementation Plan

Goal: reduce the amount of code the plugin must maintain, remove brittle implementation seams, and fix latent defects without changing current user-visible behavior, plugin capabilities, or custom UI flows.

Non-negotiable constraints:

- Keep all existing features and business logic.
- Do not reduce UI capability, branch-selection behavior, status-widget behavior, or repo-comparison workflows.
- Prefer public, supported IntelliJ Platform APIs.
- Avoid deprecated, internal, and experimental APIs unless there is no supported alternative and the usage is isolated behind one compatibility seam.
- Preserve current automated coverage and adjust tests only when behavior-preserving implementation changes require it.

Plan status:

- Conservative remaining count: 117 plan points.

- File-by-file review completed across production, unit, Remote Robot, Starter, resources, and bridge files.
- Cross-check completed against prior subagent findings and repo memory notes.
- Conflicts reconciled in plan text where a public replacement is not currently available.
- Implemented in this session:
	- Per-content disposers for closable comparison tabs.
	- Disposable branch-selection panel and replacement of temporary selection content instead of in-place repurposing.
	- Tool-window title restore now reads through the shared settings authority instead of PropertiesComponent directly.
	- Starter bridge cleanup: supported VFS refresh helper, direct action invocation, and removal of deprecated VCS accessors.
	- ToolWindowStateService tab-update logic deduplicated and validated with focused persistence tests.
	- VisualTrackerManager now exposes a narrow standalone-tracker accessor, removing bridge reflection for that path.
	- Immutable TabInfo and ToolWindowState carriers, with normalized persistence copies in ToolWindowStateService.
	- Canonical tab lookup helpers in ToolWindowStateService now back factory, helper, and bridge selection flows.
	- Remote Robot settings reset now delegates to LstCrcSettingsService.resetToDefaults() instead of duplicating the default matrix in JS.
	- LstCrcChangesBrowser now exposes a narrow line-stats snapshot helper, removing one more reflective Robot path.
	- ToolWindowSettingsProvider and ToolWindowStateService now use smaller shared helpers for reset/state access instead of duplicating selection and default logic inline.
	- LstCrcChangesBrowser now exposes its tree viewer through an explicit helper, removing reflective viewer lookup from the test bridge.
	- LstCrcSettingsService now centralizes reset-to-default behavior in one ordered reset action list, with focused unit coverage for representative settings round-trips.
	- ToolWindowStateService now centralizes selected-profile naming for refresh logging and refresh-cycle orchestration.
	- VisualTrackerManager now owns the tracker-summary diagnostic seam used by the test bridge, removing direct tracker reflection from bridge code.
	- Added a focused unit test for LstCrcSettingsService reset and getter/setter round-trips.
	- LstCrcProvidedScopes now caches its canonical scope lists instead of rebuilding them on every access.
	- Removed the unused includeInSearchScopes descriptor flag from FileStatusScopes.
	- MyToolWindowFactory now uses one helper for branch-content lookup instead of repeating contentManager scans.
	- ToolWindowHelper now owns the shared branch-content lookup helper used by the factory and selection flows.
	- ToolWindowHelper now owns the shared selection-tab display-name lookup helper used by the branch-selection flow.
	- BranchSelectionPanel now resolves searchable node text through one helper instead of duplicating the same mapping in filter and selection paths.
	- LstCrcUiTestBridge now normalizes paths and matches change file names through shared helpers instead of repeating the same path/file-name logic across tree lookups.
	- ToolWindowStateService now owns a shared single-repository comparison update helper reused by the branch-selection dialog, VCS log action, Starter bridge, and Remote Robot fixture.
	- OpenBranchSelectionTabAction and the status widget now reuse ToolWindowHelper content-lookup helpers instead of rescanning content metadata inline.
	- ShowRepoComparisonInfoAction now routes its single-repo dialog dispatch through one helper instead of repeating the same dialog creation path.
	- Tab display-name reads now reuse a shared non-persisted TabInfo extension instead of repeating alias-or-branch formatting across the state service and tool-window UI.
	- CreateTabFromRevisionAction and RenameTabAction now reuse one alias-normalization helper instead of trimming empty aliases inline in each action.
	- ToolWindowUiCompatibility now owns the ToolWindowEx tab-action cast so MyToolWindowFactory no longer reaches into that UI seam directly.
	- LstCrcUiTestBridge now shares one tree-path text matcher and one change-row matcher instead of repeating the same selected-tree scans across bridge helpers.
	- RenameTabAction now reuses one renamable-tab context helper instead of validating closable tab and branch identity through separate paths.
	- ToolWindowUiCompatibility now documents why title visibility still requires one isolated internal API seam.
	- Remote Robot title-visibility helpers now reuse ToolWindowUiCompatibility instead of touching ToolWindowContentUi internals directly.
	- LstCrcUiTestBridge now routes renderedTreeText() through its existing reflective text helper instead of duplicating a second getText lookup.
	- LstCrcUiTestBridge now builds synthetic repo-comparison dialog snapshots through one helper instead of duplicating the same dialog payload assembly.
	- LstCrcUiTestBridge now shares one full-project Git refresh helper for VCS activation and external-change refresh flows.
	- ToolWindowHelper now owns a shared HEAD-content lookup reused by the status widget and Starter bridge.
	- LstCrcUiTestBridge now reuses ToolWindowHelper for widget-tab selection and display-name tab lookup instead of rescanning content lists inline.
	- LstCrcUiTestBridge now centralizes selected-content display-name reads used for tab state synchronization.
	- ToolWindowSettingsProvider now routes repetitive boolean and int setting reads through shared helpers instead of duplicating settingsService access patterns.
	- Removed the unused duplicate reset-to-default matrix from ToolWindowSettingsProvider so LstCrcSettingsService remains the only reset authority.
	- LstCrcSettingsService now uses typed setting definitions for click-action strings plus reset-time boolean and int defaults instead of repeating raw key/default pairs.
	- LstCrcChangesBrowser now drives click-dispatch rules, context-menu titles, and test lookups from one shared action-definition table, with focused unit coverage for action availability and configured click lookup.
	- Remote Robot resetGitChangesViewState now resolves ToolWindowUiCompatibility through the plugin classloader instead of touching ToolWindowContentUi title internals directly in JS.
	- ToolWindowSettingsProvider boolean toggle actions now read and write through typed LstCrcSettingsService accessors instead of raw key/default triples, with focused widget regression coverage.
	- LstCrcSettingDefinitions now centralizes settings keys and defaults so LstCrcSettingsService, ToolWindowSettingsProvider, tests, and the UI test bridge no longer duplicate raw metadata.
	- LstCrcUiTestBridge now uses typed LstCrcSettingsService accessors for tool-window settings snapshots and mutations instead of raw key/default calls.
	- Added focused scope coverage for canonical LST-CRC scope ids, ordering, and searchable-scope filtering on top of the descriptor-driven scope registry.
	- RepoNodeRenderer now builds trailing revision and line-stat fragments through one shared helper used by both rendering and text snapshot helpers, with focused renderer unit coverage.
	- CreateTabFromRevisionAction, SetRevisionAsRepoComparisonAction, and ShowRepoComparisonInfoAction now reuse shared action-context helpers for selected tab and single-selection resolution instead of repeating those lookups inline.
	- CreateTabFromRevisionAction and RenameTabAction now share one ToolWindowHelper path for tab-alias normalization and state updates instead of normalizing and dispatching alias writes separately in each action flow.
	- LstCrcChangesBrowser now routes toolbar action insertion through one helper, with focused unit coverage for placing the repo-comparison action after the group-by action when present and otherwise appending it.
	- LstCrcChangesBrowser now exposes a minimal file-color test hook so LstCrcUiTestBridge can read deleted-file and scope colors without reflective access to the tree implementation.
	- VisualTrackerManager now exposes one document-scoped tracker summary accessor used by both LstCrcUiTestBridge and the Remote Robot IdeaFrame fixture instead of duplicating native-or-standalone tracker lookup and summary formatting in each test harness.
	- VisualTrackerManager now exposes the full document-scoped gutter summary used by both LstCrcUiTestBridge and the Remote Robot IdeaFrame fixture, so Starter and Remote Robot gutter assertions no longer rebuild the same highlighter-summary string independently.
	- LstCrcUiTestBridge now reads available change context-menu actions through LstCrcChangesBrowser's explicit test hook instead of reconstructing the same titles and deleted-file rule inline.
	- LstCrcUiTestBridge now shares one required selected-browser accessor and one required selected-tree accessor instead of repeating the same missing-selection error path across bridge helpers.
	- LstCrcUiTestBridge now routes project-relative file-path construction and slash normalization through shared helpers instead of repeating inline `replace('\\', '/')` path rewriting across bridge file and scope helpers.
	- LstCrcUiTestBridge now routes repeated repository refresh-and-wait setup through the existing shared Git refresh helper, while still forcing the relevant repository root to refresh before snapshot-dependent dialog flows.
	- LstCrcChangesBrowser and RepoNodeRenderer now expose minimal row-text snapshot hooks so LstCrcUiTestBridge no longer renders selected browser rows through its generic component-text scraper.
	- BranchSelectionPanel now exposes visible branch leaf texts for tests so LstCrcUiTestBridge can read branch selection snapshots without rendering panel trees through the generic tree-text scraper.
	- ToolWindowSettingsProvider now builds mouse click action groups from shared click-setting and action-choice definitions instead of wiring each submenu inline.
	- ToolWindowSettingsProvider now drives double-click speed options from one shared delay-choice list instead of rebuilding the label/value matrix inline.
	- ToolWindowSettingsProvider now routes primitive setting writes used by menu actions through shared helpers instead of calling raw service setters inline.
	- ToolWindowSettingsProvider now builds its mouse-click submenu and tree-view submenu through dedicated helpers so the root settings group only assembles top-level structure.
	- ToolWindowSettingsProvider now builds its gutter submenu and general settings actions through dedicated helpers so createToolWindowSettingsGroup stays structural.
	- LstCrcSettingsService now exposes typed accessors for representative boolean settings and stored double-click delay instead of forcing tests and nearby callers through raw key/default pairs.
	- ToolWindowSettingsProvider now reuses typed service accessors for representative settings instead of mixing raw key/default reads with typed click-action access.
	- Removed dead generic primitive helper reads from ToolWindowSettingsProvider after the typed accessor migration.
	- LstCrcStatusWidget now routes popup tool-window activation through one helper instead of repeating lookup and activate plumbing in each popup action.
	- LstCrcStatusWidget now assembles popup actions through one helper instead of building the action list inline in the click consumer.
	- MyToolWindowFactory now routes selected-tab synchronization from the content-manager listener through one helper instead of keeping that branch logic inline in the listener.
	- ToolWindowHelper now reuses one helper for selecting an existing branch-selection tab and refocusing its search field instead of duplicating that reuse path before and after async branch loading.
	- ToolWindowHelper now reuses one helper for branch-content creation, selection, and state registration instead of duplicating that sequence for normal tab creation and selection-tab replacement.
	- MyToolWindowFactory now routes state-driven tab display-name synchronization through one helper instead of keeping that update loop inline in the message-bus subscriber.
	- MyToolWindowFactory now restores persisted tabs through a dedicated helper instead of mixing persisted restore and initial-branch creation logic in one method.
	- ToolWindowHelper now owns the shared LST-CRC tool-window activation path used by the revision-tab action and status widget instead of repeating tool-window lookup and activate plumbing.
	- ToolWindowHelper now centralizes branch-selection tab naming and content lookup instead of rebuilding that label and lookup path in multiple runtime call sites.
	- ToolWindowHelper display-name lookup now scans content collections directly, fixing proxy-backed action tests while matching the helper's other content-resolution paths.
	- Removed the dead selectionTabName callback parameter from ToolWindowHelper after centralizing branch-selection tab lookup.
	- CreateTabFromRevisionAction now isolates alias prompting behind one helper instead of mixing dialog construction into the orchestration path.
	- BranchSelectionPanel now routes branch-tree search matching through one helper instead of repeating the same searchable-text predicate in clone and selection paths.
	- BranchSelectionPanel now centralizes snapshot-vs-repository branch list fallback and tree-path node casting through shared helpers instead of repeating that local/remote and mouse/keyboard wiring inline.
	- BranchSelectionPanel now applies branch filtering synchronously on the EDT from its stable full-tree snapshot instead of routing local filter work through a disposable coroutine job.
	- Added focused BranchSelectionPanel unit coverage for filter-first selection, Enter-key submission, and reopened-panel snapshot reset behavior.
	- Remote Robot branch-selection coverage now verifies filter and reopen behavior directly, and the fixture selects filtered slash-form branches by visible leaf label instead of assuming the full branch path is rendered as one text fragment.
	- RepoNodeRenderer now renders added and removed trailing line-stat fragments through shared helpers instead of repeating separator bookkeeping inline.
	- LstCrcStatusWidget now routes HEAD and branch popup tab selection through one helper instead of repeating tool-window activation and content-selection plumbing in each action.
	- ExpandNewNodesStateStrategy now restores expanded, collapsed, and selected tree paths manually instead of delegating to `TreeState.applyTo()`, because IntelliJ recenters the selected row during generic restore and breaks comparison-tree viewport preservation after local edits; focused browser and Remote Robot regressions cover the offscreen-selection and selected-untracked-file cases.
	- LstCrcChangesBrowser now resolves pointer-targeted tree changes through shared helpers instead of repeating the same tree-path-to-change lookup in click and context-menu handlers.
	- LstCrcChangesBrowser now routes single-click and double-click button-setting lookup through one helper instead of duplicating the same mouse-button matrix inline.
	- LstCrcChangesBrowserTest now reuses one browser fixture helper instead of repeating the same browser, frame, and scroll-pane setup across viewport regression tests.
	- ShowRepoComparisonInfoAction now builds the multi-repository popup and repo action group through dedicated helpers instead of keeping that assembly inline in actionPerformed.
	- MyToolWindowFactory now builds the tool-window gear action group through one helper instead of assembling that wrapper inline in setupToolWindowActions.
	- MyToolWindowFactory now routes its HEAD fallback selection through one helper instead of duplicating HEAD selection and `selectedTabIndex = -1` synchronization inline.
	- MyToolWindowFactory now resolves the initial branch name through one helper instead of keeping the persisted-state conditional inline in the pooled startup task.
	- LstCrcStatusWidget now resolves selected-tab display text through one helper instead of keeping the selected-index formatting branch inline in getText().
	- LstCrcUiTestBridge now routes selected-content selection plus state synchronization through one helper instead of repeating that UI/state pair in individual tab-selection helpers.
	- LstCrcUiTestBridge now routes selected-content component and display-name reads through shared selected-content helpers instead of casting and reading selected content directly in multiple helpers.
	- ToolWindowHelper now routes both project-based and direct tool-window activation through one shared overload instead of leaving branch-selection tab activation on a separate direct `toolWindow.activate` path.
	- LstCrcScopeCollections now owns search-scope mapping from named scopes to IDE search scopes so both scope providers share the same scope surface instead of keeping that conversion inline in LstCrcSearchScopeProvider.
	- LstCrcProvidedScopes now directly owns both named-scope and search-scope sharing, removing the thin LstCrcScopeCollections wrapper so both providers read from the same source.
	- ToolWindowHelper no longer threads the branch-selection tab name through helper calls now that branch-selection naming is centralized.
	- CreateTabFromRevisionAction now routes revision-tab creation plus alias application through one helper instead of leaving that orchestration block inline inside the activation callback.
	- ShowRepoComparisonInfoAction now uses imported local types for its extracted helpers instead of repeating fully qualified project, repository, and tab-info types.
	- LstCrcStatusWidget now shares one select-or-log helper for popup tab actions instead of duplicating the same content-selection and warning flow for HEAD and branch tabs.
	- LstCrcStatusWidget now routes add-tab popup behavior through one helper instead of inlining the branch-selection tab opener inside the activation callback.
	- SingleRepoBranchSelectionDialog now applies the selected branch through one helper instead of keeping repo-comparison state updates inline in doOKAction().
	- SingleRepoBranchSelectionDialog now builds its branch-selection panel through one helper instead of keeping callback wiring inline in createCenterPanel().
	- ToolWindowStateService now shares one display-name matching helper instead of repeating branch-or-alias matching in both display-name lookup methods.
	- ToolWindowStateService now shares one post-update logging helper across alias, comparison-map, and repo-comparison mutations instead of repeating the same “updated state” tail in each method.
	- ToolWindowStateService now routes alias, comparison-map, and repo-comparison entry points through one shared mutation wrapper instead of repeating disposed checks, start logs, and `updateTab(...)` wiring in each method.
	- ToolWindowStateService now builds repository comparison override maps through one helper instead of leaving repo-map rewrite logic inline in updateTabRepoComparison().
	- ToolWindowStateService now routes display-name tab lookup through the indexed lookup helper instead of performing a second standalone scan.
	- LstCrcStatusWidget popup actions now use the owning widget project directly and call branch-selection opening through ToolWindowHelper instead of threading redundant project wrappers through inner actions.
	- CreateTabFromRevisionAction now uses local Project and ToolWindow imports in its helper signatures instead of carrying fully qualified IntelliJ types.
	- MyToolWindowFactory now uses imported Content and ContentManager types in its helper signatures instead of mixed fully qualified content API references.
	- LstCrcStatusWidget now uses imported Content and ContentManager types in its selection helper instead of fully qualified content API references.
	- RepoNodeRenderer now resolves ToolWindowStateService through a local import instead of a fully qualified service lookup.
	- LstCrcChangesBrowser now registers its parent disposable through the imported Disposer API instead of a fully qualified disposal call.
	- LstCrcUiTestBridge now uses local DumbService and StatusBarWidget imports instead of fully qualified IntelliJ type references in its bridge helpers.
	- LstCrcUiTestBridge now uses local JBColor, Document, and AnActionEvent imports in its bridge helpers instead of fully qualified IntelliJ type references.
	- SetRevisionAsRepoComparisonAction now shares one single-commit visibility helper instead of resolving commit-selection cardinality inline in update().
	- CreateTabFromRevisionAction now shares one selected-revision resolver instead of reading the single selected revision separately in update() and actionPerformed().
	- ShowRepoComparisonInfoAction now shares one selected-tab lookup helper instead of resolving ToolWindowStateService state separately in update() and actionPerformed().
	- SetRevisionAsRepoComparisonAction now shares one selected-tab lookup helper instead of resolving ToolWindowStateService state separately in update() and actionPerformed().
	- LstCrcStatusWidget now resolves the selected open tab through one helper instead of open-coding selected-index bounds inside widget text rendering.
	- ToolWindowStateService now routes branch-name and display-name index lookups through one shared predicate-based tab-index helper instead of scanning open tabs separately in each method.
	- ToolWindowStateService now resolves selected tabs through `getOrNull(...)` instead of open-coding selected-index bounds in its private selected-tab accessor.
	- ToolWindowStateService now shares one normalized state-publishing helper instead of duplicating the same message-bus publish call in commit and explicit broadcast paths.
	- LstCrcSettingsService now shares one stored-value lookup helper instead of repeating the same non-blank state-map gate in string, boolean, and int getters.
	- LstCrcSettingsService now resets defaults from typed string, boolean, and int definition lists instead of a hand-maintained reset lambda matrix.
	- ToolWindowSettingsProvider now shares one VisualTrackerManager settings-change callback helper instead of repeating the same gutter refresh lambda across gutter toggles.
	- ToolWindowSettingsProvider now builds right-click behavior radio actions from metadata instead of open-coding the two context-menu mode toggles.
	- ToolWindowHelper now routes branch-name, display-name, and HEAD content lookup through one shared content-scan helper instead of scanning contentManager contents separately in each method.
	- RepoNodeRenderer now shares one default target-revision fallback helper instead of repeating the same active-branch or selected-tab fallback chain in single-repo and per-repository resolution.
	- RepoNodeRenderer now shares one categorized-changes fallback helper path instead of repeating the same provider-or-diff-service selection in comparison-context and line-stats accessors.
	- BranchSelectionPanel now builds local and remote category nodes through one shared helper instead of repeating the same category-node assembly flow twice in its full tree model builder.
	- Scope registry ordering and searchable filtering now reuse the shared provided-scope lists instead of duplicating metadata.
	- Removed write-unsafe Remote Robot git initialization pre-save path by deleting saveAllDocuments() usage from PluginUiTestSteps.
	- Validated Remote Robot coverage with deterministic class-by-class runs and persisted logs for all five UI test classes.
	- Narrowed Starter suite breakage to a single failing restart-persistence assertion in multi-root Starter coverage.
	- Hardened the Starter restart-persistence test flow by waiting for restored tab presence first, then selecting and asserting restored alias/comparison state.
	- Fixed persisted state deserialization bug for restart flows by restoring writable state properties required by XMLB for ToolWindowState and TabInfo.
	- LstCrcStatusWidget now refreshes through one explicit `refresh(project)` helper from the settings provider, Starter bridge, and startup path, removing redundant widget-only settings-topic plumbing.
	- Status-widget lookups now reuse `LstCrcStatusWidget.ID` across the Starter bridge and Remote Robot fixture, with focused unit coverage that keeps the `plugin.xml` widget-factory id aligned with the widget constant.
	- Remote Robot fixture setting writes now reuse `LstCrcSettingDefinitions` keys instead of duplicating raw setting ids inline.
	- IdeaFrame now routes status-widget JS reads through one shared widget-lookup helper instead of repeating the same project/status-bar/widget lookup across widget text and popup snapshot helpers.
	- IdeaFrame boolean and int setting writes now reuse one shared `LstCrcSettingsService` JS helper instead of rebuilding the same plugin-classloader lookup in each setter path.
	- IdeaFrame now shares one settings-service lookup statement block between reset-state and click-settings snapshot helpers instead of duplicating the same plugin-classloader and service lookup inline.
	- IdeaFrame string setting writes now reuse the same shared `LstCrcSettingsService` JS helper while preserving the existing `PropertiesComponent` fallback path.

## Phase 0: Guardrails And Sequencing

1. Create an implementation branch checkpoint in local workflow notes before changing runtime behavior.
2. Keep the plan file updated as slices land so the refactor does not drift.
3. Execute runtime refactors in thin slices with focused validation after each slice.
4. Prefer production simplification before large test-harness rewrites.
5. Preserve current plugin.xml registrations until the replacement code is validated.
6. Avoid parallel edits across unrelated subsystems when a targeted validation exists.
7. Record any unavoidable internal API usages in repo memory with justification.
8. Keep compatibility seams localized instead of scattering special cases.
9. Do not merge test-harness changes with runtime changes unless the runtime change forces it.
10. Run verifyPlugin separately from Starter tasks to avoid bridge-class false positives.

## Phase 1: Persistent State Model Simplification

11. Convert TabInfo into a strictly immutable state carrier.
12. Remove mutable map mutation patterns from TabInfo update flows.
13. Normalize TabInfo comparisonMap writes through one helper path.
14. Convert ToolWindowState into a strictly immutable state carrier.
15. Remove ad hoc state-copy boilerplate scattered across ToolWindowStateService.
16. Migrate ToolWindowStateService to SerializablePersistentStateComponent if serialization shape remains stable.
17. Keep selectedTabIndex semantics exactly as today, including HEAD as -1.
18. Encode HEAD special handling once instead of repeating it in selection helpers.
19. Centralize open-tab add, remove, rename, alias, and comparison-map updates.
20. Replace branchName-or-alias scans with one canonical tab-resolution helper.
21. Collapse duplicated “selected tab info” resolution paths between service, widget, and bridge.
22. Reconcile persistence writes so they only happen after state is actually changed.
23. Remove redundant temporary collections created only to reassign state.
24. Keep persisted XML format compatible where practical; if incompatible, add migration logic.
25. Add unit tests for alias update, close-tab behavior, HEAD selection, and comparison-map replacement on immutable state.

## Phase 2: ToolWindowStateService Orchestration Cleanup

26. Narrow ToolWindowStateService responsibilities to state orchestration and refresh sequencing.
27. Extract pure tab-state mutation helpers out of refresh-heavy code paths.
28. Remove duplicated selected-tab fallback logic.
29. Centralize refreshDataForCurrentSelection entry points.
30. Eliminate duplicate EDT hops between state updates and browser refreshes.
31. Ensure refresh ordering stays deterministic when tabs are rapidly switched.
32. Prevent stale async refresh completions from overwriting newer selection state.
33. Collapse repeated “resolve HEAD vs tab” conditionals into one branch.
34. Replace repeated tool-window content scans with cached intent where safe.
35. Revisit message-bus emissions and keep only those required by external consumers.
36. Add tests for rapid selection changes and stale refresh suppression.
37. Add tests for branch alias selection consistency after persistence reload.
38. Ensure refresh entry points remain safe when tool window is not yet initialized.
39. Ensure service initialization does not eagerly touch UI on background startup.
40. Keep unit coverage focused on orchestration and avoid overtesting UI details here.

## Phase 3: Active Diff Snapshot And Messaging Cleanup

41. Keep ProjectActiveDiffDataService as the single source of truth for the active diff.
42. Remove duplicate active-diff bookkeeping that mirrors service state elsewhere.
43. Publish immutable diff snapshots instead of exposing mutable collections.
44. Ensure scope readers can consume diff data safely from background threads.
45. Remove redundant file-set recomputation when current selection has not changed.
46. Normalize created, modified, moved, and deleted collections through one snapshot builder.
47. Collapse repeated invalidation triggers into one explicit diff-updated flow.
48. Reassess whether all current topics in LstCrcTopics are still needed.
49. Keep DIFF_DATA_CHANGED_TOPIC only if external consumers still require it directly.
50. Remove no-op or duplicated topic plumbing if direct service refresh hooks suffice.
51. Add tests for diff snapshot replacement, empty diff handling, and multi-repo snapshot publishing.
52. Add tests that confirm scopes observe the same active snapshot the UI uses.

## Phase 4: GitService Simplification Without Feature Loss

53. Keep GitService as the only production layer that talks to Git4Idea and low-level git.
54. Split pure diff categorization helpers from repository-discovery helpers.
55. Extract branch snapshot assembly from tab refresh orchestration.
56. Consolidate repo-comparison target resolution for single-repo and multi-repo cases.
57. Reuse one helper for revision tabs and branch tabs where semantics match.
58. Avoid repeated repository sorting or scanning within one refresh cycle.
59. Preserve unsaved-document handling for line stats and overlays.
60. Preserve current overlay-merge logic for new files and deleted files.
61. Keep Git work off the EDT everywhere, especially repository lookups triggered from editor listeners.
62. Simplify line-stat result types if current wrappers only forward data.
63. Reduce duplicate branch-snapshot deduplication logic.
64. Reassess revision content wrapper usage and replace with public simpler classes only where semantics stay identical.
65. Keep commit and revision naming behavior exactly stable for UI and tests.
66. Add focused tests around comparisonMap precedence, multi-root target resolution, and unsaved-document overlays.

## Phase 5: Plugin Startup And VCS Listener Cleanup

67. Reduce startup wiring duplication between PluginStartupActivity and listener services.
68. Keep early initialization behavior that makes scopes, widget, and gutter state ready on project open.
69. Extract one startup registration path for listeners and refresh kicks.
70. Remove any redundant first-refresh calls once the canonical startup sequence is defined.
71. Ensure VcsChangeListener never performs repository lookup work on the EDT.
72. Collapse duplicated dirty-scope and refresh scheduling triggers.
73. Keep background refresh behavior resilient when VCS mappings change mid-session.
74. Add tests for startup-first-refresh ordering where covered by current test shape.
75. Add regression coverage for repository-change events not blocking editor callbacks.

## Phase 6: Tool Window Content Lifecycle And Disposal

76. Bind every closable content tab to its own disposer via Content.setDisposer.
77. Stop using the tool-window disposable for every closable browser instance.
78. Ensure closing a tab disposes only that tab’s browser resources.
79. Ensure HEAD content disposal remains owned by the tool window lifecycle.
80. Remove the in-place repurpose path for the temporary branch-selection content if separate content creation is cleaner.
81. Make BranchSelectionPanel disposable if it owns coroutine scope or listeners.
82. Dispose branch-selection resources when the temporary tab closes or is replaced.
83. Recheck branch-selection tab existence on the EDT before creating a new one.
84. Collapse duplicated content lookup helpers for HEAD, branch tabs, and temporary tabs.
85. Ensure content display names always match resolved alias/branch state after rename.
86. Simplify content creation so content metadata is attached once and reused.
87. Keep the permanent HEAD tab creation path obvious and isolated.
88. Add focused tests for close-tab disposal and branch-selection-tab uniqueness where feasible.

## Phase 7: MyToolWindowFactory And ToolWindowHelper Simplification

89. Split MyToolWindowFactory into distinct responsibilities: create HEAD content, restore saved tabs, restore title visibility, install listeners.
90. Collapse duplicated browser creation code paths between initial creation and restore.
91. Replace repeated contentManager scans with explicit helper methods.
92. Move branch-selection tab opening, tab creation, and selection synchronization into clearer helper units.
93. Ensure restore logic uses persisted state rather than UI scans as the authority.
94. Remove duplicated alias-display-name formatting logic.
95. Preserve existing tab order semantics across reloads.
96. Keep tool-window title visibility behavior unchanged while isolating unsupported UI seam usage.
97. Use one compatibility seam for ToolWindowContentUi client property handling.
98. Do not spread impl-package imports for title visibility beyond that seam.
99. Add tests for restored tabs, alias display, and title-visibility state restoration if possible.

## Phase 8: BranchSelectionPanel Cleanup

100. Keep the embedded searchable branch-selection UX; do not regress to a simpler modal-only flow.
101. Make filtering logic operate on stable snapshots instead of live Swing tree mutation from background work.
102. Simplify any coroutine usage that only debounces local filter changes.
103. Replace custom delayed filter machinery with a smaller, lifecycle-safe approach.
104. Dispose listeners, jobs, and models when the panel is removed.
105. Normalize branch list creation and grouping so local and remote branches share one rendering path.
106. Remove duplicated tree-expansion and selection restoration logic.
107. Keep keyboard navigation and search focus behavior unchanged.
108. Extract helper methods for selected-branch resolution and submission.
109. Add focused UI tests for filter, selection, and reopen behavior.

## Phase 9: LstCrcChangesBrowser Cleanup

110. Keep custom click-action behavior, context-menu behavior, and deleted-file handling intact.
111. Remove duplicate repository-refresh subscriptions if project-level orchestration already covers them.
112. Consolidate toolbar setup into one place.
113. Replace reflective bridge access to browser actions with explicit test-support entry points where sensible.
114. Expose only minimal public-for-testing helpers instead of broad new API.
115. Simplify click-handler dispatch so left, middle, right, and double-click rules read from one mapping table.
116. Avoid rebuilding the view when only non-visual state changes.
117. Reuse one helper for open source, show diff, and show in project tree pathways where possible.
118. Keep show-in-project-tree unavailable for deleted changes.
119. Preserve custom context-menu labeling from bundle keys.
120. Add tests for click behavior and context-menu behavior after simplification.

## Phase 10: Widget And Tool Window Settings Cleanup

121. Fix any widget factory id mismatch so registration and lookup share one source of truth.
122. Keep the current widget text and tab-selection behavior unchanged.
123. Reevaluate whether the widget can use MultipleTextValuesPresentation without breaking tests or UX.
124. If not, keep the current widget structure and just simplify state reads.
125. Remove redundant settings-topic plumbing where direct widget refresh is enough.
126. Move LstCrcSettingsService to a typed state model instead of ad hoc primitive accessors.
127. Centralize all settings keys, defaults, and metadata into one definition table.
128. Generate repetitive boolean and int accessors from shared helpers where practical.
129. Keep ToolWindowSettingsProvider as the one UI-facing settings authority.
130. Remove duplicated key/default references from tests and bridge by reusing the central metadata.
131. Ensure setting changes still publish any topic events relied on by widget or gutter consumers.
132. Add tests for default values, persistence, and widget text changes.

## Phase 11: Scope And Search Scope Simplification

133. Preserve every current named scope and search scope display name.
134. Replace FileStatusScopes boilerplate with a descriptor-driven definition list.
135. Build named scopes and search scopes from the same canonical descriptors.
136. Keep public scope classes only where tests or IDE registrations need them.
137. Use one canonical “changed files” composition rule instead of recomputing union logic in multiple places.
138. Ensure include-HEAD behavior remains governed only by settings.
139. Reduce holder/provider duplication between LstCrcScopeProvider and LstCrcSearchScopeProvider.
140. Keep thread-safety around active diff file membership checks.
141. Add tests for every scope id, display name, and membership behavior after deduplication.

## Phase 12: Gutter And Visual Tracker Simplification

142. Keep current gutter-marker behavior, including the standalone new-file tracker path.
143. Simplify VisualTrackerManager lifecycle and dispose stale trackers deterministically.
144. Prevent older async content application from overwriting newer tracker state.
145. Keep production fallback for whole-new-file INSERTED state unless a supported API fully covers it.
146. Remove duplicate tracker lookup logic where LineStatusTrackerManager already provides the normal case.
147. Expose a minimal diagnostic accessor for tests if that removes reflection from bridge and Remote Robot fixtures.
148. Reuse one summary or accessor path between Starter and Remote Robot gutter assertions.
149. Keep line-status rendering semantics unchanged for modified, inserted, and deleted ranges.
150. Add tests for stale tracker cleanup and new-file tracker visibility.

## Phase 13: Repo Node Rendering And Tree Presentation Cleanup

151. Keep current visual labels, colors, and context metadata in the tree.
152. Preserve child-component-based rendering where UI tests depend on visible trailing metadata.
153. Reduce duplicated string formatting in RepoNodeRenderer.
154. Centralize color selection and line-stat text formatting.
155. Simplify ExpandNewNodesStateStrategy so state restoration logic is easier to reason about.
	- Keep `restoreState(...)` on the manual path. Reintroducing `TreeState.applyTo()` pulls the selected row back into view through `TreeUtil.showRowCentered(...)` and reintroduces the scroll-reset regression.
156. Keep the current “expand new files in collapsed directories” feature exact.
157. Add tests for rendered text snapshots and expansion behavior.

## Phase 14: Actions Cleanup

158. Keep all current actions and visibility rules.
159. Share helper logic between CreateTabFromRevisionAction and SetRevisionAsRepoComparisonAction where possible.
160. Use public VCS log action APIs where they simplify behavior without feature loss.
161. Keep single-commit selection assumptions explicit in action code and tests.
162. Simplify RenameTabAction by reusing a central rename validation helper.
163. Keep custom rename popup behavior unless a public chooser gives identical UX.
164. Simplify OpenBranchSelectionTabAction to delegate directly to ToolWindowHelper.
165. Keep ShowRepoComparisonInfoAction behavior intact while reducing duplicated repo-choice setup.
166. Preserve single-repo fast path and multi-repo dialog behavior.
167. Add or update visibility tests for every action after helper consolidation.

## Phase 15: Tool Window UI Compatibility Seam

168. Keep ToolWindowUiCompatibility as the only production location that touches unsupported tool-window title APIs.
169. Move any stray title-visibility logic from bridge or factory into that seam if production code still duplicates it.
170. Document why no supported public API exists today.
171. Keep bridge usage aligned with the same seam where practical.
172. Do not introduce new internal UI dependencies outside that file.

## Phase 16: Plugin Resources And Registration Cleanup

173. Keep plugin.xml registrations stable while refactoring underlying classes.
174. Remove redundant attributes in plugin.xml only if they are provably unused.
175. Keep bundle keys in LstCrcMessages.properties complete and aligned with code.
176. Remove duplicate or obsolete message keys after code cleanup.
177. Keep README plugin description block in sync if marketplace-facing description changes.
178. Validate plugin.xml references after any class/package moves.

## Phase 17: Bridge Simplification And Public API Cleanup

179. Keep LstCrcUiTestBridge as the Starter test control surface.
180. Replace refreshBaseDir implementation with VfsUtil.markDirtyAndRefresh.
181. Stop using ActionUtil.performAction in the bridge; call action.actionPerformed(event) directly after a proper update path if needed.
182. Replace deprecated setDirectoryMapping patterns by continuing to manage directoryMappings lists directly.
183. Centralize Git activation and refresh sequences to remove repeated waitForUpdate blocks.
184. Replace duplicated path normalization helpers with one shared helper.
185. Remove repeated selected-browser and selected-tree search patterns where one helper suffices.
186. Replace browser private-method reflection for openDiff, openSource, and showInProjectTree with explicit test-support hooks where possible.
187. Replace reflection into currentChanges with either a production accessor or tree-derived snapshot logic.
188. Replace reflection into VisualTrackerManager.visualTrackers with a narrow diagnostic API or shared helper.
189. Keep title-visibility checks isolated because supported public API is not available.
190. Reduce javaClass.name heuristics for diff detection if a stronger public signal exists.
191. Consolidate repeated Change file-name matching logic into one helper.
192. Consolidate repeated repo-comparison dialog snapshot logic.
193. Simplify rendered tree text extraction without regressing child-component capture.
194. Keep fragment-recursion behavior because renderer child text is required by tests.
195. Remove reflection branches only when an equally expressive public/test-support alternative exists.
196. Add bridge-focused smoke tests through Starter scenarios after each major bridge simplification slice.

## Phase 18: Remote Robot Fixture Consolidation

197. Keep the Remote Robot suite; do not delete coverage simply because Starter also exists.
198. Extract shared menu, tree, and settings-reset helpers from IdeaFrame, fixtures, and PluginUiTestSteps.
199. Reuse one canonical settings-reset source shared with the bridge if feasible.
200. Centralize change-tree text extraction logic so Remote Robot and Starter do not drift.
201. Replace brittle JS snippets with fewer, well-named helper entry points.
202. Keep branch-selection fixture behavior and accessible-name assumptions stable.
203. Simplify project-manager fixture setup and teardown paths.
204. Unify wait-for-idle and refresh helpers where semantics match.
205. Remove duplicated git identity constants if they match Starter equivalents.
206. Add fixture-level helpers instead of copying interaction sequences into individual tests.

## Phase 19: Starter Harness Consolidation

207. Keep Starter test coverage, including performance smoke and multi-root scenarios.
208. Extract a shared session runner from LstCrcStarterUiTestBase for pre-seeded projects.
209. Add a helper for activating already-initialized git repositories.
210. Replace custom polling helper loops with the remote-driver waitFor helper where equivalent.
211. Remove redundant waitForSmartMode after waitForIndicators only if validation confirms no startup regressions.
212. Remove redundant project trust setup if Starter context already applies it.
213. Collapse repeated tab creation plus selection-wait sequences into one helper.
214. Extract shared worktree and multi-repo fixture builders.
215. Replace stringly typed snapshot assertions with typed bridge predicates where that reduces brittleness.
216. Keep CLI-based git/worktree setup where Starter public helpers are not expressive enough.
217. Prefer Starter ProcessExecutor over bespoke ProcessBuilder wrappers when semantics match.
218. Use Starter Git helpers selectively rather than force-fitting every custom git action into them.
219. Keep performance smoke as threshold-based smoke, not benchmark-style infrastructure, unless a separate benchmark suite is introduced.
220. Align Starter and Remote Robot scenario coverage so both suites test different value, not the same boilerplate.

## Phase 20: Unit Test Simplification And Expansion

221. Keep unit tests for state, scopes, widget, actions, renderer, GitService, and listeners.
222. Remove duplicated test data builders by centralizing shared fixtures like DiffDataTestSupport.
223. Convert repeated snapshot assembly into named helper functions.
224. Keep coverage of behavior-specific edge cases such as HEAD inclusion, aliasing, and multi-root comparison maps.
225. Expand tests where new immutable-state helpers justify smaller direct unit coverage.
226. Avoid overly broad integration assertions when a pure helper test is enough.
227. Add tests around per-content disposal if the runtime implementation changes there.
228. Add tests around stale async refresh suppression.
229. Add tests around centralized settings metadata to prevent key/default drift.
230. Add tests for any new public-for-testing accessors introduced to remove reflection.

## Phase 21: Validation And Release Gates

231. After each runtime slice, run the narrowest unit tests that can falsify the change.
232. After each test-harness slice, run only the affected unit or UI subset first.
233. Run the full non-UI test suite after major production refactor milestones.
234. Run verifyPlugin in its own Gradle invocation.
235. Run Starter suites only after bridge and related production slices stabilize.
236. Run Remote Robot slices only when fixture or bridge changes affect them.
237. Keep a final validation pass of test, verifyPlugin, and targeted UI suites before considering the refactor complete.
238. If any public-API replacement subtly changes behavior, prefer the original behavior over the cleaner implementation.
239. Stop and isolate regressions instead of compensating with broader changes.
240. Update this plan file after each completed slice so remaining work stays accurate.

## Phase 22: Explicit Non-Actions And Conflict Resolution

241. Do not remove the custom embedded branch-selection tab in favor of a simple generic chooser.
242. Do not replace the rename popup if the public alternative worsens UX or tests.
243. Do not remove the standalone new-file visual tracker path unless a public replacement preserves identical behavior.
244. Do not spread internal tool-window title APIs outside one seam; current platform still lacks a supported replacement.
245. Do not replace CLI git setup in Starter where public helpers do not cover nested repo and worktree flows correctly.
246. Do not remove Remote Robot coverage just because Starter covers similar scenarios.
247. Do not collapse all UI suites into one harness if it increases brittleness or hides harness-specific failures.
248. Do not rewrite large stable areas just to achieve theoretical purity without a concrete line-count or reliability win.
249. Do not accept a line-count reduction that makes tests less expressive or production code harder to debug.
250. Do not merge this refactor until validation confirms that UI behavior, scope behavior, widget behavior, and multi-root workflows are unchanged.

## Recommended Implementation Order

251. Land content disposal and branch-selection lifecycle cleanup first.
252. Land immutable state and ToolWindowStateService cleanup second.
253. Land settings metadata and settings service cleanup third.
254. Land scope descriptor deduplication fourth.
255. Land bridge public-API cleanup fifth.
256. Land gutter diagnostic unification sixth.
257. Land Starter and Remote Robot fixture consolidation seventh.
258. Land low-risk plugin.xml and resource cleanup near the end.
259. Run full non-UI validation after each of the first four milestones.
260. Run verifyPlugin and targeted UI suites after milestones five through seven.