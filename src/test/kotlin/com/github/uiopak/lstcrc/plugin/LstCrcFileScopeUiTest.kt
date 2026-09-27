package com.github.uiopak.lstcrc.plugin

import com.automation.remarks.junit5.Video
import com.github.uiopak.lstcrc.plugin.pages.filesMatchingScope
import com.github.uiopak.lstcrc.plugin.pages.gitChangesView
import com.github.uiopak.lstcrc.plugin.pages.idea
import com.github.uiopak.lstcrc.plugin.steps.PluginUiTestSteps
import com.intellij.remoterobot.RemoteRobot
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
            dumbAware {}

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
            setIncludeHeadInScopes(true)

            openGitChangesView()
            gitChangesView {
                selectTab("HEAD")
            }

            // On the HEAD tab each file is in its own scope and no other.
            val expected = mapOf(
                "LSTCRC.Created" to setOf("NewFile.txt"),
                "LSTCRC.Modified" to setOf("Main.txt"),
                "LSTCRC.Moved" to setOf("Moved.txt"),
                "LSTCRC.Deleted" to setOf("ToDelete.txt")
            )
            val candidates = listOf("NewFile.txt", "Main.txt", "Moved.txt", "ToDelete.txt")
            var actual = emptyMap<String, Set<String>>()
            runCatching {
                waitFor(Duration.ofSeconds(20), interval = Duration.ofMillis(500)) {
                    actual = expected.keys.associateWith { filesMatchingScope(it, candidates) }
                    actual == expected
                }
            }
            Assertions.assertEquals(expected, actual, "HEAD scopes should classify created/modified/moved/deleted files")
        }
    }
}



