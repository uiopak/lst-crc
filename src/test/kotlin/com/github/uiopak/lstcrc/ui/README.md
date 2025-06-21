# UI Testing Guide for lst-crc

This document provides instructions for running UI tests for the lst-crc plugin.

## Overview

The UI tests use IntelliJ's Remote Robot framework to test the plugin in a real IDE environment. The tests verify that:

1. The GitChangesView tool window is available and can be opened
2. The status bar widget is available
3. Custom scopes are registered and available
4. The tool window displays changes and content correctly

## Running UI Tests

There are two ways to run the UI tests:

### Method 1: Using the Run Scripts (Recommended)

The project includes scripts that automate the process of starting the IDE and running the tests:

#### Windows:
```
scripts\run-ui-tests.bat
```

#### Linux/macOS:
```
./scripts/run-ui-tests.sh
```

These scripts will:
1. Start the IDE with Robot Server in a separate process
2. Wait for the IDE to initialize
3. Run the UI tests against the running IDE

### Method 2: Manual Two-Step Process

If you prefer more control over the process, you can run the steps manually:

#### Step 1: Start the IDE with Robot Server

Run the "Start IDE for UI Tests (Separate Process)" run configuration or execute:

```
./gradlew startIdeForUiTests
```

This will start the IDE with Robot Server in a separate process. Wait for the IDE to fully initialize.

#### Step 2: Run the UI Tests

Once the IDE is running, run the "Run UI Tests" run configuration or execute:

```
./gradlew runUiTests
```

This will run the UI tests against the already running IDE.

## Troubleshooting

If you encounter issues with the UI tests:

1. **IDE doesn't start**: Make sure you have sufficient memory allocated to the Gradle JVM.

2. **Tests can't connect to Robot Server**: Verify that the Robot Server is running by checking if port 8082 is open:
   ```
   netstat -an | findstr 8082  # Windows
   netstat -an | grep 8082     # Linux/macOS
   ```

3. **Tests fail with timeout**: Increase the timeout value by setting the system property:
   ```
   ./gradlew runUiTests -Dui.test.timeout=180
   ```

4. **IDE starts but tests don't run**: Make sure you're running the tests after the IDE has fully initialized.

## Adding New UI Tests

When adding new UI tests:

1. Add them to the `com.github.uiopak.lstcrc.ui` package
2. Use the `RemoteRobot` API to interact with the IDE
3. Add appropriate wait conditions to ensure the UI is ready before testing
4. Use descriptive test names and add comments explaining what the test is verifying