// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.uiopak.lstcrc.plugin.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor

fun RemoteRobot.actionMenuItem(text: String): ActionMenuItemFixture {
    val xpath = byXpath("text '$text'", "//div[@class='ActionMenuItem' and @text='$text']")
    waitFor {
        findAll<ActionMenuItemFixture>(xpath).isNotEmpty()
    }
    return findAll<ActionMenuItemFixture>(xpath).first()
}

@FixtureName("ActionMenuItem")
class ActionMenuItemFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : ComponentFixture(remoteRobot, remoteComponent)