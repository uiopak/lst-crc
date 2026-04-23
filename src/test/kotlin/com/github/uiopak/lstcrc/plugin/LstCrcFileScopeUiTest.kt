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

            uiSteps.createNewFile("Main.txt", "Base content\n")
            uiSteps.createNewFile("ToMove.txt", "Original content\n")
            uiSteps.createNewFile("ToDelete.txt", "I'm about to disappear\n")
            uiSteps.commitChanges("Initial files")
            val defaultBranch = uiSteps.defaultBranchName()

            uiSteps.createBranch("base-branch")
            uiSteps.checkoutBranch(defaultBranch)

            uiSteps.switchToProjectView()
            uiSteps.modifyFile("Main.txt", "Base content updated\n")
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
            }

            var scopesDebug = ""
            val scopesDeadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
            while (System.nanoTime() < scopesDeadline) {
                scopesDebug = callJs<String>(
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

                                const vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
                                const normalizedBasePath = String(project.getBasePath()).split('\\').join('/');
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

                                    const createdFile = diffDataService ? findBySuffix(diffDataService.getCreatedFiles(), "NewFile.txt") : null;
                                    const modifiedFile = diffDataService ? findBySuffix(diffDataService.getModifiedFiles(), "Main.txt") : null;
                                    const movedFile = diffDataService ? findBySuffix(diffDataService.getMovedFiles(), "Moved.txt") : null;
                                    const deletedFile = diffDataService ? findBySuffix(diffDataService.getDeletedFiles(), "ToDelete.txt") : null;

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

                if (
                    scopesDebug.contains("created=true") &&
                    scopesDebug.contains("modified=true") &&
                    scopesDebug.contains("moved=true") &&
                    scopesDebug.contains("deleted=true")
                ) {
                    break
                }

                Thread.sleep(500)
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



