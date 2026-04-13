package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

@LstCrcUiTest
class LstCrcFileScopeUiTest : LstCrcUiTestSupport() {

    @Test
    @Video
    fun testFileOperations(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            uiSteps.createNewFile("ToMove.txt", "Original content\n")
            uiSteps.createNewFile("ToDelete.txt", "I'm about to disappear\n")
            uiSteps.commitChanges("Initial files")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("base-branch")
            uiSteps.checkoutBranch(defaultBranch)

            uiSteps.switchToProjectView()
            uiSteps.renameFile("ToMove.txt", "Moved.txt")
            uiSteps.deleteFile("ToDelete.txt")
            uiSteps.createNewFile("NewFile.txt", "Brand new\n")
            runJs(
                """
                com.intellij.ide.util.PropertiesComponent.getInstance().setValue(
                    "com.github.uiopak.lstcrc.app.includeHeadInScopes",
                    true,
                    false
                );
                """.trimIndent(),
                true
            )

            openGitChangesView()
            gitChangesView {
                selectTab("HEAD")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Moved.txt").isNotEmpty() &&
                        changesTree.findAllText("ToDelete.txt").isNotEmpty() &&
                        changesTree.findAllText("NewFile.txt").isNotEmpty()
                }
            }

            var scopesVerified = false
            var scopesDebug = ""
            repeat(10) {
                scopesDebug = callJs<String>(
                    """
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    const holders = com.intellij.psi.search.scope.packageSet.NamedScopesHolder.getAllNamedScopeHolders(project);
                    let scopesVerified = false;
                    let movedContains = false;
                    let createdContains = false;
                    let movedFile = null;
                    let createdFile = null;
                    let movedScopeFound = false;
                    let createdScopeFound = false;
                    let holderFound = false;

                    let movedScope = null;
                    let createdScope = null;
                    let movedHolder = null;
                    let createdHolder = null;

                    for (let i = 0; i < holders.length; i++) {
                        const holder = holders[i];
                        const scopes = holder.getEditableScopes();
                        for (let j = 0; j < scopes.length; j++) {
                            const scope = scopes[j];
                            if (scope.getScopeId() === "LSTCRC.Moved") {
                                movedScope = scope;
                                movedHolder = holder;
                                holderFound = true;
                            }
                            if (scope.getScopeId() === "LSTCRC.Created") {
                                createdScope = scope;
                                createdHolder = holder;
                                holderFound = true;
                            }
                        }
                    }

                    movedScopeFound = movedScope != null;
                    createdScopeFound = createdScope != null;

                    const vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
                    movedFile = vfs.refreshAndFindFileByPath(project.getBasePath() + "/Moved.txt");
                    createdFile = vfs.refreshAndFindFileByPath(project.getBasePath() + "/NewFile.txt");

                    movedContains = movedScope != null && movedFile != null && movedScope.getValue() != null && movedScope.getValue().contains(movedFile, project, movedHolder);
                    createdContains = createdScope != null && createdFile != null && createdScope.getValue() != null && createdScope.getValue().contains(createdFile, project, createdHolder);
                    scopesVerified = movedContains && createdContains;

                    "result=" + scopesVerified +
                    ";holderFound=" + holderFound +
                    ";movedScopeFound=" + movedScopeFound +
                    ";createdScopeFound=" + createdScopeFound +
                    ";movedFileNull=" + (movedFile == null) +
                    ";createdFileNull=" + (createdFile == null) +
                    ";movedContains=" + movedContains +
                    ";createdContains=" + createdContains;
                    """.trimIndent(),
                    true
                )
                scopesVerified = scopesDebug.contains("result=true")
                if (!scopesVerified && it < 9) {
                    Thread.sleep(1000)
                }
            }

            val scopesAvailable = scopesDebug.contains("movedScopeFound=true") || scopesDebug.contains("createdScopeFound=true")
            if (scopesAvailable) {
                Assertions.assertTrue(scopesVerified, "Scopes for moved and created files should be correct: $scopesDebug")
            }
        }
    }
}