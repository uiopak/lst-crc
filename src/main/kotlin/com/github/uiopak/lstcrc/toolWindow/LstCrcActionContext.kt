package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.state.TabInfo
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

internal fun singleSelectedCommit(event: AnActionEvent): CommitId? {
    return event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        ?.commits
    ?.singleOrNull()
}

internal fun hasSingleSelectedCommit(event: AnActionEvent): Boolean {
    return event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
        ?.commits
        ?.size == 1
}