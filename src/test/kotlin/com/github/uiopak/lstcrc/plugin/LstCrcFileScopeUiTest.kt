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
                    changesTree.findAllText("Main.txt").isNotEmpty() &&
                    changesTree.findAllText("Moved.txt").isNotEmpty() &&
                        changesTree.findAllText("ToDelete.txt").isNotEmpty() &&
                        changesTree.findAllText("NewFile.txt").isNotEmpty()
                }
            }

            var scopesDebug = ""
            waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(500)) {
                scopesDebug = callJs<String>(
                    """
                    (function() {
                        const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                        if (!project) return "projectMissing=true";

                        const holders = com.intellij.psi.search.scope.packageSet.NamedScopesHolder.getAllNamedScopeHolders(project);
                        const scopeIds = ["LSTCRC.Created", "LSTCRC.Modified", "LSTCRC.Moved", "LSTCRC.Deleted"];
                        const resolved = {};
                        const holderMap = {};

                        for (let i = 0; i < holders.length; i++) {
                            const holder = holders[i];
                            const scopes = holder.getEditableScopes();
                            for (let j = 0; j < scopes.length; j++) {
                                const scope = scopes[j];
                                const id = String(scope.getScopeId());
                                if (scopeIds.indexOf(id) >= 0) {
                                    resolved[id] = scope;
                                    holderMap[id] = holder;
                                }
                            }
                        }

                        const vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
                        const createdFile = vfs.refreshAndFindFileByPath(project.getBasePath() + "/NewFile.txt");
                        const modifiedFile = vfs.refreshAndFindFileByPath(project.getBasePath() + "/Main.txt");
                        const movedFile = vfs.refreshAndFindFileByPath(project.getBasePath() + "/Moved.txt");
                        const diffDataService = project.getService(com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService);
                        const deletedFiles = diffDataService ? diffDataService.getDeletedFiles() : java.util.Collections.emptyList();
                        let deletedFile = null;
                        for (let i = 0; i < deletedFiles.size(); i++) {
                            const candidate = deletedFiles.get(i);
                            if (String(candidate.getPath()).endsWith("/ToDelete.txt") || String(candidate.getPath()).endsWith("\\ToDelete.txt")) {
                                deletedFile = candidate;
                                break;
                            }
                        }

                        function contains(scopeId, file) {
                            const scope = resolved[scopeId];
                            const holder = holderMap[scopeId];
                            return !!(scope && holder && file && scope.getValue() && scope.getValue().contains(file, project, holder));
                        }

                        return [
                            "created=" + contains("LSTCRC.Created", createdFile),
                            "modified=" + contains("LSTCRC.Modified", modifiedFile),
                            "moved=" + contains("LSTCRC.Moved", movedFile),
                            "deleted=" + contains("LSTCRC.Deleted", deletedFile),
                            "deletedFilePresent=" + (deletedFile != null)
                        ].join(";");
                    })();
                    """.trimIndent(),
                    true
                )

                scopesDebug.contains("created=true") &&
                    scopesDebug.contains("modified=true") &&
                    scopesDebug.contains("moved=true") &&
                    scopesDebug.contains("deleted=true")
            }

            Assertions.assertTrue(
                scopesDebug.contains("created=true") &&
                    scopesDebug.contains("modified=true") &&
                    scopesDebug.contains("moved=true") &&
                    scopesDebug.contains("deleted=true"),
                "HEAD scopes should classify created/modified/moved/deleted files correctly: $scopesDebug"
            )
        }
    }
}