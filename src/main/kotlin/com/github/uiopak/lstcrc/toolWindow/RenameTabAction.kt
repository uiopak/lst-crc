package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.content.Content
import java.awt.Component

class RenameTabAction : AnAction() {

    private val logger = thisLogger()

    private fun getContent(e: AnActionEvent): Content? {
        val component: Component? = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
        if (component == null) {
            logger.debug("CONTEXT_COMPONENT is null, cannot determine right-clicked tab.")
            return null
        }

        // The component is likely an internal class like `ContentTabLabel`.
        // We use reflection to access its `myContent` field. This is fragile and may
        // break in future IDE versions, but it's a known way to solve this.
        return try {
            val field = component.javaClass.getDeclaredField("myContent")
            field.isAccessible = true
            val content = field.get(component) as? Content
            if (content == null) {
                logger.debug("Reflection succeeded, but 'myContent' is not a Content or is null.")
            }
            content
        } catch (ex: NoSuchFieldException) {
            logger.warn("Could not find field 'myContent' in component ${component.javaClass.name}. The IDE's internal structure may have changed.")
            null
        } catch (ex: Exception) {
            logger.warn("Failed to get 'myContent' via reflection from ${component.javaClass.name}", ex)
            null
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
        if (project == null || toolWindow == null || toolWindow.id != "GitChangesView") {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Get the content of the RIGHT-CLICKED tab, not the selected one.
        val content = getContent(e)

        // A tab is renamable if it's a closeable tab that has a branch name associated with it.
        // The "Select Branch" tab is closeable but does not have this key.
        // The "HEAD" tab is not closeable.
        val isRenamable = content != null &&
                content.isCloseable &&
                content.getUserData(LstCrcKeys.BRANCH_NAME_KEY) != null

        e.presentation.isEnabledAndVisible = isRenamable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!! // update() should prevent this from being null

        // Get the content of the RIGHT-CLICKED tab.
        val content = getContent(e)
        if (content == null) {
            Messages.showErrorDialog(project, LstCrcBundle.message("dialog.error.rename.no.tab.message"), LstCrcBundle.message("dialog.error.rename.title"))
            return
        }

        val branchName = content.getUserData(LstCrcKeys.BRANCH_NAME_KEY)
        if (branchName == null) {
            Messages.showErrorDialog(project, LstCrcBundle.message("dialog.error.rename.no.identifier.message"), LstCrcBundle.message("dialog.error.rename.title"))
            return
        }

        invokeRenameDialog(project, branchName)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    companion object {
        /**
         * Shows a dialog to rename a tab (identified by its branch/revision name).
         * This can be called from different actions. It must be called on the EDT.
         */
        fun invokeRenameDialog(project: Project, branchName: String) {
            val stateService = project.service<ToolWindowStateService>()
            val tabInfo = stateService.state.openTabs.find { it.branchName == branchName }
            val currentDisplayName = tabInfo?.alias ?: branchName

            val newAlias = Messages.showInputDialog(
                project,
                LstCrcBundle.message("dialog.rename.tab.message"),
                LstCrcBundle.message("dialog.rename.tab.title"),
                Messages.getQuestionIcon(),
                currentDisplayName,
                null // No validator
            )

            // newAlias is null if user presses Cancel
            if (newAlias != null) {
                // If user clicks OK with an empty or blank string, we reset the alias by setting it to null.
                val finalAlias = newAlias.trim().ifEmpty { null }
                stateService.updateTabAlias(branchName, finalAlias)
            }
        }
    }
}