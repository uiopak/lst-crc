# UI Testing Guide for lst-crc

This document describes the maintained Remote Robot UI suite for the LST-CRC plugin.

## Suite Layout

The active UI tests live in `com.github.uiopak.lstcrc.plugin` and are split by feature:

1. `LstCrcBranchComparisonUiTest`
2. `LstCrcFileScopeUiTest`
3. `LstCrcInteractionUiTest`
4. `LstCrcVisualUiTest`

All Remote Robot classes are tagged as `ui` and only run when `runUiTests=true` is set. That keeps normal `test` runs and generic IDE `Run Tests` actions from accidentally launching Robot tests.

## Running UI Tests

### Step 1: Start the IDE with Robot Server

Use the shared `Start IDE with Robot Server` run configuration or execute:

```powershell
.\gradlew.bat runIdeForUiTests
```

This task is long-running by design. Gradle will look "stuck" near the end of the progress bar because the IDE process stays alive until you stop it.

To get an explicit readiness signal instead of guessing with a timer, run in another terminal:

```powershell
.\gradlew.bat uiTestReady
```

That task succeeds only when the Remote Robot server is reachable.

### Step 2: Run the UI suite

Use the shared `Run UI Tests` run configuration or execute:

```powershell
.\gradlew.bat uiTest
```

`uiTest` now depends on `uiTestReady`, so if the IDE was not started first it fails fast with a clear message instead of waiting for the full suite timeout.

### Cleanup

When you are done with Remote Robot runs, stop the long-lived IDE process explicitly:

```powershell
.\gradlew.bat stopUiTestProcesses
```

If you also want to release stale Gradle daemons after a batch of UI-test runs, follow with:

```powershell
.\gradlew.bat --stop
```

To run a single UI class or method, keep the IDE running and use:

```powershell
.\gradlew.bat uiTest --tests "com.github.uiopak.lstcrc.plugin.LstCrcInteractionUiTest"
.\gradlew.bat uiTest --tests "com.github.uiopak.lstcrc.plugin.LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions"
```

## Troubleshooting

1. If tests cannot connect to Robot Server, verify port `8082` is open.
2. If IDE startup is slow, increase the readiness wait with `-Dui.test.server.wait.timeout=180`.
3. If Remote Robot connects slowly after the port is open, increase the test-side connection wait with `-Dui.test.connection.timeout=60`.
3. If you run a UI test from an IDE JUnit configuration, make sure it sets `-DrunUiTests=true`.
4. Videos are written under `video/` for post-failure inspection.

## Coverage Rules

1. Put new Robot tests in `com.github.uiopak.lstcrc.plugin`.
2. Group tests by feature, not by “basic” or “advanced” buckets.
3. Prefer UI-observable assertions over internal state when practical.
4. Keep the normal `test` task free of Robot execution.