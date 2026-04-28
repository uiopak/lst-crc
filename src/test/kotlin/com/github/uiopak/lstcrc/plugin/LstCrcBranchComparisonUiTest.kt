package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.branchSelection
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions
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
                scopeDebug = callJs<String>(
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

            var scopesUpdated = false
            var scopesDebug = ""
            repeat(10) {
                scopesDebug = callJs<String>(
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
                scopesUpdated = scopesDebug.contains("result=true")
                if (!scopesUpdated && it < 9) {
                    Thread.sleep(1000)
                }
            }

            if (scopesDebug.contains("scopeFound=true")) {
                Assertions.assertTrue(scopesUpdated, "Custom scopes should be updated with modified files: $scopesDebug")
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

    private fun RemoteRobot.modifyFileWithoutSave(fileName: String, content: String) {
        val typedContent = content.trimEnd('\r', '\n')

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

        keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(typedContent)
        }
    }

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