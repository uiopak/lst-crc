# lst-crc: Git Branch Comparison & Change Tracking Tool for JetBrains IDEs

## Project Overview

**lst-crc** (likely "Local Status To Commit/Ref Comparison") is a JetBrains IDE plugin designed to enhance developer workflows by providing a dedicated tool window for visualizing and tracking file changes. It allows users to:

1.  **Compare Current Work**: View differences between the current **working tree** (including uncommitted local modifications) and any selected Git branch, commit, or tag.
2.  **Tabbed Comparisons**: Open multiple comparisons in separate, persistent tabs within the tool window.
3.  **Visualize Changes**: See a hierarchical tree view of changed files within the tool window, color-coded by their status (new, modified, moved, deleted).
4.  **Navigate Quickly**: Easily open diffs or source files from the change tree, with configurable single and double-click actions.
5.  **Dynamic IDE Scopes**: Utilize automatically updated custom scopes (e.g., "LSTCRC: Created Files," "LSTCRC: Modified Files") based on the active comparison. These scopes can be used for filtering in the Project View, search, and other IDE features.
6.  **Editor Tab Coloring**: Benefit from automatic color highlighting of editor tabs for files that are part of the active comparison (created, modified, moved).
7.  **Real-time Updates**: The active comparison tab automatically refreshes in response to file system changes (like saving a file) and VCS operations (like switching branches).

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
    *   Compare the current working tree (including uncommitted changes) against any local or remote branch.
    *   View only local uncommitted changes by comparing against "HEAD" or the current branch.
*   **Tabbed Interface**: Manage multiple branch comparisons in individual, persistent tabs. Includes a non-closeable "HEAD" tab.
*   **Hierarchical Change Tree**:
    *   Displays created, modified, moved, and deleted files in a structured tree.
    *   Files in the tree are color-coded according to their change type.
*   **Configurable Click Actions**: Set preferences for single-click and double-click actions on files in the tree (e.g., "Show Diff," "Show Source File," "None") and adjust double-click speed.
*   **Dynamic IDE Scopes**:
    *   Provides scopes: "LSTCRC: Created Files," "LSTCRC: Modified Files," "LSTCRC: Moved Files," and "LSTCRC: Changed Files."
    *   These scopes are dynamically updated based on the files in the currently active tool window tab's comparison.
    *   Usable in Project View filtering, "Find Usages," "Scope"-based searches, etc.
*   **Editor Tab Highlighting**: Editor tabs for files identified as created, modified, or moved in the active comparison are automatically highlighted with colors.
*   **Automatic Refresh**: The content of the active comparison tab updates automatically using a multi-layered approach that responds to direct file saves (`VfsChangeListener`), standard VCS updates (`ChangeListListener`), and Git repository state changes (`GitRepositoryChangeListener`).
*   **Persistent State**: Remembers open tabs and user-defined click action preferences across IDE restarts.
*   **Branch Selection UI**: An "Add Tab" action opens a panel with a searchable list of local and remote branches to start new comparisons.
*   **Robust Initial Loading**: On project open, intelligently delays loading initial diff data for the selected tab to ensure Git and the project system are fully ready.

## Key Components

### Services

1.  **`GitService`** (`services/GitService.kt`):
    *   Handles all Git interactions, such as fetching branch lists and repository information.
    *   Calculates differences using `GitChangeUtils.getDiffWithWorkingTree` (for comparing the working tree against a specified ref) or `ChangeListManager` (for viewing local uncommitted changes).
    *   Categorizes changes (created, modified, moved, deleted) and returns them asynchronously via `CompletableFuture`.

2.  **`ToolWindowStateService`** (`services/ToolWindowStateService.kt`):
    *   Manages the persistent state of the tool window (list of open tabs, index of the selected tab) using `PersistentStateComponent`.
    *   Orchestrates data loading for the currently selected tab: when a tab is selected, it triggers `GitService` to fetch changes and then updates `ProjectActiveDiffDataService`.
    *   Coordinates refreshes for the active tab when its corresponding branch data changes.

3.  **`ProjectActiveDiffDataService`** (`services/ProjectActiveDiffDataService.kt`):
    *   A central service that holds the change data (lists of created, modified, and moved `VirtualFile`s) for the *currently active* comparison tab.
    *   This data is the source of truth for the custom scopes and editor tab coloring.
    *   When updated, it notifies IntelliJ's `FileStatusManager`. This is crucial for triggering re-evaluation of custom scopes across the IDE (e.g., in the Project View).
    *   It also triggers editor tab color refreshes by calling `FileEditorManager.updateFilePresentation()`.

