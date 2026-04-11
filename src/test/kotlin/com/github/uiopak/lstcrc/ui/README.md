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

Wait for the sandbox IDE to finish loading.

### Step 2: Run the UI suite

Use the shared `Run UI Tests` run configuration or execute:

```powershell
.\gradlew.bat uiTest
```

To run a single UI class or method, keep the IDE running and use:

```powershell
.\gradlew.bat uiTest --tests "com.github.uiopak.lstcrc.plugin.LstCrcInteractionUiTest"
.\gradlew.bat uiTest --tests "com.github.uiopak.lstcrc.plugin.LstCrcInteractionUiTest.testStatusWidgetAndRevisionActions"
```

## Troubleshooting

1. If tests cannot connect to Robot Server, verify port `8082` is open.
2. If UI startup is slow, increase the timeout with `-Dui.test.timeout=180`.
3. If you run a UI test from an IDE JUnit configuration, make sure it sets `-DrunUiTests=true`.
4. Videos are written under `video/` for post-failure inspection.

## Coverage Rules

1. Put new Robot tests in `com.github.uiopak.lstcrc.plugin`.
2. Group tests by feature, not by “basic” or “advanced” buckets.
3. Prefer UI-observable assertions over internal state when practical.
4. Keep the normal `test` task free of Robot execution.