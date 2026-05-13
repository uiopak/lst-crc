package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.IdeaFrame
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.branchSelection
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.time.Duration

@LstCrcUiTest
class LstCrcBranchComparisonUiTest : LstCrcUiTestSupport() {

    @Test
    @Video
    fun testBranchComparisonUpdatesModifiedScope(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Modified.txt", "base line\n")
            uiSteps.createNewFile("RenameMe.txt", "rename source\n")
            uiSteps.createNewFile("DeleteMe.txt", "delete source\n")
            uiSteps.commitChanges("Initial comparison fixture")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-all-statuses")
            uiSteps.modifyFile("Modified.txt", "feature line\n")
            uiSteps.renameFile("RenameMe.txt", "Renamed.txt")
            uiSteps.deleteFile("DeleteMe.txt")
            uiSteps.createNewFile("Created.txt", "created on branch\n")
            uiSteps.commitChanges("Branch status changes")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-all-statuses")
            }

            gitChangesView {
                selectTab("feature-all-statuses")
            }

            var scopeDebug = ""
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(500)) {
                scopeDebug = callJs(
                    """
                    (function() {
                        const result = new java.util.concurrent.atomic.AtomicReference("projectMissing=true");
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                            run: function() {
                                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                                if (!project) {
                                    result.set("projectMissing=true");
                                    return;
                                }

                                const getPluginClass = (className) => {
                                    const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                                    const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                                    if (plugin && plugin.getPluginClassLoader()) {
                                        return java.lang.Class.forName(className, true, plugin.getPluginClassLoader());
                                    }
                                    return java.lang.Class.forName(className);
                                };
                                const namedScopeManager = com.intellij.psi.search.scope.packageSet.NamedScopeManager.getInstance(project);
                                const createdScope = getPluginClass("com.github.uiopak.lstcrc.scopes.CreatedFilesScope").getDeclaredConstructor().newInstance();
                                const modifiedScope = getPluginClass("com.github.uiopak.lstcrc.scopes.ModifiedFilesScope").getDeclaredConstructor().newInstance();
                                const movedScope = getPluginClass("com.github.uiopak.lstcrc.scopes.MovedFilesScope").getDeclaredConstructor().newInstance();
                                const deletedScope = getPluginClass("com.github.uiopak.lstcrc.scopes.DeletedFilesScope").getDeclaredConstructor().newInstance();
                                const diffDataServiceClass = getPluginClass("com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService");
                                const diffDataService = project.getService(diffDataServiceClass);

                                function findBySuffix(files, suffix) {
                                    if (!files) return null;
                                    for (let i = 0; i < files.size(); i++) {
                                        const candidate = files.get(i);
                                        if (String(candidate.getPath()).endsWith("/" + suffix) || String(candidate.getPath()).endsWith("\\" + suffix)) {
                                            return candidate;
                                        }
                                    }
                                    return null;
                                }

                                const modifiedFile = diffDataService ? findBySuffix(diffDataService.getModifiedFiles(), "Modified.txt") : null;
                                const createdFile = diffDataService ? findBySuffix(diffDataService.getCreatedFiles(), "Created.txt") : null;
                                const movedFile = diffDataService ? findBySuffix(diffDataService.getMovedFiles(), "Renamed.txt") : null;
                                const deletedFile = diffDataService ? findBySuffix(diffDataService.getDeletedFiles(), "DeleteMe.txt") : null;

                                function contains(scope, file) {
                                    if (!(scope && file && scope.getValue())) {
                                        return false;
                                    }
                                    return scope.getValue().contains(file, project, namedScopeManager) === true;
                                }

                                result.set([
                                    "activeBranch=" + (diffDataService ? diffDataService.getActiveBranchName() : "null"),
                                    "createdFilePresent=" + (createdFile != null),
                                    "modifiedFilePresent=" + (modifiedFile != null),
                                    "movedFilePresent=" + (movedFile != null),
                                    "createdPathCached=" + (diffDataService && createdFile ? diffDataService.getCreatedFilePaths().contains(String(createdFile.getPath())) : false),
                                    "modifiedPathCached=" + (diffDataService && modifiedFile ? diffDataService.getModifiedFilePaths().contains(String(modifiedFile.getPath())) : false),
                                    "movedPathCached=" + (diffDataService && movedFile ? diffDataService.getMovedFilePaths().contains(String(movedFile.getPath())) : false),
                                    "deletedPathCached=" + (diffDataService && deletedFile ? diffDataService.getDeletedFilePaths().contains(String(deletedFile.getPath())) : false),
                                    "createdScopeClass=" + (createdScope.getValue() ? createdScope.getValue().getClass().getName() : "null"),
                                    "modifiedScopeClass=" + (modifiedScope.getValue() ? modifiedScope.getValue().getClass().getName() : "null"),
                                    "created=" + contains(createdScope, createdFile),
                                    "modified=" + contains(modifiedScope, modifiedFile),
                                    "moved=" + contains(movedScope, movedFile),
                                    "deleted=" + contains(deletedScope, deletedFile),
                                    "deletedFilePresent=" + (deletedFile != null)
                                ].join(";"));
                            }
                        }));
                        return String(result.get());
                    })();
                    """.trimIndent(),
                    true
                )
                scopeDebug.contains("activeBranch=feature-all-statuses") && scopeDebug.contains("modified=true")
            }

            assertTrue(
                scopeDebug.contains("activeBranch=feature-all-statuses") && scopeDebug.contains("modified=true"),
                "Branch comparison should update the active branch and modified scope correctly: $scopeDebug"
            )
        }
    }

    @Test
    @Video
    fun testGitBranchComparison(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Initial content\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-branch")
            uiSteps.createNewFile("Feature.txt", "Feature content\n")
            uiSteps.modifyFile("Main.txt", "Main modified in feature\n")
            uiSteps.commitChanges("Feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()

            gitChangesView {
                step("Check HEAD tab") {
                    selectTab("HEAD")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.isShowing
                    }
                }
                addTab()
            }

            branchSelection {
                searchAndSelect("feature-branch")
            }

            gitChangesView {
                step("Verify feature-branch comparison tab") {
                    selectTab("feature-branch")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature.txt").isNotEmpty() &&
                            changesTree.findAllText("Main.txt").isNotEmpty()
                    }
                }
            }

            uiSteps.switchToProjectView()
            uiSteps.modifyFile("Main.txt", "Modified on master\n")

            openGitChangesView()
            gitChangesView {
                selectTab("HEAD")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Main.txt").isNotEmpty()
                }

                selectTab("feature-branch")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Main.txt").isNotEmpty()
                }
            }

            var scopesDebug = ""
            waitFor(Duration.ofSeconds(10), interval = Duration.ofSeconds(1)) {
                scopesDebug = callJs(
                    """
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    const namedScopeManager = com.intellij.psi.search.scope.packageSet.NamedScopeManager.getInstance(project);
                    const modifiedScopeClass = com.intellij.ide.plugins.PluginManagerCore
                        .getPlugin(com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc"))
                        .getPluginClassLoader()
                        .loadClass("com.github.uiopak.lstcrc.scopes.ModifiedFilesScope");
                    const modifiedScope = modifiedScopeClass.getDeclaredConstructor().newInstance();
                    const modifiedHolder = namedScopeManager;

                    const mainFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath() + "/Main.txt");
                    const scopeFound = modifiedScope != null;
                    const fileFound = mainFile != null;
                    const containsFile = scopeFound && fileFound && modifiedScope.getValue() != null && modifiedScope.getValue().contains(mainFile, project, modifiedHolder);
                    "result=" + containsFile + ";scopeFound=" + scopeFound + ";fileFound=" + fileFound;
                    """.trimIndent(),
                    true
                )
                scopesDebug.contains("result=true")
            }

            if (scopesDebug.contains("scopeFound=true")) {
                assertTrue(scopesDebug.contains("result=true"), "Custom scopes should be updated with modified files: $scopesDebug")
            }
        }
    }

    @Test
    @Video
    fun testUnsavedLocalEditAppearsWithoutSave(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Initial content\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-unsaved")
            uiSteps.createNewFile("Feature.txt", "Feature content\n")
            uiSteps.commitChanges("Feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-unsaved")
            }

            modifyFileWithoutSave("Main.txt", "Unsaved local change\n")

            gitChangesView {
                step("Verify unsaved local edit in current comparison tab") {
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature.txt").isNotEmpty() &&
                            changesTree.findAllText("Main.txt").isNotEmpty()
                    }
                }

                step("Verify unsaved local edit in HEAD") {
                    selectTab("HEAD")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Main.txt").isNotEmpty()
                    }
                }
            }
        }
    }

    @Test
    @Video
    fun testUnsavedSingleCharacterEditsUpdateLineStatsImmediatelyAcrossMultipleLines(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            val initialContent = (1..10).joinToString(separator = "\n", postfix = "\n") { index ->
                "line $index"
            }
            uiSteps.createNewFile("Main.txt", initialContent)
            uiSteps.commitChanges("Initial commit")

            openGitChangesView()
            gitChangesView {
                selectTab("HEAD")
            }
            setTreeContextSettings(showLineStats = true)

            focusEditorFile("Main.txt")

            step("Type once on line 2 and show one changed line") {
                moveCaretToLineEnd(1)
                insertSingleCharacterAtCaretWithoutSave()
                waitForMainFileLineStats(1)
            }

            step("Type once on line 4 and show two changed lines") {
                moveCaretToLineEnd(3)
                insertSingleCharacterAtCaretWithoutSave()
                waitForMainFileLineStats(2)
            }

            step("Type once on line 5 and show three changed lines") {
                moveCaretToLineEnd(4)
                insertSingleCharacterAtCaretWithoutSave()
                waitForMainFileLineStats(3)
            }

            step("Type once on line 6 and show four changed lines") {
                moveCaretToLineEnd(5)
                insertSingleCharacterAtCaretWithoutSave()
                waitForMainFileLineStats(4)
            }

            step("Type once on line 7 and show five changed lines") {
                moveCaretToLineEnd(6)
                insertSingleCharacterAtCaretWithoutSave()
                waitForMainFileLineStats(5)
            }

            step("Type again on line 7 and keep five changed lines") {
                insertSingleCharacterAtCaretWithoutSave()
                waitForMainFileLineStats(5)
            }
        }
    }

    @Test
    @Video
    fun testNewFileStaysCreatedDuringUnsavedEdits(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Initial content\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-new-file-unsaved")
            uiSteps.createNewFile("Feature.txt", "Feature content\n")
            uiSteps.commitChanges("Feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            uiSteps.createNewFile("Local.txt", "Local content\n")

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-new-file-unsaved")
            }

            gitChangesView {
                step("Verify new local file is visible in comparison tab") {
                    selectTab("feature-new-file-unsaved")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature.txt").isNotEmpty() &&
                            changesTree.findAllText("Local.txt").isNotEmpty()
                    }
                }
            }

            step("Verify new local file starts with added file status") {
                var initialStatus = ""
                waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                    initialStatus = fileStatusDebug("Local.txt")
                    initialStatus.contains("status=ADDED")
                }
                assertTrue(initialStatus.contains("status=ADDED"), "Expected ADDED file status for a new file, got: $initialStatus")
            }

            modifyFileWithoutSave("Local.txt", "Unsaved local content\n")

            step("Verify unsaved edit keeps added file status") {
                var statusAfterEdit = ""
                var addedVisible = false
                waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                    statusAfterEdit = fileStatusDebug("Local.txt")
                    addedVisible = statusAfterEdit.contains("status=ADDED")
                    addedVisible
                }
                assertTrue(
                    addedVisible,
                    "Unsaved edit should keep ADDED file status. got=$statusAfterEdit"
                )
            }
        }
    }

    @Test
    @Video
    fun testLocalNewFileAppearsInComparisonTab(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Main.txt", "Initial content\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-local-new-file")
            uiSteps.createNewFile("Feature.txt", "Feature content\n")
            uiSteps.commitChanges("Feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-local-new-file")
            }

            uiSteps.switchToProjectView()
            uiSteps.createNewFile("Local.txt", "Local on master\n")

            gitChangesView {
                step("Verify new local file in HEAD") {
                    selectTab("HEAD")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Local.txt").isNotEmpty()
                    }
                }

                step("Verify new local file in comparison tab") {
                    selectTab("feature-local-new-file")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature.txt").isNotEmpty() &&
                            changesTree.findAllText("Local.txt").isNotEmpty()
                    }
                }
            }
        }
    }

    @Test
    @Video
    fun testMultipleComparisonTabs(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Base.txt", "Base content\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-1")
            uiSteps.createNewFile("Feature1.txt", "Feature 1 content\n")
            uiSteps.modifyFile("Base.txt", "Base modified in feature 1\n")
            uiSteps.commitChanges("Feature 1 commit")

            uiSteps.checkoutBranch(defaultBranch)
            uiSteps.createBranch("feature-2")
            uiSteps.createNewFile("Feature2.txt", "Feature 2 content\n")
            uiSteps.commitChanges("Feature 2 commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-1")
            }
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-2")
            }

            gitChangesView {
                val headTabName = if (hasTab("HEAD")) "HEAD" else defaultBranch

                step("Verify feature-1 tab content") {
                    selectTab("feature-1")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature1.txt").isNotEmpty() &&
                            changesTree.findAllText("Base.txt").isNotEmpty()
                    }
                }

                step("Verify feature-2 tab content") {
                    selectTab("feature-2")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature2.txt").isNotEmpty()
                    }
                }

                step("Verify HEAD tab is empty") {
                    selectTab(headTabName)
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature1.txt").isEmpty() &&
                            changesTree.findAllText("Feature2.txt").isEmpty() &&
                            changesTree.findAllText("Base.txt").isEmpty()
                    }
                }
            }

            uiSteps.switchToProjectView()
            uiSteps.createNewFile("Local.txt", "Local on master\n")

            gitChangesView {
                val headTabName = if (hasTab("HEAD")) "HEAD" else defaultBranch

                step("Verify local change in HEAD") {
                    selectTab(headTabName)
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Local.txt").isNotEmpty()
                    }
                }

                step("Verify local change is visible in comparison tab") {
                    selectTab("feature-1")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature1.txt").isNotEmpty() &&
                            changesTree.findAllText("Local.txt").isNotEmpty()
                    }
                }
            }
        }
    }

    @Test
    @Video
    fun testTreeStatePersistsAcrossTabSwitches(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Base.txt", "base\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-tree-a")
            uiSteps.createNewFile("nested/featureA/OnlyA.txt", "only a\n")
            uiSteps.commitChanges("Branch A nested file")
            uiSteps.checkoutBranch(defaultBranch)

            uiSteps.createBranch("feature-tree-b")
            uiSteps.createNewFile("nested/featureB/OnlyB.txt", "only b\n")
            uiSteps.commitChanges("Branch B nested file")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()

            gitChangesView { addTab() }
            branchSelection { searchAndSelect("feature-tree-a") }
            gitChangesView {
                selectTab("feature-tree-a")
                step("Wait for OnlyA.txt to appear") {
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("OnlyA.txt").isNotEmpty()
                    }
                }
            }

            step("Collapse 'nested' on tab A") {
                setChangesTreeNodeExpanded("nested", false)
            }

            gitChangesView {
                step("Verify OnlyA.txt is hidden after collapse") {
                    waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(200)) {
                        changesTree.findAllText("OnlyA.txt").isEmpty()
                    }
                    assertTrue(
                        changesTree.findAllText("OnlyA.txt").isEmpty(),
                        "OnlyA.txt should be hidden after collapsing 'nested'"
                    )
                }
            }

            gitChangesView { addTab() }
            branchSelection { searchAndSelect("feature-tree-b") }
            gitChangesView {
                selectTab("feature-tree-b")
                step("Wait for OnlyB.txt to appear on tab B") {
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("OnlyB.txt").isNotEmpty()
                    }
                }
            }

            gitChangesView {
                selectTab("feature-tree-a")
                step("Wait for tree to stabilize on tab A after switch") {
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("OnlyB.txt").isEmpty()
                    }
                }
            }

            gitChangesView {
                step("Verify collapse state persisted across tab switch") {
                    val visibleItems = changesTree.findAllText("OnlyA.txt")
                    assertTrue(
                        visibleItems.isEmpty(),
                        "Tree collapse state should be preserved after tab switch: 'nested' should still be collapsed, so OnlyA.txt should not be visible"
                    )
                }
            }

            step("Re-expand 'nested' on tab A") {
                setChangesTreeNodeExpanded("nested", true)
            }

            gitChangesView {
                step("Verify OnlyA.txt is visible after re-expanding") {
                    waitFor(Duration.ofSeconds(5)) {
                        changesTree.findAllText("OnlyA.txt").isNotEmpty()
                    }
                }
            }
        }
    }

    @Test
    @Video
    fun testNewFileInCollapsedDirExpandsDirWhenSettingEnabled(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Base.txt", "base\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-expand-initial")
            uiSteps.createNewFile("nested/featureA/ExistingA.txt", "existing\n")
            uiSteps.commitChanges("Initial nested change")
            uiSteps.checkoutBranch("feature-expand-initial")
            uiSteps.createBranch("feature-expand-updated")
            uiSteps.createNewFile("nested/featureA/NewA.txt", "new\n")
            uiSteps.commitChanges("Updated nested change")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView { addTab() }
            branchSelection { searchAndSelect("feature-expand-initial") }
            gitChangesView {
                selectTab("feature-expand-initial")
            }
            waitForInitialNestedFile("ExistingA.txt")

            step("Collapse 'nested' before introducing a new file") {
                setChangesTreeNodeExpanded("nested", false)
            }

            gitChangesView {
                step("Verify ExistingA.txt is hidden after collapse") {
                    waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(200)) {
                        changesTree.findAllText("ExistingA.txt").isEmpty()
                    }
                }
            }

            setExpandNewFilesInCollapsedDirs(true)
            setBranchAsRepoComparison("feature-expand-updated")

            step("Wait for selected tab comparison map to update") {
                waitFor(Duration.ofSeconds(10)) {
                    selectedTabComparisonMap().contains("feature-expand-updated")
                }
            }

            var lastSnapshotA = ""
            step("Wait for active diff to include the newly added file") {
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (System.nanoTime() < deadline) {
                    lastSnapshotA = activeDiffSnapshot()
                    if (lastSnapshotA.contains("NewA.txt")) break
                    Thread.sleep(1000)
                }
                assertTrue(lastSnapshotA.contains("NewA.txt"),
                    "Active diff never included NewA.txt after 20s. Last snapshot: '$lastSnapshotA'")
            }

            gitChangesView {
                step("Verify collapsed directory expands to reveal NewA.txt") {
                    waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(200)) {
                        changesTree.findAllText("NewA.txt").isNotEmpty()
                    }
                    assertTrue(
                        changesTree.findAllText("NewA.txt").isNotEmpty(),
                        "NewA.txt should be visible when the setting is enabled and a new file appears in a collapsed directory"
                    )
                }
            }
        }
    }

    @Test
    @Video
    fun testNewFileInCollapsedDirStaysCollapsedWhenSettingDisabled(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Base.txt", "base\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-collapse-initial")
            uiSteps.createNewFile("nested/featureB/ExistingB.txt", "existing\n")
            uiSteps.commitChanges("Initial nested change")
            uiSteps.checkoutBranch("feature-collapse-initial")
            uiSteps.createBranch("feature-collapse-updated")
            uiSteps.createNewFile("nested/featureB/NewB.txt", "new\n")
            uiSteps.commitChanges("Updated nested change")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView { addTab() }
            branchSelection { searchAndSelect("feature-collapse-initial") }
            gitChangesView {
                selectTab("feature-collapse-initial")
            }
            waitForInitialNestedFile("ExistingB.txt")

            step("Collapse 'nested' before introducing a new file") {
                setChangesTreeNodeExpanded("nested", false)
            }

            gitChangesView {
                step("Verify ExistingB.txt is hidden after collapse") {
                    waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(200)) {
                        changesTree.findAllText("ExistingB.txt").isEmpty()
                    }
                }
            }

            setExpandNewFilesInCollapsedDirs(false)
            setBranchAsRepoComparison("feature-collapse-updated")

            step("Wait for selected tab comparison map to update") {
                waitFor(Duration.ofSeconds(10)) {
                    selectedTabComparisonMap().contains("feature-collapse-updated")
                }
            }

            val branchLog = uiSteps.runGitCommand("diff", "--name-status", "feature-collapse-updated").replace("\n", "|")
            var lastSnapshotB = ""
            step("Wait for active diff to include the newly added file") {
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (System.nanoTime() < deadline) {
                    lastSnapshotB = activeDiffSnapshot()
                    if (lastSnapshotB.contains("NewB.txt")) break
                    Thread.sleep(1000)
                }
                assertTrue(lastSnapshotB.contains("NewB.txt"),
                    "Active diff never included NewB.txt after 20s. " +
                    "Last snapshot: '$lastSnapshotB'. " +
                    "feature-collapse-updated log: '$branchLog'")
            }

            gitChangesView {
                step("Verify collapsed directory stays collapsed so NewB.txt remains hidden") {
                    waitFor(Duration.ofSeconds(3), interval = Duration.ofMillis(200)) {
                        changesTree.findAllText("NewB.txt").isEmpty()
                    }
                    assertTrue(
                        changesTree.findAllText("NewB.txt").isEmpty(),
                        "NewB.txt should stay hidden when the setting is disabled and a new file appears in a collapsed directory"
                    )
                }
            }
        }
    }

    private fun IdeaFrame.waitForInitialNestedFile(fileName: String) {
        var lastSnapshot = ""

        step("Wait for initial nested file to appear") {
            waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(500)) {
                lastSnapshot = activeDiffSnapshot()
                lastSnapshot.contains(fileName)
            }

            gitChangesView {
                waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(200)) {
                    changesTree.findAllText(fileName).isNotEmpty()
                }
            }

            gitChangesView {
                assertTrue(
                    changesTree.findAllText(fileName).isNotEmpty(),
                    "Expected initial comparison tree to show $fileName. Last diff snapshot: '$lastSnapshot'"
                )
            }
        }
    }

    @Test
    @Video
    fun testUntrackedFileAppearsWhenSettingEnabled(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Base.txt", "base\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-untracked-visible")
            uiSteps.createNewFile("Feature.txt", "feature\n")
            uiSteps.commitChanges("Feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-untracked-visible")
            }
            gitChangesView {
                selectTab("feature-untracked-visible")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Feature.txt").isNotEmpty()
                }
            }

            setShowUntrackedFilesAsNew(true)
            uiSteps.createNewFile("UntrackedEnabled.txt", "enabled\n", stage = false)
            setShowUntrackedFilesAsNew(true)

            gitChangesView {
                step("Verify untracked file appears when setting is enabled") {
                    waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(300)) {
                        changesTree.findAllText("UntrackedEnabled.txt").isNotEmpty()
                    }
                }
            }
        }
    }

    @Test
    @Video
    fun testUntrackedFileStaysHiddenWhenSettingDisabled(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Base.txt", "base\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-untracked-hidden")
            uiSteps.createNewFile("Feature.txt", "feature\n")
            uiSteps.commitChanges("Feature commit")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-untracked-hidden")
            }
            gitChangesView {
                selectTab("feature-untracked-hidden")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Feature.txt").isNotEmpty()
                }
            }

            setShowUntrackedFilesAsNew(false)
            uiSteps.createNewFile("UntrackedDisabled.txt", "disabled\n", stage = false)
            setShowUntrackedFilesAsNew(false)

            gitChangesView {
                step("Verify untracked file stays hidden when setting is disabled") {
                    waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(200)) {
                        changesTree.findAllText("UntrackedDisabled.txt").isEmpty()
                    }
                }
            }

            setShowUntrackedFilesAsNew(true)
            gitChangesView {
                step("Verify same file appears after enabling setting") {
                    waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(300)) {
                        changesTree.findAllText("UntrackedDisabled.txt").isNotEmpty()
                    }
                }
            }
        }
    }

    @Test
    @Video
    fun testUntrackedFileHasUnknownFileStatus(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("Base.txt", "base\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-file-status")
            uiSteps.createNewFile("TrackedAdded.txt", "tracked\n")
            uiSteps.commitChanges("Add tracked file")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-file-status")
            }
            gitChangesView {
                selectTab("feature-file-status")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("TrackedAdded.txt").isNotEmpty()
                }
            }

            setShowUntrackedFilesAsNew(true)
            uiSteps.createNewFile("UntrackedStatus.txt", "untracked\n", stage = false)
            setShowUntrackedFilesAsNew(true)

            gitChangesView {
                step("Wait for untracked file to appear") {
                    waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(300)) {
                        changesTree.findAllText("UntrackedStatus.txt").isNotEmpty()
                    }
                }
            }

            val untrackedStatus = fileStatusForTreeItem("UntrackedStatus.txt")
            val addedStatus = fileStatusForTreeItem("TrackedAdded.txt")

            assertEquals("UNKNOWN", untrackedStatus,
                "Untracked file should have UNKNOWN file status for native text coloring")
            assertNotEquals(untrackedStatus, addedStatus,
                "Untracked and added files should have different file statuses")
        }
    }

    @Test
    @Video
    fun testFileTypeFileStatuses(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("ToModify.txt", "original\n")
            uiSteps.createNewFile("ToDelete.txt", "to be deleted\n")
            uiSteps.createNewFile("ToRename.txt", "to be renamed\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("feature-change-types")
            uiSteps.modifyFile("ToModify.txt", "modified content\n")
            uiSteps.deleteFile("ToDelete.txt")
            uiSteps.renameFile("ToRename.txt", "Renamed.txt")
            uiSteps.createNewFile("NewFile.txt", "brand new\n")
            uiSteps.commitChanges("All change types")
            uiSteps.checkoutBranch(defaultBranch)

            openGitChangesView()
            gitChangesView {
                addTab()
            }
            branchSelection {
                searchAndSelect("feature-change-types")
            }
            gitChangesView {
                selectTab("feature-change-types")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("ToModify.txt").isNotEmpty() &&
                        changesTree.findAllText("NewFile.txt").isNotEmpty() &&
                        changesTree.findAllText("ToDelete.txt").isNotEmpty() &&
                        changesTree.findAllText("ToRename.txt").isNotEmpty()
                }
            }

            assertEquals("DELETED", fileStatusForTreeItem("NewFile.txt"),
                "File only in comparison branch should have DELETED file status (not present in working dir)")
            assertEquals("MODIFIED", fileStatusForTreeItem("ToModify.txt"),
                "Modified file should have MODIFIED file status")
            assertEquals("ADDED", fileStatusForTreeItem("ToDelete.txt"),
                "File only in working dir should have ADDED file status (deleted in comparison branch)")
            assertEquals("MODIFIED", fileStatusForTreeItem("ToRename.txt"),
                "Renamed file should have MODIFIED file status (rename detected)")
        }
    }


    @Suppress("SameParameterValue")
    private fun RemoteRobot.setChangesTreeNodeExpanded(nodeText: String, expanded: Boolean) {
        step("${if (expanded) "Expand" else "Collapse"} tree node '$nodeText'") {
            val success = callJs<Boolean>(
                """
                (function() {
                    var result = new java.util.concurrent.atomic.AtomicBoolean(false);
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                        run: function() {
                            var targetText = ${toJsStringLiteral(nodeText)};
                            var expand = ${if (expanded) "true" else "false"};
                            var tree = null;
                            var windows = java.awt.Window.getWindows();
                            for (var w = 0; w < windows.length && !tree; w++) {
                                var queue = new java.util.LinkedList();
                                queue.add(windows[w]);
                                while (!queue.isEmpty()) {
                                    var c = queue.poll();
                                    if (c != null && c.getClass().getName().endsWith("LstCrcAsyncChangesTree") && c.isShowing()) {
                                        tree = c;
                                        break;
                                    } else if (c != null) {
                                        try {
                                            var children = c.getComponents();
                                            if (children) {
                                                for (var ci = 0; ci < children.length; ci++) {
                                                    queue.add(children[ci]);
                                                }
                                            }
                                        } catch(e2) {}
                                    }
                                }
                            }
                            if (!tree) return;
                            for (var row = 0; row < tree.getRowCount(); row++) {
                                var path = tree.getPathForRow(row);
                                if (!path) continue;
                                var node = path.getLastPathComponent();
                                if (!node) continue;
                                try { if (node.isLeaf()) continue; } catch(e) {}
                                var userObject = null;
                                try { userObject = node.getUserObject(); } catch(e) {}
                                var text = "";
                                if (userObject) {
                                    try { text = String(userObject.getPath()); } catch(e) {}
                                    if (!text) { try { text = String(userObject.getName()); } catch(e) {} }
                                    if (!text) { try { text = String(userObject); } catch(e) {} }
                                }
                                if (!text) { try { text = String(node); } catch(e) {} }
                                if (text.indexOf(targetText) >= 0) {
                                    if (expand) { tree.expandPath(path); } else { tree.collapsePath(path); }
                                    result.set(true);
                                    break;
                                }
                            }
                        }
                    }));
                    return result.get();
                })()
                """.trimIndent(),
                true
            )
            check(success) { "Could not find tree node '$nodeText' to ${if (expanded) "expand" else "collapse"}" }
        }
    }

    private fun RemoteRobot.modifyFileWithoutSave(fileName: String, content: String) {
        val typedContent = content.trimEnd('\r', '\n')

        focusEditorFile(fileName)

        keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(typedContent)
        }
    }

    private fun RemoteRobot.focusEditorFile(fileName: String) {
        idea {
            openFile(fileName)
        }

        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        const editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
                        if (editor) {
                            editor.getContentComponent().requestFocusInWindow();
                        }
                    }
                }));
            }
            """.trimIndent(),
            false
        )
    }

    private fun RemoteRobot.moveCaretToLineEnd(lineIndex: Int) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        const editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
                        if (!editor) {
                            return;
                        }

                        const document = editor.getDocument();
                        const safeLine = Math.max(0, Math.min($lineIndex, document.getLineCount() - 1));
                        const offset = document.getLineEndOffset(safeLine);
                        editor.getCaretModel().moveToOffset(offset);
                        editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER);
                        editor.getContentComponent().requestFocusInWindow();
                    }
                }));
            }
            """.trimIndent(),
            false
        )
    }

    private fun RemoteRobot.insertSingleCharacterAtCaretWithoutSave() {
        val textLiteral = toJsStringLiteral("a")
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        const editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
                        if (!editor) {
                            return;
                        }
                        const document = editor.getDocument();
                        const caretModel = editor.getCaretModel();
                        const offset = caretModel.getOffset();
                        const text = $textLiteral;

                        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, new java.lang.Runnable({
                            run: function() {
                                document.insertString(offset, text);
                                caretModel.moveToOffset(offset + text.length);
                            }
                        }));

                        editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER);
                        editor.getContentComponent().requestFocusInWindow();
                    }
                }));
            }
            """.trimIndent(),
            false
        )
    }

    private fun RemoteRobot.currentBrowserLineStatsSnapshot(): String = callJs(
        """
        (function() {
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) return "project=missing";

            const toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("GitChangesView");
            const browser = toolWindow && toolWindow.getContentManager().getSelectedContent()
                ? toolWindow.getContentManager().getSelectedContent().getComponent()
                : null;
            if (!browser) return "browser=missing";

            const lineStats = browser.currentLineStatsSnapshot();
            if (!lineStats) return "currentChanges=null";
            const entries = [];
            const it = lineStats.iterator();
            while (it.hasNext()) {
                entries.push(String(it.next()));
            }
            return entries.join(",");
        })();
        """.trimIndent(),
        true
    )

    private fun IdeaFrame.waitForMainFileLineStats(changedLineCount: Int) {
        val fileName = "Main.txt"
        val expectedAdded = "+$changedLineCount"
        val expectedRemoved = "-$changedLineCount"

        try {
            var renderedMetadata: String
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(100)) {
                renderedMetadata = remoteRobot.renderedMainFileMetadata()
                selectedChangesTreeContains(fileName) &&
                    renderedMetadata.contains(expectedAdded) &&
                    renderedMetadata.contains(expectedRemoved)
            }
        } catch (_: WaitForConditionTimeoutException) {
            val debugSnapshot = "settings=${treeContextSettingsSnapshot()} diff=${activeDiffSnapshot()} browserStats=${remoteRobot.currentBrowserLineStatsSnapshot()} rendered=${remoteRobot.renderedMainFileMetadata()}"
            assertTrue(
                false,
                "Expected $fileName to render $expectedAdded/$expectedRemoved immediately after the edit. $debugSnapshot"
            )
        }
    }

    private fun RemoteRobot.renderedMainFileMetadata(): String {
        val fileNameLiteral = toJsStringLiteral("Main.txt")
        return callJs(
            """
            (function() {
                var result = new java.util.concurrent.atomic.AtomicReference("");
                function findTree() {
                    var windows = java.awt.Window.getWindows();
                    for (var w = 0; w < windows.length; w++) {
                        var queue = new java.util.LinkedList();
                        queue.add(windows[w]);
                        while (!queue.isEmpty()) {
                            var component = queue.poll();
                            if (component && component.getClass().getName().endsWith("LstCrcAsyncChangesTree") && component.isShowing()) {
                                return component;
                            }
                            if (!component) continue;
                            try {
                                var children = component.getComponents();
                                if (children) {
                                    for (var ci = 0; ci < children.length; ci++) {
                                        queue.add(children[ci]);
                                    }
                                }
                            } catch (ignored) {}
                        }
                    }
                    return null;
                }

                function findDeclaredField(instance, fieldName) {
                    var cls = instance.getClass();
                    while (cls) {
                        try {
                            var field = cls.getDeclaredField(fieldName);
                            field.setAccessible(true);
                            return field;
                        } catch (ignored) {
                            cls = cls.getSuperclass();
                        }
                    }
                    return null;
                }

                function fragmentText(component) {
                    if (!component) return "";
                    var fragmentsField = findDeclaredField(component, "myFragments");
                    if (!fragmentsField) return "";
                    var fragments = fragmentsField.get(component);
                    if (!fragments) return "";

                    var values = [];
                    var iterator = fragments.iterator();
                    while (iterator.hasNext()) {
                        var fragment = iterator.next();
                        var textField = findDeclaredField(fragment, "myText") || findDeclaredField(fragment, "text");
                        if (textField) {
                            values.push(String(textField.get(fragment)));
                        }
                    }
                    return values.join("");
                }

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        var tree = findTree();
                        if (!tree) {
                            result.set("tree=missing");
                            return;
                        }
                        var renderer = tree.getCellRenderer();
                        if (!renderer) {
                            result.set("renderer=missing");
                            return;
                        }

                        for (var row = 0; row < tree.getRowCount(); row++) {
                            var path = tree.getPathForRow(row);
                            if (!path) continue;
                            var node = path.getLastPathComponent();
                            if (!node) continue;
                            var userObject = node.getUserObject ? node.getUserObject() : null;
                            var change = userObject instanceof com.intellij.openapi.vcs.changes.Change ? userObject : null;
                            if (!change) continue;

                            var candidate = change.getAfterRevision() ? change.getAfterRevision().getFile().getName() : null;
                            if (!candidate && change.getBeforeRevision()) {
                                candidate = change.getBeforeRevision().getFile().getName();
                            }
                            if (String(candidate || "") !== $fileNameLiteral) continue;

                            renderer.getTreeCellRendererComponent(tree, node, false, tree.isExpanded(row), tree.getModel().isLeaf(node), row, false);
                            var trailingField = findDeclaredField(renderer, "trailingRenderer");
                            if (!trailingField) {
                                result.set("trailing=missing");
                                return;
                            }
                            var trailingRenderer = trailingField.get(renderer);
                            var fragmentCount = "";
                            try {
                                fragmentCount = String(trailingRenderer.getFragmentCount());
                            } catch (ignored) {
                                fragmentCount = "unknown";
                            }
                            result.set("count=" + fragmentCount + "|text=" + fragmentText(trailingRenderer));
                            return;
                        }

                        result.set("row=missing");
                    }
                }));

                return result.get();
            })();
            """.trimIndent(),
            true
        )
    }

    @Suppress("SameParameterValue")
    private fun RemoteRobot.fileStatusDebug(fileName: String): String = callJs(
        """
        (function() {
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) return "";

            const file = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(String(project.getBasePath()).replace('\\', '/') + "/" + ${toJsStringLiteral(fileName)});
            const statusManager = com.intellij.openapi.vcs.FileStatusManager.getInstance(project);
            const status = file ? statusManager.getStatus(file) : null;
            return [
                "filePresent=" + (file != null),
                "status=" + (status ? String(status.getId()) : "null")
            ].join(";");
        })();
        """.trimIndent(),
        true
    )

    private fun toJsStringLiteral(value: String): String {
        val escaped = buildString(value.length + 2) {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
        return escaped
    }
}