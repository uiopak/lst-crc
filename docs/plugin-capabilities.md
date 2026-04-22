# Plugin Capabilities

## Purpose

LST-CRC is an IntelliJ Platform plugin for comparing the current working tree against `HEAD`, branches, commits, or per-repository revision targets inside the IDE.

## Main User-Facing Capabilities

- Permanent `HEAD` tab. The tool window always provides a baseline comparison against `HEAD`, even when no extra tabs are open.
- Additional comparison tabs. Users can open closable tabs for branches and specific revisions or commits and optionally rename those tabs to meaningful aliases.
- Git Log integration. Actions in the VCS log let users create a new comparison tab from a selected revision or set the selected revision as the comparison target for one repository inside the active tab.
- Multi-repository comparisons. In multi-root projects, each tab can keep one primary comparison target plus per-repository overrides, so one repository can compare against a different branch or commit than another.
- Dedicated comparison tree plus categorized change data. The tool window shows Git changes in a dedicated comparison tree, while the plugin separately classifies those changes into created, modified, moved, deleted, and changed sets for scopes, coloring, and gutter logic.
- Configurable file actions. Left click, double click, and middle click can be mapped to diff, source, project-view, or no-op actions. Right click can either follow the configured action model or be switched into context-menu mode.
- Branch selection UI. Users can add new comparison tabs from a searchable branch picker and can reopen the same picker from the tool window toolbar or the status bar widget. In multi-root projects, that add-tab picker is populated from the primary repository's branch list.
- Repository comparison controls. A toolbar action shows the current comparison target per repository for the active non-`HEAD` tab and lets users adjust per-repository branch overrides without leaving the tool window. Per-repository revision or commit overrides can also be set from the Git Log.
- Status bar widget. The selected comparison tab label or alias is visible in the IDE status bar, and the widget popup can switch tabs or open the branch-selection flow.
- Custom named scopes. The plugin publishes IntelliJ named scopes for created, modified, moved, deleted, and changed files relative to the active comparison.
- Search-scope integration. Created, modified, moved, and changed scopes appear in Find/Search scope pickers through dedicated search-scope wrappers. Those search scopes reuse the same active diff cache as the named scopes, so they are also empty on the `HEAD` tab unless `Include HEAD in scopes` is enabled. Deleted files remain available only as a named scope because the current search integration path does not enumerate those revision-backed virtual files correctly.
- File coloring and context labels. The changes tree can append comparison context text and apply deleted-file colors based on the active comparison state.
- Custom gutter tracking. The plugin overlays line-status markers against the active comparison target instead of only against `HEAD`, including support for unsaved editor content.
- Automatic refresh. Startup hooks, VFS listeners, changelist listeners, and repository listeners keep the active comparison in sync with local file edits, saves, branch changes, and repository updates.
- Persistent UI state. Open tabs, selected tab, aliases, and per-repository comparison overrides survive IDE restarts through project-level persisted state.
- Recovery and notification flow. If a branch-based comparison target is missing in one repository, the plugin reports the failure and offers a direct path to reconfigure the broken repository target. Commit-hash misses are handled differently and do not trigger the same recovery notification flow.

## Scope Of The Plugin

- The plugin is Git-focused and depends on the `Git4Idea` integration supplied by the JetBrains IDE.
- The active comparison is tied to the currently selected LST-CRC tab, but some consumers are settings-dependent. In particular, scopes and custom gutter overlays on the `HEAD` tab are cleared unless `Include HEAD in scopes` is enabled.
- Deleted-file handling, multi-repo overrides, and unsaved-editor overlays are first-class behaviors, not edge-case add-ons.
- The `Changed` scope intentionally excludes deleted files. Deleted files have their own named scope and dedicated UI handling.
- The plugin does not try to replace the full Git tool window. It adds a comparison-centric workflow optimized around one active diff context that other IDE surfaces can reuse.