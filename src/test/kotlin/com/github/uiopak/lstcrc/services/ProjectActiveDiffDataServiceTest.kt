package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.testsupport.categorizedChanges
import com.github.uiopak.lstcrc.testsupport.flushEdt
import com.github.uiopak.lstcrc.testsupport.selectComparisonTab
import com.github.uiopak.lstcrc.testsupport.selectHeadTab
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import java.util.concurrent.TimeUnit

class ProjectActiveDiffDataServiceTest : LstCrcTestCase() {

    fun testAcceptsHeadUpdateWhenHeadTabIsSelected() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val headFile = myFixture.addFileToProject("diff/HeadOnly.txt", "head\n").virtualFile

        selectHeadTab(project)

        diffDataService.updateActiveDiff(
            "HEAD",
            categorizedChanges(createdFiles = listOf(headFile))
        )
        flushEdt()

        assertEquals("HEAD", diffDataService.activeBranchName)
        assertTrue(diffDataService.createdFilePaths.contains(headFile.path))
        assertTrue(diffDataService.changedFilePaths.contains(headFile.path))
        assertTrue(diffDataService.createdFilePaths.contains(headFile.path))
    }

    fun testRejectsStaleUpdateWhenSelectedBranchDoesNotMatch() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val selectedFile = myFixture.addFileToProject("diff/Selected.txt", "selected\n").virtualFile
        val staleFile = myFixture.addFileToProject("diff/Stale.txt", "stale\n").virtualFile

        selectComparisonTab(project, "selected-branch")

        diffDataService.updateActiveDiff(
            "selected-branch",
            categorizedChanges(createdFiles = listOf(selectedFile))
        )
        flushEdt()

        diffDataService.updateActiveDiff(
            "other-branch",
            categorizedChanges(createdFiles = listOf(staleFile))
        )
        flushEdt()

        assertEquals("selected-branch", diffDataService.activeBranchName)
        assertEquals(listOf(selectedFile), diffDataService.createdFiles)
        assertTrue(diffDataService.createdFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.createdFilePaths.contains(staleFile.path))
        assertTrue(diffDataService.changedFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.changedFilePaths.contains(staleFile.path))
        assertTrue(diffDataService.createdFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.createdFilePaths.contains(staleFile.path))
    }

    // A result sent from a background thread is applied on a later EDT turn; the tab may be switched before that.
    fun testRejectsUpdateWhenTheTabIsSwitchedBeforeItIsApplied() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val staleFile = myFixture.addFileToProject("diff/SwitchedAway.txt", "stale\n").virtualFile

        selectComparisonTab(project, "first-branch")
        diffDataService.updateActiveDiff("first-branch", categorizedChanges())
        flushEdt()

        // Sent while "first-branch" is still selected, applied only when this EDT turn ends.
        ApplicationManager.getApplication().executeOnPooledThread {
            diffDataService.updateActiveDiff("first-branch", categorizedChanges(createdFiles = listOf(staleFile)))
        }.get(10, TimeUnit.SECONDS)
        selectComparisonTab(project, "second-branch")
        flushEdt()

        assertFalse(diffDataService.createdFilePaths.contains(staleFile.path))
        selectHeadTab(project)
    }

    fun testRejectsUpdateWhenRepositoryTargetChangesBeforeItIsApplied() {
        val diffData = project.service<ProjectActiveDiffDataService>()
        val state = project.service<ToolWindowStateService>()
        val staleFile = myFixture.addFileToProject("diff/OldTarget.txt", "stale\n").virtualFile
        val root = project.basePath!!
        selectComparisonTab(project, "feature")
        state.updateTabRepoComparison("feature", root, "old-target", triggerRefresh = false)
        diffData.updateActiveDiff("feature", categorizedChanges().copy(comparisonContext = mapOf(root to "old-target")))

        var notifications = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC,
            com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener { notifications++ }
        )
        ApplicationManager.getApplication().executeOnPooledThread {
            diffData.updateActiveDiff(
                "feature",
                categorizedChanges(createdFiles = listOf(staleFile)).copy(comparisonContext = mapOf(root to "old-target"))
            )
        }.get(10, TimeUnit.SECONDS)
        state.updateTabRepoComparison("feature", root, "new-target", triggerRefresh = false)
        flushEdt()

        assertFalse("A result for the old repository target must be rejected", diffData.createdFilePaths.contains(staleFile.path))
        assertEquals(0, notifications)

        diffData.updateActiveDiff(
            "feature",
            categorizedChanges(createdFiles = listOf(staleFile)).copy(comparisonContext = mapOf(root to "new-target"))
        )
        flushEdt()
        assertEquals(1, notifications)
        assertEquals(mapOf(root to "new-target"), diffData.activeComparisonContext)
        selectHeadTab(project)
    }

    fun testRejectsHeadUpdateWhileComparisonTabIsSelected() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val selectedFile = myFixture.addFileToProject("diff/BranchSelected.txt", "branch selected\n").virtualFile
        val headFile = myFixture.addFileToProject("diff/HeadShouldBeRejected.txt", "head\n").virtualFile

        selectComparisonTab(project, "selected-branch")

        diffDataService.updateActiveDiff(
            "selected-branch",
            categorizedChanges(createdFiles = listOf(selectedFile))
        )
        flushEdt()

        diffDataService.updateActiveDiff(
            "HEAD",
            categorizedChanges(createdFiles = listOf(headFile))
        )
        flushEdt()

        assertEquals("selected-branch", diffDataService.activeBranchName)
        assertEquals(listOf(selectedFile), diffDataService.createdFiles)
        assertTrue(diffDataService.createdFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.createdFilePaths.contains(headFile.path))
        assertTrue(diffDataService.changedFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.changedFilePaths.contains(headFile.path))
        assertTrue(diffDataService.createdFilePaths.contains(selectedFile.path))
        assertFalse(diffDataService.createdFilePaths.contains(headFile.path))
    }

    fun testUpdateActiveDiffWithIdenticalSnapshotBypassesNotification() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val file = myFixture.addFileToProject("diff/Same.txt", "same\n").virtualFile
        selectHeadTab(project)

        diffDataService.updateActiveDiff(
            "HEAD",
            categorizedChanges(createdFiles = listOf(file))
        )
        flushEdt()

        var notifications = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC,
            com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener { notifications++ }
        )

        diffDataService.updateActiveDiff(
            "HEAD",
            categorizedChanges(createdFiles = listOf(file))
        )
        flushEdt()

        assertEquals(0, notifications)

        // Now update with different file
        val otherFile = myFixture.addFileToProject("diff/Other.txt", "other\n").virtualFile
        diffDataService.updateActiveDiff(
            "HEAD",
            categorizedChanges(createdFiles = listOf(otherFile))
        )
        flushEdt()

        assertEquals(1, notifications)
    }

    fun testSamePathsWithNewUnsavedContentPublishesNewChanges() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val file = myFixture.addFileToProject("diff/Edited.txt", "base\n").virtualFile
        val filePath = com.intellij.vcsUtil.VcsUtil.getFilePath(file)
        fun unsavedEdit(text: String) = CategorizedChanges.EMPTY.copy(
            allChanges = listOf(
                com.intellij.openapi.vcs.changes.Change(
                    TextContentRevision(filePath, "base\n", git4idea.GitRevisionNumber("HEAD")),
                    TextContentRevision(filePath, text, git4idea.GitRevisionNumber("LOCAL")),
                    com.intellij.openapi.vcs.FileStatus.MODIFIED
                )
            ),
            modifiedFiles = listOf(file)
        )
        selectHeadTab(project)
        diffDataService.updateActiveDiff("HEAD", unsavedEdit("one\n"))
        flushEdt()

        var notifications = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC,
            com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener { notifications++ }
        )

        diffDataService.updateActiveDiff("HEAD", unsavedEdit("one\n"))
        flushEdt()
        assertEquals(0, notifications)

        diffDataService.updateActiveDiff("HEAD", unsavedEdit("two\n"))
        flushEdt()
        assertEquals(1, notifications)
        assertEquals("two\n", diffDataService.categorizedChanges!!.allChanges.single().afterRevision!!.content)
    }

    // Every pause in typing replaced the snapshot (the unsaved text changed) and reset every file status in the
    // project, although no file joined or left a scope. File statuses (scopes, file colours) only depend on paths.
    fun testNewUnsavedContentOfTheSameFilesKeepsFileStatuses() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val file = myFixture.addFileToProject("diff/Typed.txt", "base\n").virtualFile
        val filePath = com.intellij.vcsUtil.VcsUtil.getFilePath(file)
        fun unsavedEdit(text: String) = CategorizedChanges.EMPTY.copy(
            allChanges = listOf(
                com.intellij.openapi.vcs.changes.Change(
                    TextContentRevision(filePath, "base\n", git4idea.GitRevisionNumber("HEAD")),
                    TextContentRevision(filePath, text, git4idea.GitRevisionNumber("LOCAL")),
                    com.intellij.openapi.vcs.FileStatus.MODIFIED
                )
            ),
            modifiedFiles = listOf(file)
        )
        selectComparisonTab(project, "feature")
        diffDataService.updateActiveDiff("feature", unsavedEdit("one\n"))
        flushEdt()

        var fileStatusResets = 0
        com.intellij.openapi.vcs.FileStatusManager.getInstance(project).addFileStatusListener(
            object : com.intellij.openapi.vcs.FileStatusListener {
                override fun fileStatusesChanged() {
                    fileStatusResets++
                }
            },
            testRootDisposable
        )

        diffDataService.updateActiveDiff("feature", unsavedEdit("two\n"))
        flushEdt()
        assertEquals("New content of the same files", 0, fileStatusResets)

        val otherFile = myFixture.addFileToProject("diff/Created.txt", "new\n").virtualFile
        diffDataService.updateActiveDiff("feature", unsavedEdit("three\n").copy(createdFiles = listOf(otherFile)))
        flushEdt()
        assertEquals("A file joined a scope", 1, fileStatusResets)
        selectHeadTab(project)
    }

    fun testSwitchingComparisonTabsWithSameScopesKeepsFileStatuses() {
        val diffData = project.service<ProjectActiveDiffDataService>()
        val file = myFixture.addFileToProject("diff/Same.txt", "text\n").virtualFile
        val changes = categorizedChanges(modifiedFiles = listOf(file))
        selectComparisonTab(project, "first-base")
        diffData.updateActiveDiff("first-base", changes)
        flushEdt()

        var fileStatusResets = 0
        var diffUpdates = 0
        com.intellij.openapi.vcs.FileStatusManager.getInstance(project).addFileStatusListener(
            object : com.intellij.openapi.vcs.FileStatusListener {
                override fun fileStatusesChanged() { fileStatusResets++ }
            }, testRootDisposable
        )
        project.messageBus.connect(testRootDisposable).subscribe(
            com.github.uiopak.lstcrc.messaging.DIFF_DATA_CHANGED_TOPIC,
            com.github.uiopak.lstcrc.messaging.ActiveDiffDataChangedListener { diffUpdates++ }
        )

        selectComparisonTab(project, "second-base")
        diffData.updateActiveDiff("second-base", changes)
        flushEdt()

        assertEquals("Unchanged scope membership must not reload every native tracker", 0, fileStatusResets)
        assertEquals("The new comparison must still reach the browser and gutters", 1, diffUpdates)
        assertEquals("second-base", diffData.activeBranchName)

        diffData.updateActiveDiff("second-base", categorizedChanges(createdFiles = listOf(file)))
        flushEdt()
        assertEquals("Reclassifying a file must still refresh its status", 1, fileStatusResets)
    }

    fun testHeadSwitchOnlyResetsFileStatusesWhenScopeMembershipChanges() {
        val diffData = project.service<ProjectActiveDiffDataService>()
        val settings = ApplicationManager.getApplication().service<com.github.uiopak.lstcrc.toolWindow.LstCrcSettingsService>()
        val definition = com.github.uiopak.lstcrc.toolWindow.LstCrcSettingDefinitions.INCLUDE_HEAD_IN_SCOPES
        val original = settings[definition]
        val file = myFixture.addFileToProject("diff/HeadMembership.txt", "text\n").virtualFile
        val changes = categorizedChanges(modifiedFiles = listOf(file))
        var fileStatusResets = 0
        com.intellij.openapi.vcs.FileStatusManager.getInstance(project).addFileStatusListener(
            object : com.intellij.openapi.vcs.FileStatusListener {
                override fun fileStatusesChanged() { fileStatusResets++ }
            }, testRootDisposable
        )

        try {
            listOf(false, true).forEach { includeHead ->
                settings[definition] = includeHead
                selectComparisonTab(project, "feature-base")
                diffData.updateActiveDiff("feature-base", changes)
                flushEdt()
                fileStatusResets = 0

                selectHeadTab(project)
                diffData.updateActiveDiff("HEAD", changes)
                flushEdt()
                val expectedResets = if (includeHead) 0 else 1
                assertEquals("Switch to HEAD with Include HEAD=$includeHead", expectedResets, fileStatusResets)

                fileStatusResets = 0
                selectComparisonTab(project, "feature-base")
                diffData.updateActiveDiff("feature-base", changes)
                flushEdt()
                assertEquals("Switch from HEAD with Include HEAD=$includeHead", expectedResets, fileStatusResets)
            }
        } finally {
            settings[definition] = original
            selectHeadTab(project)
        }
    }

    // Regression (round five): the gutter of a moved file loaded the target content by its new path, which the
    // target doesn't have, so every line showed as added.
    fun testMovedFileIsLookedUpByItsOldPathInTheTarget() {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        val root = project.basePath!!
        val moved = com.intellij.openapi.vcs.changes.Change(
            revision("$root/Old.txt"),
            revision("$root/New.txt"),
            com.intellij.openapi.vcs.FileStatus.MODIFIED
        )

        selectComparisonTab(project, "feature")
        diffDataService.updateActiveDiff("feature", categorizedChanges().copy(allChanges = listOf(moved)))
        flushEdt()

        assertEquals("$root/Old.txt", diffDataService.pathInTarget("$root/New.txt"))
        assertEquals("$root/Other.txt", diffDataService.pathInTarget("$root/Other.txt"))
    }

    private fun revision(path: String) = TextContentRevision(
        com.intellij.vcsUtil.VcsUtil.getFilePath(path, false),
        "text\n",
        git4idea.GitRevisionNumber("feature")
    )
}
