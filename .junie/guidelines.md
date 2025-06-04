# lst-crc: Git Branch Comparison & Change Tracking Tool for JetBrains IDEs

## Project Overview

**lst-crc** (likely "Local Status To Commit/Ref Comparison") is a JetBrains IDE plugin designed to enhance developer workflows by providing a dedicated tool window for visualizing and tracking file changes. It allows users to:

1.  **Compare Current Work**: View differences between the current **working tree** (including uncommitted modifications) and any selected Git branch/commit, or inspect current uncommitted local changes (when comparing against HEAD).
2.  **Tabbed Comparisons**: Open multiple comparisons in separate, persistent tabs within the tool window.
3.  **Visualize Changes**: See a hierarchical tree view of changed files within the tool window, color-coded by their status (new, modified, moved, deleted).
4.  **Navigate Quickly**: Easily open diffs or source files from the change tree, with configurable single and double-click actions.
5.  **Dynamic IDE Scopes**: Utilize automatically updated custom scopes (e.g., "LSTCRC: Created Files," "LSTCRC: Modified Files") based on the active comparison. These scopes can be used for filtering in the Project View, search, and other IDE features.
6.  **Editor Tab Coloring**: Benefit from automatic color highlighting of editor tabs for files that are part of the active comparison (created, modified, moved).
7.  **Real-time Updates**: The active comparison tab automatically refreshes based on VCS changes detected by the IDE.

The plugin aims to streamline the process of reviewing changes, understanding branch differences, and keeping track of modifications without constantly switching Git branches or running command-line operations.

## Project Structure

The project follows a standard Kotlin/IntelliJ Platform plugin structure:

```
lst-crc/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/github/uiopak/lstcrc/
│   │   │       ├── listeners/       # Project lifecycle and other event listeners
│   │   │       ├── messaging/       # Definitions for internal message bus topics
│   │   │       ├── scopes/          # Custom IDE scope definitions and provider
│   │   │       ├── services/        # Core logic, Git interaction, state management
│   │   │       ├── state/           # Data classes for persistent state
│   │   │       └── toolWindow/      # UI components for the tool window
│   │   └── resources/
│   │       └── META-INF/
│   │           └── plugin.xml       # Plugin configuration and extension point registration
│   └── test/
│       ├── kotlin/                  # Test classes
│       └── testData/                # Test resources
├── build.gradle.kts                 # Build configuration
└── gradle.properties                # Plugin metadata
```

## Core Features

*   **Dedicated Tool Window**: A custom tool window ("GitChangesView") to display comparisons.
*   **Flexible Comparisons**:
   *   Compare current working tree (including uncommitted changes) against any local or remote branch.
   *   View local uncommitted changes when comparing against "HEAD" or the current branch.
*   **Tabbed Interface**: Manage multiple branch comparisons in individual, persistent tabs. Includes a non-closeable "HEAD" tab.
*   **Hierarchical Change Tree**:
   *   Displays created, modified, moved, and deleted files in a structured tree.
   *   Files in the tree are color-coded according to their change type.
*   **Configurable Click Actions**: Set preferences for single-click and double-click actions on files in the tree (e.g., "Show Diff," "Open Source File," "None") and adjust double-click speed.
*   **Dynamic IDE Scopes**:
   *   Provides scopes: "LSTCRC: Created Files," "LSTCRC: Modified Files," "LSTCRC: Moved Files," and "LSTCRC: Changed Files."
   *   These scopes are dynamically updated based on the files in the currently active tool window tab's comparison.
   *   Usable in Project View filtering, "Find Usages," "Scope"-based searches, etc.
*   **Editor Tab Highlighting**: Editor tabs for files identified as created, modified, or moved in the active comparison are automatically highlighted.
*   **Automatic Refresh**: The content of the active comparison tab updates automatically in response to VCS changes (e.g., file saves, Git operations) detected by IntelliJ's `ChangeListListener` and `GitRepositoryChangeListener`.
*   **Persistent State**: Remembers open tabs and user-defined click action preferences across IDE restarts.
*   **Branch Selection UI**: An "Add Tab" action opens a panel with a searchable list of local and remote branches to start new comparisons.
*   **Initial Data Loading**: On project open, intelligently delays loading initial diff data for the selected tab to ensure Git and project system readiness.

## Key Components

### Services

1.  **`GitService`** (`services/GitService.kt`):
   *   Handles all Git interactions, such as fetching branch lists and repository information.
   *   Calculates differences using `GitChangeUtils.getDiffWithWorkingTree` (working tree vs. specified ref) or `ChangeListManager` (for local uncommitted changes).
   *   Categorizes changes (created, modified, moved, deleted) and returns them asynchronously via `CompletableFuture`.

2.  **`ToolWindowStateService`** (`services/ToolWindowStateService.kt`):
   *   Manages the persistent state of the tool window (list of open tabs, index of the selected tab) using `PersistentStateComponent`.
   *   Orchestrates data loading for the currently selected tab: when a tab is selected, it triggers `GitService` to fetch changes and then updates `ProjectActiveDiffDataService`.
   *   Refreshes data for the active tab if its corresponding branch changes.

3.  **`ProjectActiveDiffDataService`** (`services/ProjectActiveDiffDataService.kt`):
   *   Holds the change data (list of all changes, and categorized lists of created, modified, moved `VirtualFile`s) for the *currently active* comparison tab.
   *   This data is consumed by the custom scopes.
   *   When updated, it notifies IntelliJ's `FileStatusManager`. This is crucial for triggering re-evaluation of custom scopes across the IDE (e.g., in Project View) and for other UI elements that depend on file status.
   *   Explicitly triggers editor tab color refreshes by calling `FileEditorManager.updateFilePresentation()`.

