
# lst-crc: Git Branch Comparison Tool for JetBrains IDEs

## Project Overview

lst-crc is a JetBrains IDE plugin that provides a specialized tool window for comparing changes between Git branches. The plugin allows developers to:

1. View differences between the current HEAD and any selected Git branch
2. Open multiple branch comparisons in separate tabs
3. Visualize file changes with color-coded indicators (green for new files, blue for modified files, etc.)
4. Quickly navigate to changed files and view diffs
5. Create IDE scopes based on file status for improved navigation and search filtering

The plugin enhances Git workflow by making it easier to track and review changes across branches without switching contexts.

## Project Structure

The project follows a standard Kotlin/IntelliJ Platform plugin structure:

```
lst-crc/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/github/uiopak/lstcrc/
│   │   │       ├── listeners/       # Event listeners
│   │   │       ├── messaging/       # Internal messaging system
│   │   │       ├── scopes/          # IDE scope definitions
│   │   │       ├── services/        # Core services
│   │   │       ├── state/           # State management
│   │   │       └── toolWindow/      # UI components
│   │   └── resources/
│   │       ├── messages/            # Localization files
│   │       └── META-INF/
│   │           └── plugin.xml       # Plugin configuration
│   └── test/
│       ├── kotlin/                  # Test classes
│       └── testData/                # Test resources
├── build.gradle.kts                 # Build configuration
└── gradle.properties                # Plugin metadata
```

## Key Components

### Services

1. **GitService** (`services/GitService.kt`):
   - Core service for Git operations
   - Retrieves local and remote branches
   - Fetches changes between branches
   - Categorizes changes by type (created, modified, moved)

2. **ToolWindowStateService** (`services/ToolWindowStateService.kt`):
   - Manages persistent state of the tool window
   - Tracks open tabs and selected tab index

3. **ProjectActiveDiffDataService** (`services/ProjectActiveDiffDataService.kt`):
   - Maintains the current set of changed files (created, modified, moved)
   - Provides data for IDE scopes
   - Manages file status notifications to update IDE colorings
   - Handles editor tab color refresh

### UI Components

1. **MyToolWindowFactory** (`toolWindow/MyToolWindowFactory.kt`):
   - Creates and initializes the tool window
   - Manages tab creation and content

2. **ChangesTreePanel** (`toolWindow/ChangesTreePanel.kt`):
   - Displays a tree view of changed files
   - Color-codes files based on change type
   - Handles file selection and diff viewing
   - Implements auto-refresh on Git changes
   - Uses ChangeListListener for efficient change detection and refresh

3. **BranchSelectionPanel** (`toolWindow/BranchSelectionPanel.kt`):
   - Provides UI for selecting Git branches
   - Includes search functionality for filtering branches
   - Organizes branches into local and remote categories

### Scopes

The `scopes/FileStatusScopes.kt` defines custom IDE scopes based on file status:
- **CreatedFilesScope**: Files newly added in the compared branch
- **ModifiedFilesScope**: Files changed in the compared branch
- **MovedFilesScope**: Files renamed or relocated in the compared branch
- **ChangedFilesScope**: Combination of all changed files

Each scope includes detailed logging to track scope checks and performance.

### State Management

The `state/ToolWindowState.kt` defines the persistent state model:
- Tracks open tabs (branch comparisons)
- Maintains selected tab index
- Persists state between IDE restarts

## Technical Implementation

### Git Integration
The plugin leverages the Git4Idea API (JetBrains' Git integration) to:
- Access Git repositories
- Retrieve branch information
- Calculate diffs between branches
- Monitor file status changes

### UI Architecture
- Uses IntelliJ's component system for UI elements
- Implements custom tree renderers for visual indicators
- Employs background tasks for non-blocking Git operations
- Utilizes the IntelliJ messaging system for component communication

### State Persistence
- Stores tool window state using IntelliJ's persistence framework
- Maintains tab configuration across IDE restarts
- Preserves user preferences and branch selections

## Development Workflow

### Building and Running
- Uses Gradle for build automation
- JDK 21+ required for development
- Supports running in development mode with `./gradlew runIde`
- Includes UI testing capabilities with `./gradlew runIdeForUiTests`

### Testing
- Tests are currently only for reference and not fully implemented,
- When making changes, only test is checked for compilation.
- Tests will be performed manually by checking the functionality in the IDE.

### Debugging
- Utilizes IntelliJ's logging system via `thisLogger()`
- Supports debug mode with `./gradlew runIde --debug-jvm`
- Logs detailed information about Git operations and UI events

## Current Development Focus

The current development efforts are focused on:
1. Enhancing changes detection and updating diffs and scopes in real-time
2. Improving performance for large repositories with many branches (tracking only the selected branch and getting changes for another branch when its tab is selected)
3. Enabling custom plugin scopes in all places where IDE supports scopes (currently implemented only for file coloring)
4. Making the IDE display line gutters according to selected branch changes

This plugin provides developers with a powerful tool for tracking and reviewing changes across Git branches without the need to switch contexts, improving productivity and code review workflows.