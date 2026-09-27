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
