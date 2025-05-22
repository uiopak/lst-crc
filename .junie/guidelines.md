# Development Guidelines for lst-crc

This document provides guidelines and information for developers working on the lst-crc IntelliJ Platform plugin project.

## Build/Configuration Instructions

### Prerequisites
- JDK 21 or later
- Gradle 8.13 or compatible version
- IntelliJ IDEA or Rider for development

### Setting Up the Development Environment
1. Clone the repository:
   ```
   git clone https://github.com/uiopak/lst-crc.git
   ```

2. Import the project into IntelliJ IDEA or Rider as a Gradle project.

3. Sync the Gradle project to download dependencies.

### Building the Plugin
To build the plugin, run:
```
./gradlew build
```

This will compile the code, run tests, and create a plugin distribution in the `build/distributions` directory.

### Running the Plugin
To run the plugin in a development instance of IntelliJ IDEA or Rider:
```
./gradlew runIde
```

For UI testing, use:
```
./gradlew runIdeForUiTests
```

### Plugin Configuration
The plugin is configured in the following files:
- `gradle.properties`: Contains plugin metadata, version, and platform compatibility information
- `build.gradle.kts`: Contains build configuration, dependencies, and plugin tasks

## Testing Information

### Running Tests
To run all tests:
```
./gradlew test
```

To run a specific test class:
```
./gradlew test --tests "com.github.uiopak.lstcrc.services.GitServiceTest"
```

### Test Structure
Tests are located in the `src/test/kotlin` directory and follow the same package structure as the main code.

The project uses the IntelliJ Platform Test Framework, which provides utilities for testing plugins. Tests extend `BasePlatformTestCase` to access these utilities.

### Adding New Tests
1. Create a new test class in the appropriate package in `src/test/kotlin`.
2. Extend `BasePlatformTestCase` to access IntelliJ Platform test utilities.
3. Use the `@Test` annotation for test methods.
4. Use the `myFixture` object provided by `BasePlatformTestCase` to set up test environments.

### Example Test
Here's an example of a simple test for the GitService class:

```kotlin
// Example test for GitService
class GitServiceTest : BasePlatformTestCase() {

    @Test
    fun testGetLocalBranches() {
        val gitService = GitService(project)
        val branches = gitService.getLocalBranches()

        // Verify that the local branches list is not empty
        assertFalse("Local branches list should not be empty", branches.isEmpty())

        // Verify that the local branches list contains "main"
        assertTrue("Local branches list should contain 'main'", branches.contains("main"))
    }
}
```

### Known Testing Issues
- When running tests for Rider plugins, you may encounter the error "solution can't be null [Plugin: com.intellij]". This is because Rider tests require a solution (project) to be properly set up. Make sure your test environment is correctly configured for Rider.

## Additional Development Information

### Project Structure
- `src/main/kotlin`: Contains the main Kotlin source code
- `src/main/resources`: Contains resources like icons, messages, and plugin.xml
- `src/test/kotlin`: Contains test code
- `src/test/testData`: Contains test data files

### Key Components
- `GitService`: Provides Git-related functionality like retrieving branches and changes
- `GitChangesToolWindow`: Implements the tool window UI for displaying Git changes
- `MyToolWindowFactory`: Factory class for creating the tool window

### Current Task
The current task is to modify the plugin tool window to:
1. Display a tree with changes between HEAD and selected reference
2. Add tabs and a plus button at the top
3. Allow the plus button to display a modal/select with search to select Git branches
4. Enable opening multiple tabs and closing them
5. Color files/folders in the tree based on their status (green for new, blue for modified, etc.)
6. Open a diff view when a file is clicked

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and method names
- Add comments for complex logic
- Write unit tests for new functionality

### Debugging
- Use `thisLogger().info()` or `thisLogger().debug()` for logging
- Run the plugin in debug mode using `./gradlew runIde --debug-jvm`
- Check the IDE log files for error messages

### Git Integration
The plugin uses the Git4Idea plugin for Git integration. Make sure this plugin is included in your development environment.
