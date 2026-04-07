package com.github.uiopak.lstcrc.plugin.steps

import com.github.uiopak.lstcrc.plugin.pages.idea
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.component
import com.intellij.remoterobot.utils.keyboard

/**
 * Helper class for UI test interactions in PluginUiTest
 */
class PluginUiTestSteps(private val remoteRobot: RemoteRobot) {
    companion object {
        // Flag to track whether we've already handled the "Don't ask again" dialog
        private var dialogHandled = false
    }

    /**
     * Creates a new file with the given name and content
     */
    fun createNewFile(fileName: String, content: String) = with(remoteRobot) {
        step("Create new file: $fileName") {
            step("Left click on ActionButton") {
                component("//div[@accessiblename='New File or Directory…']")
                    .click()
            }
            step("Left click at text 'File' on ListPopupImpl") {
                component("//div[@class='MyList']")
                    .findText("File").click()
            }
            keyboard {
                enterText(fileName)
                enter()
            }

            // Handle "Don't ask again" dialog if it appears (only for the first file)
            if (!dialogHandled) {
                try {
                    step("Left click on Don't ask again") {
                        component("//div[@class='SouthPanel']/div[@class='JPanel']/div[@class='JCheckBox']")
                            .click()
                    }
                    step("Left click on Add") {
                        component("//div[@accessiblename='Add' and @class='JButton']")
                            .click()
                    }
                    // Dialog was successfully handled, set flag to true
                    dialogHandled = true
                } catch (e: Exception) {
                    // Dialog didn't appear, which is unexpected for the first file
                    // but we'll set the flag anyway to prevent future attempts
                    dialogHandled = true
                }
            }

            keyboard {
                enterText(content)
            }

            // Click on Project structure tree to ensure focus is back on the project
            step("Left click on ProjectViewPane") {
                component("//div[@accessiblename='Project structure tree']")
                    .click()
            }
        }
    }

    /**
     * Modifies an existing file by clicking on it and adding content
     */
    fun modifyFile(fileName: String, content: String) = with(remoteRobot) {
        step("Modify file: $fileName") {
            step("Left click on $fileName") {
                component("//div[@accessiblename='$fileName' and @class='SimpleColoredComponent']")
                    .click()
            }
            keyboard {
                enter()
                enterText(content)
            }
        }
    }

    /**
     * Replaces content in an existing file
     */
    fun replaceFileContent(fileName: String, content: String) = with(remoteRobot) {
        step("Replace content in file: $fileName") {
            step("Left click on $fileName") {
                component("//div[@accessiblename='$fileName' and @class='SimpleColoredComponent']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+A'") { hotKey(17, 65) }

                // Split content by newlines and enter each line separately
                val lines = content.split("\n")
                for (i in lines.indices) {
                    enterText(lines[i])
                    if (i < lines.size - 1) {
                        // Press Enter between lines, but not after the last line
                        enter()
                    }
                }
            }
        }
    }

    /**
     * Performs a Git commit with the given commit message
     */
    fun commitChanges(commitMessage: String) = with(remoteRobot) {
        step("Commit changes with message: $commitMessage") {
            step("Left click on SquareStripeButton") {
                component("//div[@accessiblename='Commit']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+K', select all files from active changelist") { hotKey(17, 75) }
            }
            step("Left click on Commit Message") {
                component("//div[@accessiblename='Commit Message']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+A'") { hotKey(17, 65) }
                enterText(commitMessage)
            }
            step("Left click on Commit") {
                component("//div[@accessiblename='Commit' and @class='MainButton']")
                    .click()
            }
        }
    }

    /**
     * Switches to the Project view
     */
    fun switchToProjectView() = with(remoteRobot) {
        step("Switch to Project view") {
            step("Left click on SquareStripeButton") {
                component("//div[@accessiblename='Project']")
                    .click()
            }
            step("Left click on ProjectViewPane") {
                component("//div[@accessiblename='Project structure tree']")
                    .click()
            }
        }
    }
}
