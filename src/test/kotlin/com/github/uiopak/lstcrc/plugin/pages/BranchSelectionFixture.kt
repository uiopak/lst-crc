package com.github.uiopak.lstcrc.plugin.pages

import com.github.uiopak.lstcrc.plugin.utils.jsNoticeExternalChanges
import com.github.uiopak.lstcrc.plugin.utils.jsOpenProject
import com.github.uiopak.lstcrc.plugin.utils.jsRefreshSelectedComparison
import com.github.uiopak.lstcrc.plugin.utils.toJsStringLiteral
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun IdeaFrame.branchSelection(function: BranchSelectionFixture.() -> Unit) {
    val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(90) else Duration.ofSeconds(20)
    val locator = byXpath("//div[@class='BranchSelectionPanel']")
    var branchSelectionFixture: BranchSelectionFixture? = null
    waitFor(timeout, interval = Duration.ofMillis(500)) {
        branchSelectionFixture = remoteRobot.findAll<BranchSelectionFixture>(locator).firstOrNull()
        branchSelectionFixture != null
    }
    branchSelectionFixture!!.apply(function)
}

/** Opens the "Select Branch" tab through the "+" button and picks [branchName], which opens its comparison tab. */
fun IdeaFrame.addComparisonTab(branchName: String) {
    gitChangesView { addTab() }
    branchSelection { searchAndSelect(branchName) }
}

@FixtureName("BranchSelection")
class BranchSelectionFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        private val treeLocator = byXpath(
            "Branch selection tree",
            "//div[@class='BranchSelectionPanel']//div[@class='Tree']"
        )
    }


    private fun ContainerFixture.isShowingOnScreen(): Boolean {
        return runCatching {
            callJs<Boolean>("component.isShowing()", true)
        }.getOrDefault(false)
    }

    private fun findShowingBranchTree(): ContainerFixture? {
        return remoteRobot.findAll<ContainerFixture>(treeLocator)
            .firstOrNull { it.isShowingOnScreen() }
    }

    private fun waitForBranchTree(): ContainerFixture {
        val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
        var branchTree: ContainerFixture? = null
        waitFor(timeout, interval = Duration.ofMillis(250)) {
            branchTree = findShowingBranchTree()
            branchTree != null
        }
        return branchTree!!
    }

    private fun waitForPanelToClose(
        timeout: Duration = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(30) else Duration.ofSeconds(10)
    ): Boolean = runCatching {
        waitFor(timeout, interval = Duration.ofMillis(250)) {
            remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='BranchSelectionPanel']")).isEmpty()
        }
        true
    }.getOrDefault(false)

    fun setSearchTerm(searchTerm: String) {
        step("Set branch search term to '$searchTerm'") {
            runJs(
                """
                (function() {
                    function findComponent(root, classNameSuffix) {
                        if (!root) return null;
                        if (String(root.getClass().getName()).endsWith(classNameSuffix)) return root;
                        if (!root.getComponents) return null;
                        const children = root.getComponents();
                        for (let i = 0; i < children.length; i++) {
                            var match = findComponent(children[i], classNameSuffix);
                            if (match != null) return match;
                        }
                        return null;
                    }

                    const searchField = findComponent(component, "SearchTextField");
                    if (searchField != null) {
                        searchField.setText(${toJsStringLiteral(searchTerm)});
                    }
                })();
                """.trimIndent(),
                true
            )
        }
    }

    fun hasVisibleBranchText(text: String): Boolean {
        val tree = findShowingBranchTree() ?: return false
        return runCatching { tree.findAllText(text).isNotEmpty() }.getOrDefault(false)
    }

    fun searchAndSelect(branchName: String) {
        step("Search and select branch '$branchName'") {
            remoteRobot.runJs("${jsOpenProject()}\n${jsNoticeExternalChanges()}", false)

            val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(20)
            val branchLabel = branchName.substringAfterLast('/')
            setSearchTerm(branchName)
            waitFor(timeout, interval = Duration.ofMillis(500)) {
                val tree = findShowingBranchTree() ?: return@waitFor false
                runCatching { tree.findAllText(branchLabel).isNotEmpty() }.getOrDefault(false)
            }
            val tree = waitForBranchTree()
            tree.findText(branchLabel).doubleClick()

            // On slow CI runners (seen on Windows) the robot's double-click occasionally does not reach the
            // row, leaving the panel open. Fall back to selecting through the panel, as addTab() does.
            if (!waitForPanelToClose(Duration.ofSeconds(10))) {
                println("[BranchSelectionFixture] Double-click on '$branchName' did not close the panel; selecting it through the panel.")
                val selected = callJs<Boolean>("component.selectVisibleBranchForTest(${toJsStringLiteral(branchName)})", true)
                check(selected) { "Branch '$branchName' is not visible in the branch selection tree." }
                check(waitForPanelToClose()) { "Branch selection panel did not close after selecting '$branchName'." }
            }

            remoteRobot.runJs("${jsOpenProject()}\n${jsRefreshSelectedComparison()}", false)
        }
    }
}