4.  **`VfsListenerService`** (`services/VfsListenerService.kt`):
    *   A project-level service that is initialized by `MyToolWindowFactory` when the tool window is created.
    *   Its primary role is to register the `VfsChangeListener`.

### Listeners

1.  **`ProjectOpenCloseListener`** (`listeners/ProjectOpenCloseListener.kt`):
    *   Manages plugin initialization logic when a project is opened.
    *   Schedules a delayed task to robustly load the initial diff data for the selected tab, ensuring the project and Git repository are fully initialized.
    *   Triggers an initial refresh of editor tab colors.

2.  **`VfsChangeListener`** (`services/VfsChangeListener.kt`):
    *   A `BulkFileListener` that listens for low-level file system events (create, delete, content change).
    *   Upon detecting a relevant change, it calls `VcsDirtyScopeManager.markEverythingDirty()`, which efficiently signals to IntelliJ's VCS system that it should re-scan for changes. This is the first step in the automatic refresh process on file saves.

3.  **`ChangesTreePanel`** (as a listener, in `toolWindow/ChangesTreePanel.kt`):
    *   Implements `ChangeListListener` and `git4idea.repo.GitRepositoryChangeListener`. This allows it to react to higher-level VCS events that are fired after the VFS change has been processed by the IDE, or when repository state changes (e.g., branch checkout). This is the final step that triggers a data refresh for the UI.

### UI Components (in `toolWindow/` package)

1.  **`MyToolWindowFactory`**:
    *   Responsible for creating and setting up the main tool window ("GitChangesView").
    *   Initializes the `VfsListenerService`, activating the file-based change detection.
    *   Restores previously opened tabs from `ToolWindowStateService`.
    *   Manages the lifecycle of tabs (content) within the tool window and syncs changes (add, remove, selection) with `ToolWindowStateService`.

2.  **`ChangesTreePanel`**:
    *   The core UI component displaying the hierarchical tree of changed files for a specific branch comparison.
    *   Handles user interactions like single/double clicks to open diffs or source files.
    *   As a listener, it automatically triggers data refreshes for its associated branch comparison.

3.  **`OpenBranchSelectionTabAction`**:
    *   The "Add Tab" action in the tool window's toolbar. Opens a temporary tab containing the `BranchSelectionPanel` to start a new comparison.

### Scopes (in `scopes/` package)

1.  **`LstCrcScopeProvider` & `FileStatusScopes.kt`**:
    *   Define and provide custom IDE scopes that dynamically query `ProjectActiveDiffDataService`. Because this service notifies `FileStatusManager` on data changes, the scopes update in real-time for use in the Project View, "Find in Files", and other IDE features.

## Technical Implementation Details

*   **Git Integration**: Leverages the `Git4Idea` plugin API. Uses `GitChangeUtils.getDiffWithWorkingTree` for comparing the working directory against a reference and `ChangeListManager` for local uncommitted changes.
*   **Change Detection**: A multi-layered system ensures robust and timely updates:
    1.  **File System Events (`VfsChangeListener`)**: A `BulkFileListener` detects file modifications on save. It notifies the core VCS system via `VcsDirtyScopeManager` to check for changes.
    2.  **VCS Subsystem (`ChangeListListener`)**: The active `ChangesTreePanel` listens for finalized change events from the `ChangeListManager`. This triggers a debounced data refresh, preventing excessive reloads.
    3.  **Git Repository State (`GitRepositoryChangeListener`)**: The panel also listens for events like branch checkouts, ensuring the view refreshes when HEAD moves or the repository state changes fundamentally.
*   **Asynchronous Operations**: Git operations are performed on background threads using `Task.Backgroundable` to keep the UI responsive. Results are handled via `CompletableFuture`.
*   **UI**: Built using Swing and IntelliJ's UI components (`JBPanel`, `Tree`, `ColoredTreeCellRenderer`, etc.).
*   **State Persistence**: Tool window tab configuration is persisted via `ToolWindowStateService` (implementing `PersistentStateComponent`). UI behavior preferences (click actions) are persisted using `PropertiesComponent`.
*   **Disposables**: Proper use of `Disposable` (passed from the `ToolWindow` down to components like `ChangesTreePanel`) ensures listeners and resources are cleaned up correctly when the tool window or project closes.

## Development Workflow

*   **Build System**: Gradle with the `org.jetbrains.intellij` plugin.
*   **JDK Requirement**: As per project configuration (e.g., JDK 21).
*   **Running/Debugging**: Standard IntelliJ plugin development workflow (`./gradlew runIde`).