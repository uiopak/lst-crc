# Plugin Capabilities

## Purpose

LST-CRC is an IntelliJ Platform plugin for comparing the current working tree against `HEAD`, branches, commits, or per-repository revision targets inside the IDE.

## Capability Inventory

### C1. Comparison tabs and comparison identity

- `C1.1` Permanent `HEAD` baseline tab.
	- The tool window always exposes a non-closable `HEAD` comparison, and falling back to `HEAD` is the default empty-state behavior.
- `C1.2` Branch comparison tabs.
	- Users can open closable tabs that compare the working tree against a branch tip.
- `C1.3` Revision comparison tabs.
	- Users can create a comparison tab from a VCS Log revision or commit selection.
- `C1.4` Multiple comparison tabs.
	- Several comparison tabs can coexist, and the selected tab defines the active diff consumed by the rest of the plugin.
- `C1.5` Tab aliases.
	- Closable comparison tabs can be renamed with aliases, and the alias becomes the display identity used by the tool window, widget, and persisted state.
	- Rename affordances are exposed only for closable comparison tabs that carry a persistent branch or revision identity.

### C2. Comparison target selection and reconfiguration

- `C2.1` Searchable branch picker.
	- Users can open the add-tab branch picker from the tool window or the status-bar widget.
	- The add-tab affordance hides itself while the temporary branch-selection tab is already open.
- `C2.2` Primary-repository branch sourcing in multi-root projects.
	- In multi-root projects, the add-tab branch picker is populated from the primary repository branch list.
- `C2.3` Repository comparison toolbar dialog.
	- Active non-`HEAD` tabs can show the current comparison target for each repository and let the user change per-repository branch targets.
	- Per-repository overrides affect only the selected repository root inside the active tab.
	- Per-repository overrides can coexist with one primary tab target.
- `C2.4` VCS Log per-repository revision targeting.
	- A selected VCS Log revision can become the comparison target for one repository inside the active tab.
	- Revision-based targeting and branch-based targeting share the same per-root comparison map.
	- The Git Log repo-comparison action is only available when exactly one commit is selected and a comparison tab is active.
- `C2.5` Missing-branch repair flow.
	- If a branch target disappears in one repository, the plugin surfaces a notification that routes the user back to repository-level reconfiguration.
	- The repair flow can reset only the broken repository root instead of discarding the whole tab.
- `C2.6` Missing-commit handling.
	- Missing commit hashes are treated differently from missing branches and do not follow the same warning-and-repair flow.

### C3. Active diff consumers and file-state decisions

- `C3.1` Comparison tree with categorized change data.
	- The tool window renders created, modified, moved, deleted, and otherwise changed files from the active comparison.
	- New or local-only files appear as created entries.
	- Content edits appear as modified entries.
	- Renames and moves appear as moved entries.
	- Missing files relative to the selected target appear as deleted entries.
	- Mixed-state tabs can surface several file states at once across one active comparison.
- `C3.2` Custom named scopes.
	- The plugin publishes named scopes for Created, Modified, Moved, Deleted, and Changed files from the active diff.
	- `Created` tracks new files.
	- `Modified` tracks content edits.
	- `Moved` tracks rename and move results.
	- `Deleted` uses deleted-path matching so revision-backed deleted files still match.
	- `Changed` aggregates created, modified, and moved, and intentionally excludes deleted files.
- `C3.3` Search-scope wrappers.
	- Created, Modified, Moved, and Changed scopes are available in Find/Search scope pickers and respect the active comparison selection.
	- Wrapper membership follows the same active diff cache as the named scopes.
	- Search-scope wrappers exclude library content.
	- Deleted files are intentionally omitted from Find/Search scope publication.
- `C3.4` Deleted-file special handling.
	- Deleted files remain available through dedicated deleted handling and stay out of the `Changed` aggregate scope/search-scope path.
	- Deleted-file handling uses deleted-path caches instead of ordinary live-file identity.
- `C3.5` Tree context labels.
	- Tree rows can show comparison context for single-repository tabs, multi-repository overrides, and revision-based comparisons.
	- Single-repository branch tabs, multi-root override tabs, and revision tabs each have separate rendering decisions.
