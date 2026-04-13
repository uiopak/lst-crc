package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.branchSelection
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

@LstCrcUiTest
class LstCrcBranchComparisonUiTest : LstCrcUiTestSupport() {

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
                    const holders = com.intellij.psi.search.scope.packageSet.NamedScopesHolder.getAllNamedScopeHolders(project);
                    let modifiedScope = null;
                    let modifiedHolder = null;
                    for (let i = 0; i < holders.length; i++) {
                        const holder = holders[i];
                        const scopes = holder.getEditableScopes();
                        for (let j = 0; j < scopes.length; j++) {
                            const scope = scopes[j];
                            if (scope.getScopeId() === "LSTCRC.Modified") {
                                modifiedScope = scope;
                                modifiedHolder = holder;
                            }
                        }
                    }

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

                step("Verify local change also appears in feature tabs") {
                    selectTab("feature-1")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Local.txt").isNotEmpty() &&
                            changesTree.findAllText("Feature1.txt").isNotEmpty()
                    }
                }
            }
        }
    }
}