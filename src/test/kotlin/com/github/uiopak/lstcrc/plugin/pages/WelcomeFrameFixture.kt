package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.hasAnyComponent

@DefaultXpath(by = "FlatWelcomeFrame type", xpath = "//div[@class='FlatWelcomeFrame']")
@FixtureName(name = "Welcome Frame")
class WelcomeFrameFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    CommonContainerFixture(remoteRobot, remoteComponent) {
    fun createNewProjectLink(): ComponentFixture {
        return welcomeFrameLink("New Project")
    }

    fun importProjectLink(): ComponentFixture {
        return welcomeFrameLink("Get from VCS")
    }

    private fun welcomeFrameLink(text: String?): ComponentFixture {
        if (this.hasAnyComponent(byXpath("//div[@class='NewRecentProjectPanel']"))) {
            return find<ComponentFixture>(
                ComponentFixture::class.java,
                byXpath("//div[@class='JBOptionButton' and @text='" + text + "']")
            )
        }
        return find<ComponentFixture>(
            ComponentFixture::class.java,
            byXpath("//div[(@class='MainButton' and @text='" + text + "') or (@accessiblename='" + text + "' and @class='JButton')]")
        )
    }
}