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
    find<BranchSelectionFixture>(byXpath("//div[@class='BranchSelectionPanel']"), Duration.ofSeconds(10)).apply(function)
}

@FixtureName("BranchSelection")
class BranchSelectionFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    fun searchAndSelect(branchName: String) {
        step("Search and select branch '$branchName'") {
            val searchField = find<ComponentFixture>(byXpath("//div[@class='SearchTextField']"))
            searchField.click()
            keyboard {
                hotKey(17, 65)
                enterText(branchName)
                enter()
            }
            
            // The tree should now show the branch. We double click it.
            val tree = find<ContainerFixture>(byXpath("//div[@class='Tree']"))
            waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(500)) {
                tree.findAllText(branchName).isNotEmpty()
            }
            tree.findText(branchName).doubleClick()
        }
    }
}