- `C3.6` Deleted-file coloring.
	- Deleted entries use dedicated deleted coloring in the comparison tree.
- `C3.7` Visual gutter tracking.
	- Line-status markers follow the active comparison target instead of only `HEAD`.
	- Modified and deleted editor ranges are evaluated against the active comparison state.
	- Gutter interception is still gated by `Include HEAD in scopes` and the gutter settings.
- `C3.8` Unsaved-editor overlay support.
	- Unsaved editor content participates in the active diff, including preserving `NEW` versus `MODIFIED` semantics when overlays merge into comparison data.
	- Unsaved edits can appear before save.
	- Unsaved edits to already-new files must stay `NEW`/`ADDED`, not degrade into ordinary modifications.

### C4. Interaction model and presentation settings

- `C4.1` Configurable click actions.
	- Single, double, middle, and right click interactions can be mapped to source, diff, project-view, or no-op behaviors.
- `C4.2` Right-click mode switch.
	- Right click can either follow the configured action model or open the context menu.
- `C4.3` Double-click delay setting.
	- The delay used to distinguish single from double click is configurable.
- `C4.4` Tool window title visibility.
	- Users can show or hide the tool-window title independently of the rest of the UI.
- `C4.5` Status widget context prefix.
	- The widget can optionally show the `LST-CRC:` context prefix before the active tab label.
- `C4.6` Context-label visibility settings.
	- Context labels are configurable separately for single-repo tabs, multi-repo tabs, and revision/commit tabs.
	- The multi-repo label toggle is distinct from the single-repo label toggle.
- `C4.7` `Include HEAD in scopes` setting.
	- Scopes and other HEAD-backed consumers can be configured to stay empty on the `HEAD` tab unless the user explicitly includes `HEAD`.
	- The setting changes both named-scope and search-scope behavior for `HEAD`.
- `C4.8` Gutter settings.
	- Gutter markers can be enabled or disabled globally, and new-file gutter handling has a separate setting.
	- New-file gutter behavior is a separate decision path from modified/deleted gutter behavior.

### C5. Lifecycle, persistence, and failure handling

- `C5.1` Automatic refresh.
	- Startup hooks plus VFS, changelist, and repository listeners keep the active comparison synchronized with local and repository changes.
	- Refresh covers local edits, saves, branch changes, and repository-level updates.
	- Async diff application rejects stale results whose comparison identity no longer matches the selected tab.
- `C5.2` Persistent project UI state.
	- Open tabs, the selected tab, aliases, and per-repository comparison overrides survive IDE restart.
	- Alias state and per-root overrides survive restart together.
	- Tab add/remove and alias update operations preserve distinct tab identities and avoid unnecessary duplicate state.
- `C5.3` Defensive `HEAD` selection semantics.
	- Persisted-state loading preserves the special meaning of `HEAD` and resets back to it safely when no comparison tab is selected.
- `C5.4` Recovery queueing and notifications.
	- Refresh/recovery flows keep the plugin usable when comparison targets disappear and distinguish between recoverable branch problems and commit misses.
	- Recovery updates can be queued without reopening the full tab-selection flow.
- `C5.5` Linked worktree repository handling.
	- When IntelliJ exposes linked Git worktrees as project roots, the plugin treats them as separate comparison roots.
	- Switching a linked worktree branch refreshes only that root's contribution to the active diff.

## Scope Of The Plugin

- The plugin is Git-focused and depends on the `Git4Idea` integration supplied by the JetBrains IDE.
- The active comparison is tied to the currently selected LST-CRC tab, but some consumers are settings-dependent. In particular, scopes and other `HEAD`-backed consumers stay empty on the `HEAD` tab unless `Include HEAD in scopes` is enabled.
- Deleted-file handling, multi-repo overrides, linked-worktree roots, and unsaved-editor overlays are first-class behaviors, not edge-case add-ons.
- The `Changed` scope intentionally excludes deleted files. Deleted files have their own named scope and dedicated UI handling.
- Find/Search intentionally omits deleted-file scopes even though deleted named scopes exist.
- The plugin does not try to replace the full Git tool window. It adds a comparison-centric workflow optimized around one active diff context that other IDE surfaces can reuse.