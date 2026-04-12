package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun IdeaFrame.branchSelection(function: BranchSelectionFixture.() -> Unit) {
    val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(30)
    waitFor(timeout, interval = Duration.ofMillis(500)) {
        remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='BranchSelectionPanel']")).isNotEmpty()
    }
    remoteRobot.find<BranchSelectionFixture>(byXpath("//div[@class='BranchSelectionPanel']"), Duration.ofSeconds(5)).apply(function)
}

@FixtureName("BranchSelection")
class BranchSelectionFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        private val searchFieldLocator = byXpath(
            "Branch search field",
            "//div[@class='BranchSelectionPanel']//div[@accessiblename='Search' and @class='TextFieldWithProcessing']"
        )
        private val treeLocator = byXpath(
            "Branch selection tree",
            "//div[@class='BranchSelectionPanel']//div[@class='Tree']"
        )
    }

    fun searchAndSelect(branchName: String) {
        step("Search and select branch '$branchName'") {
            remoteRobot.runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (project) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                        run: function() {
                            const basePath = project.getBasePath();
                            if (basePath != null) {
                                const fileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
                                const normalizedBasePath = String(basePath).split("\\").join("/");
                                const projectDir = fileSystem.refreshAndFindFileByPath(normalizedBasePath);
                                if (projectDir != null) {
                                    projectDir.refresh(false, true);
                                    const gitDir = projectDir.findChild(".git");
                                    if (gitDir != null) {
                                        gitDir.refresh(false, true);
                                    }
                                }
                            }
                            com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                        }
                    }));
                }
                """.trimIndent(),
                true
            )

            val searchTimeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
            waitFor(searchTimeout, interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(searchFieldLocator).isNotEmpty()
            }

            val searchField = remoteRobot.find<ComponentFixture>(searchFieldLocator, Duration.ofSeconds(5))
            searchField.click()
            keyboard {
                hotKey(17, 65)
                enterText(branchName)
                enter()
            }

            // The tree should now show the branch. We double click it.
            val tree = remoteRobot.find<ContainerFixture>(treeLocator, Duration.ofSeconds(5))
            val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(20)
            waitFor(timeout, interval = Duration.ofMillis(500)) {
                tree.findAllText(branchName).isNotEmpty()
            }
            tree.findText(branchName).doubleClick()
        }
    }
}
