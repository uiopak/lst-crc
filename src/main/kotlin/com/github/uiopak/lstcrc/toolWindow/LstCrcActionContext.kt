package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogDataKeys

internal fun selectedLstCrcTab(project: Project): TabInfo? {
    return project.service<ToolWindowStateService>().getSelectedTabInfo()
}

internal fun singleSelectedRevisionString(event: AnActionEvent): String? {
    return event.getData(VcsDataKeys.VCS_REVISION_NUMBERS)
        ?.singleOrNull()
        ?.asString()
}

internal fun singleSelectedCommit(event: AnActionEvent): CommitId? =
    event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)?.commits?.singleOrNull()

/**
 * A popup item that shows [text] exactly as given. Plain action texts treat '_' as a mnemonic marker and drop it, so
 * branch, tab and repository names such as `feature_login` must not be passed as ordinary action text.
 */
internal fun plainTextAction(text: String, perform: () -> Unit): AnAction = object : AnAction() {
    init {
        templatePresentation.setText(text, false)
    }

    override fun actionPerformed(e: AnActionEvent) = perform()
}
