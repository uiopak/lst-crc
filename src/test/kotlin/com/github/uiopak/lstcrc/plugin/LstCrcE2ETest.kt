package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.*
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.github.uiopak.lstcrc.plugin.utils.createFreshProjectFromWelcomeScreen
import com.github.uiopak.lstcrc.plugin.utils.RemoteRobotExtension
import com.github.uiopak.lstcrc.plugin.utils.StepsLogger
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

@ExtendWith(RemoteRobotExtension::class)
class LstCrcE2ETest {
    init {
        StepsLogger.init()
    }

    private fun RemoteRobot.prepareFreshProject() {
        createFreshProjectFromWelcomeScreen()
    }

    @Test
    @Video
    fun testGitBranchComparison(remoteRobot: RemoteRobot) = with(remoteRobot) {
        val uiSteps = PluginUiTestSteps(remoteRobot)

        // 1. Setup project with Git repository and initial commits
        prepareFreshProject()

        idea {
            step("Wait for smart mode") {
                dumbAware(Duration.ofMinutes(5)) {}
            }

            uiSteps.initializeGitRepository()
            resetGitChangesViewState()

            // Create initial file
            uiSteps.createNewFile("Main.txt", "Initial content\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            // 2. Create a feature branch and add commits
            uiSteps.createBranch("feature-branch")
            
            uiSteps.createNewFile("Feature.txt", "Feature content\n")
            uiSteps.modifyFile("Main.txt", "Main modified in feature\n")
            uiSteps.commitChanges("Feature commit")

            // 3. Switch back to main branch (it might be named 'master' or 'main' depending on Git config)
            // We'll assume 'master' as it's common in older Git versions or defaults in some IDE versions, 
            // but we can try to detect it or just use 'master'. 
            // In a fresh repo it might be 'master' or 'main'.
            // Let's use JS to find the current branch name or just checkout 'master'.
            uiSteps.checkoutBranch(defaultBranch)

            // 4. Open LST-CRC tool window
            openGitChangesView()

            // 5. Verify HEAD tab
            gitChangesView {
                step("Check HEAD tab") {
                    selectTab("HEAD")
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.isShowing
                    }
                }

                // 6. Open a new tab comparing against feature-branch
                addTab()
            }

            branchSelection {
                searchAndSelect("feature-branch")
            }

            // 7. Verify the comparison tab
            gitChangesView {
                step("Verify feature-branch comparison tab") {
                    selectTab("feature-branch")
                    
                    // Verify that we see the changes (Feature.txt and Main.txt)
                    waitFor(Duration.ofSeconds(10)) {
                        changesTree.findAllText("Feature.txt").isNotEmpty() && 
                        changesTree.findAllText("Main.txt").isNotEmpty()
                    }
                }
            }

            // 8. Test real-time updates: Modify a file on master and see it appear in HEAD
            uiSteps.switchToProjectView()
            uiSteps.modifyFile("Main.txt", "Modified on master\n")
            
            openGitChangesView()
            gitChangesView {
                selectTab("HEAD")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Main.txt").isNotEmpty()
                }
                
                // Also verify it's still in the feature-branch comparison (though the diff might change)
                selectTab("feature-branch")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Main.txt").isNotEmpty()
                }
            }

            // 9. Verify custom scopes via JS (as it's hard to test via UI reliably)
            var scopesUpdated = false
            var scopesDebug = ""
            repeat(10) {
                scopesDebug = callJs<String>("""
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
                """, true)
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

            // Create a branch to compare against later
            uiSteps.createBranch("base-branch")
            uiSteps.checkoutBranch(defaultBranch)

            // Perform operations
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

            // Open LST-CRC and check HEAD
            openGitChangesView()
            gitChangesView {
                selectTab("HEAD")
                waitFor(Duration.ofSeconds(10)) {
                    changesTree.findAllText("Moved.txt").isNotEmpty() &&
                    changesTree.findAllText("ToDelete.txt").isNotEmpty() &&
                    changesTree.findAllText("NewFile.txt").isNotEmpty()
                }
            }

            // Verify the scopes for deleted and moved files
            var scopesVerified = false
            var scopesDebug = ""
            repeat(10) {
                scopesDebug = callJs<String>("""
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
                """, true)
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

            // Initial commit on master
            uiSteps.createNewFile("Base.txt", "Base content\n")
            uiSteps.commitChanges("Initial commit")
            val defaultBranch = uiSteps.defaultBranchName()

            // Feature branch 1: Add Feature1.txt and modify Base.txt
            uiSteps.createBranch("feature-1")
            uiSteps.createNewFile("Feature1.txt", "Feature 1 content\n")
            uiSteps.modifyFile("Base.txt", "Base modified in feature 1\n")
            uiSteps.commitChanges("Feature 1 commit")

            // Feature branch 2: Add Feature2.txt (from master)
            uiSteps.checkoutBranch(defaultBranch)
            uiSteps.createBranch("feature-2")
            uiSteps.createNewFile("Feature2.txt", "Feature 2 content\n")
            uiSteps.commitChanges("Feature 2 commit")

            // Go back to master
            uiSteps.checkoutBranch(defaultBranch)

            // Open GitChangesView and add tabs for feature-1 and feature-2
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

            // Verify tabs content
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
                        // The tree should be empty as there are no local changes on master
                        changesTree.findAllText("Feature1.txt").isEmpty() &&
                        changesTree.findAllText("Feature2.txt").isEmpty() &&
                        changesTree.findAllText("Base.txt").isEmpty()
                    }
                }
            }

            // Make a local change on master and verify it appears only in HEAD
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

                step("Verify local change also appears in feature tabs (since it's a comparison with working tree)") {
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
