package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.core.MouseButton
import java.time.Duration

fun IdeaFrame.gitChangesView(function: GitChangesViewFixture.() -> Unit) {
    find<GitChangesViewFixture>(byXpath("//div[@class='LstCrcChangesBrowser']"), Duration.ofSeconds(10)).apply(function)
}

@FixtureName("GitChangesView")
class GitChangesViewFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {

    val tabContents: List<ComponentFixture>
        get() = remoteRobot.findAll(byXpath("//div[@class='ContentTabLabel']"))

    fun hasTab(tabName: String): Boolean {
        return remoteRobot.findAll<ComponentFixture>(
            byXpath("//div[@class='ContentTabLabel' and (@text='$tabName' or @accessiblename='$tabName' or @visible_text='$tabName')]")
        ).isNotEmpty()
    }

    fun selectTab(tabName: String) {
        step("Select tab '$tabName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(500)) {
                remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='ContentTabLabel' and (@text='$tabName' or @accessiblename='$tabName' or @visible_text='$tabName')]")).isNotEmpty()
            }
            remoteRobot.find<ComponentFixture>(byXpath("//div[@class='ContentTabLabel' and (@text='$tabName' or @accessiblename='$tabName' or @visible_text='$tabName')]")).click()
        }
    }

    val changesTree: ContainerFixture
        get() = remoteRobot.find(byXpath("//div[@class='LstCrcAsyncChangesTree' or @class='ChangesTree']"))

    fun clickChange(fileName: String, button: MouseButton = MouseButton.LEFT_BUTTON) {
        step("Click '$fileName' with $button") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                changesTree.findAllText(fileName).isNotEmpty()
            }
            changesTree.findText(fileName).click(button)
        }
    }

    fun doubleClickChange(fileName: String) {
        step("Double click '$fileName'") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                changesTree.findAllText(fileName).isNotEmpty()
            }
            changesTree.findText(fileName).doubleClick()
        }
    }

    fun rightClickChange(fileName: String) {
        clickChange(fileName, MouseButton.RIGHT_BUTTON)
    }

    fun addTab() {
        step("Click 'Add Tab' button") {
            waitFor(Duration.ofSeconds(10), interval = Duration.ofMillis(250)) {
                remoteRobot.findAll<ComponentFixture>(byXpath("//div[@accessiblename='Add Tab']")).isNotEmpty()
            }
            remoteRobot.find<ComponentFixture>(byXpath("//div[@accessiblename='Add Tab']")).click()
        }
    }
}