### Listeners

1.  **`ProjectOpenCloseListener`** (`listeners/ProjectOpenCloseListener.kt`):
   *   Manages plugin initialization logic when a project is opened.
   *   Schedules a delayed task (after a configurable `INITIAL_DIFF_LOAD_DELAY_MS`) to robustly load initial diff data for the tool window's selected tab, ensuring the project and Git repository are fully initialized.
   *   Triggers an initial refresh of editor tab colors.

### UI Components (in `toolWindow/` package)

1.  **`MyToolWindowFactory`**:
   *   Responsible for creating and setting up the main tool window ("GitChangesView").
   *   Restores previously opened tabs from `ToolWindowStateService`.
   *   Manages the lifecycle of tabs (content) within the tool window and syncs changes (add, remove, selection) with `ToolWindowStateService`.
   *   Integrates the "Add Tab" action and settings menu.

2.  **`ChangesTreePanel`**:
   *   The core UI component displaying the hierarchical tree of changed files for a specific branch comparison.
   *   Renders files with color-coding based on their change type (new, modified, etc.).
   *   Handles user interactions like single/double clicks to open diffs or source files, based on user preferences.
   *   Listens to IntelliJ's `ChangeListListener` and `GitRepositoryChangeListener` to automatically trigger data refreshes for its associated branch comparison.
   *   Provides methods like `displayChanges()` to update its view and `requestRefreshData()` to initiate a refresh.

3.  **`BranchSelectionPanel`**:
   *   A UI panel used within a temporary tab for selecting a new branch to compare.
   *   Displays local and remote branches in a searchable tree.
   *   Invokes a callback when a branch is selected, leading to the creation or selection of a comparison tab.

4.  **`OpenBranchSelectionTabAction`**:
   *   The "Add Tab" action in the tool window's toolbar.
   *   Opens a temporary tab containing the `BranchSelectionPanel`.
   *   Upon branch selection, either selects an existing tab for that branch or repurposes the temporary tab (or creates a new one) to show the `ChangesTreePanel` for the selected branch.

5.  **`ToolWindowSettingsProvider`**:
   *   Creates the "gear" icon submenu in the tool window for configuring plugin settings.
   *   Allows users to define actions for single-click and double-click on files in the `ChangesTreePanel` (e.g., open diff, open source, none).
   *   Persists these settings using `PropertiesComponent`.

### Scopes (in `scopes/` package)

1.  **`LstCrcScopeProvider` & `FileStatusScopes.kt`**:
   *   Define and provide custom IDE scopes: `LSTCRC: Created Files`, `LSTCRC: Modified Files`, `LSTCRC: Moved Files`, and `LSTCRC: Changed Files`.
   *   These scopes dynamically query `ProjectActiveDiffDataService` to determine file membership based on the currently active branch comparison in the tool window.
   *   Because `ProjectActiveDiffDataService` notifies `FileStatusManager` on data changes, these scopes update their contents in real-time and can be used for filtering in the Project View, "Find in Files" (with scope restriction), and other IDE features supporting scopes.

### State Management (in `state/` package)

1.  **`ToolWindowState` & `TabInfo`**:
   *   Data classes defining the structure of the plugin's persistent state (list of open tabs, selected tab index).
   *   Managed by `ToolWindowStateService` and persisted by IntelliJ's framework.

## Technical Implementation Details

*   **Git Integration**: Leverages the `Git4Idea` plugin API for all Git operations. Uses `GitChangeUtils.getDiffWithWorkingTree` for comparing the working directory against a reference and `ChangeListManager` for local uncommitted changes.
*   **Change Detection**: Primarily relies on IntelliJ's `ChangeListListener` and `GitRepositoryChangeListener`. When these listeners report VCS changes, the plugin refreshes the data for the active tool window tab.
   *   *Note*: The codebase includes a `VfsChangeListener` and `VfsListenerService` intended for more direct file system event listening. However, these are **currently not registered/activated** in the `plugin.xml`. Therefore, updates are driven by the IDE's VCS change detection system.
*   **Asynchronous Operations**: Git operations that might take time (like fetching diffs) are performed on background threads using `Task.Backgroundable` to keep the UI responsive.
*   **UI**: Built using Swing and IntelliJ's UI components (`JBPanel`, `Tree`, `ColoredTreeCellRenderer`, etc.).
*   **State Persistence**: Tool window tab configuration is persisted via `ToolWindowStateService` (implementing `PersistentStateComponent`). UI behavior preferences (click actions) are persisted using `PropertiesComponent`.
*   **Disposables**: Proper use of `Disposable` ensures resources and listeners are cleaned up when the tool window or project closes.

## Development Workflow

*   **Build System**: Gradle with the `org.jetbrains.intellij` plugin.
*   **JDK Requirement**: As per project configuration (e.g., JDK 17 or 21).
*   **Running/Debugging**: Standard IntelliJ plugin development workflow (`./gradlew runIde`).

## Future Considerations / Current Development Focus

*   **Line-level Gutter Indicators**: A potential future enhancement could be to display change indicators (e.g., colored markers in the editor gutter) based on the selected branch comparison, similar to native VCS integration.
*   **Performance**: Continuous monitoring and optimization for large repositories or complex diffs. The current design of loading data only for the active tab is a key performance consideration.
*   **VFS Listener Activation**: If more immediate, direct file system change detection (bypassing standard VCS polling/indexing cycles) becomes necessary, the existing `VfsChangeListener` could be activated and refined.

This plugin offers a focused and integrated solution for developers needing to frequently compare their current work against various Git branches or track ongoing changes within the JetBrains IDE environment.