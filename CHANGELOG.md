# LST-CRC changelog

All notable changes to this plugin are documented here in
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

## [Unreleased]

### Added

- Compare the Git working tree with `HEAD`, a branch, or a commit selected in Git Log. Keep multiple comparison tabs with aliases and restore them after an IDE restart.
- Choose separate branch or revision targets for each repository in a project, including linked Git worktrees recognized by the IDE.
- Browse created, modified, moved and deleted files in the comparison tree. Open source files, diffs and deleted-file revisions, or reveal files in the Project view, with configurable click actions.
- Use Created, Modified, Moved, Deleted and Changed named scopes, plus Created, Modified, Moved and Changed scopes in Find/Search. Changed excludes deleted files.
- Show editor gutter markers and scope-based editor tab colors for the active comparison, and switch comparisons from the status bar.
- Include unsaved editor changes and optionally show untracked files as new changes before they are added to Git.
- Optionally show added and removed line counts for files, folders and groups, and choose whether new changes expand collapsed folders.

### Changed

- Keep comparisons responsive during typing by reusing unchanged Git results and updating the edited files. Switching targets with identical file content preserves gutter markers instead of flashing them.
- Load the initial comparison as soon as Git initialization finishes, without waiting for indexing.
- Apply settings immediately across open projects and retain settings saved by earlier plugin versions.

### Fixed

- Prevent older comparison loads, failures and missing-branch recovery from replacing a newer tab selection or repository target. Cancelled loads preserve the current comparison.
- Refresh comparisons after edits, saves, branch changes and repository updates without losing pending changes or accepting files from another project. Restore the last result when Git temporarily fails, and offer repository-level repair for missing branches.
- Keep unsaved text current in the tree and diffs, including added and renamed files in nested repositories. Restoring a file to its target text removes content-only changes while preserving mode, line-ending and byte-order-mark differences.
- Reopen diffs with current content after unsaved text changes or a branch target moves. Preserve native gutter behavior when comparing with `HEAD`, and compare renamed files against their original target path.
- Keep line counts consistent with the visible diff, including renamed files and CRLF/LF normalization. Binary files and unreadable content no longer cause comparison failures.
- Preserve tree selection, scroll position and collapsed folders during refresh. Respect modifier-key multi-selection, reuse existing diff tabs and discard queued actions after their comparison tab closes.
- Preserve tab order and aliases across restarts, select the preceding tab when closing the active one, and keep the current comparison while the branch picker is open. Fix filtered branch selection, names containing underscores and truncated status-bar labels containing emoji.
- Prevent delayed gutter updates and revision-file opens from applying after their editor or comparison tab closes, and keep comparison startup compatible with IDE 2025.1 and 2025.2.

[Unreleased]: https://github.com/uiopak/lst-crc/commits/main
