package com.github.uiopak.lstcrc.lineMarkers

package com.github.uiopak.lstcrc.lineMarkers

import com.github.uiopak.lstcrc.services.SelectedBranchService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.tools.util.text.DiffUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.AnnotationsSettings
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.text.DateFormatUtil
import git4idea.GitFileRevision
import git4idea.GitVcs
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import java.util.Date

class BranchCompareLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // This provider uses collectSlowLineMarkers, so getLineMarkerInfo should return null.
        return null
    }

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        lines: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return

        val firstElement = elements.first()
        val project: Project = firstElement.project
        val psiFile: PsiFile = firstElement.containingFile ?: return
        val virtualFile: VirtualFile = psiFile.virtualFile ?: return
        if (!virtualFile.isInLocalFileSystem) return

        val document: Document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return

        val selectedBranchService = project.service<SelectedBranchService>()
        val selectedBranchName = selectedBranchService.getSelectedBranchName(project)
        if (selectedBranchName.isNullOrEmpty()) return

        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repository = repositoryManager.getRepositoryForFile(virtualFile) ?: return

        // 1. Obtain VcsRevisionNumber for the selectedBranchName
        val branchRevisionNumber: VcsRevisionNumber = try {
            GitHistoryUtils.getCurrentRevision(project, repository.root, selectedBranchName) ?: return
        } catch (e: Exception) {
            // Log error or handle
            return
        }

        // 2. Obtain ContentRevision for the virtualFile in the selected branch
        val branchContentRevision: ContentRevision = try {
            // For Git, GitHistoryUtils.getCurrentRevision might not be enough for ContentRevision directly.
            // We need a ContentRevision that can provide content.
            // Let's try to get a GitFileRevision
            val revisions = GitHistoryUtils.history(project, repository.root, virtualFile.path, "$selectedBranchName..$selectedBranchName")
            if (revisions.isNotEmpty() && revisions.first() is GitFileRevision) {
                revisions.first() as GitFileRevision
            } else {
                 // Fallback or alternative way to get ContentRevision if the above fails
                val gitVcs = GitVcs.getInstance(project)
                gitVcs?.vcsHistoryProvider?.createRevision(virtualFile, branchRevisionNumber) ?: return
            }
        } catch (e: Exception) {
            return
        }
        val branchContent = branchContentRevision.content ?: return

        // 3. Get current document content
        val currentContent = document.text

        // 4. Perform the Diff
        val lineFragments = DiffUtil.compareLines(
            branchContent,
            currentContent,
            ComparisonPolicy.DEFAULT,
            EmptyProgressIndicator.INSTANCE
        )

        // 5. Fetch FileAnnotation for the branch version
        val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(virtualFile)
        val annotationProvider = vcs?.annotationProvider
        if (annotationProvider == null) {
            // Log: Cannot get annotation provider for $virtualFile
            return
        }

        // Create VcsFileRevision for annotation
        // branchContentRevision should ideally be a VcsFileRevision.
        // If it's GitFileRevision, it implements VcsFileRevision.
        val vcsFileRevisionForAnnotation: VcsFileRevision = when (branchContentRevision) {
            is VcsFileRevision -> branchContentRevision
            else -> {
                // Manual construction if branchContentRevision is not a VcsFileRevision
                object : VcsFileRevision {
                    override fun getRevisionNumber(): VcsRevisionNumber = branchRevisionNumber
                    override fun getBranchName(): String? = selectedBranchName
                    override fun getRevisionDate(): Date? = null // Not always available easily
                    override fun getAuthor(): String? = null
                    override fun getCommitMessage(): String? = null
                    override fun getContent(): ByteArray = branchContent.toByteArray(virtualFile.charset)
                    override fun getChangedRepositoryPath(): String? = null
                }
            }
        }
        
        val fileAnnotation: FileAnnotation = try {
            // Disable show authors for this annotation to avoid UI popups if settings are different
            val originalAuthorsSetting = AnnotationsSettings.getInstance().SHOW_AUTHORS
            AnnotationsSettings.getInstance().SHOW_AUTHORS = false
            try {
                annotationProvider.annotate(virtualFile, vcsFileRevisionForAnnotation)
            } finally {
                AnnotationsSettings.getInstance().SHOW_AUTHORS = originalAuthorsSetting
            }
        } catch (e: Exception) {
             // Log error during annotation
            return
        }


        val processedLines = mutableSetOf<Int>()

        for (element in elements) {
            val currentDocLineNumber = document.getLineNumber(element.textOffset)
            if (!processedLines.add(currentDocLineNumber)) {
                continue // Already processed this line
            }

            val lineStartOffset = document.getLineStartOffset(currentDocLineNumber)
            // Ensure we pick the first non-whitespace element on the line for the marker
            val firstElementOnLine = psiFile.findElementAt(lineStartOffset)?.let {
                var el = it
                while (el.prevSibling != null && el.prevSibling.textRange.startOffset >= lineStartOffset) {
                    el = el.prevSibling
                }
                el
            } ?: element


            for (fragment in lineFragments) {
                val startLineCurrent = fragment.startLine2
                val endLineCurrent = fragment.endLine2

                if (currentDocLineNumber >= startLineCurrent && currentDocLineNumber < endLineCurrent) {
                    val tooltipText: String
                    // Line exists in current document and is part of a change
                    if (fragment.startLine1 != -1) { // Line was MODIFIED (exists in branch)
                        val branchDocLineNumber = fragment.startLine1 + (currentDocLineNumber - fragment.startLine2)
                        
                        if (branchDocLineNumber >= 0 && branchDocLineNumber < fileAnnotation.lineCount) {
                            val revisionInfo = fileAnnotation.getLineRevisionNumber(branchDocLineNumber)
                            val author = fileAnnotation.getAuthorsForLine(branchDocLineNumber)?.firstOrNull()
                            val date = revisionInfo?.timestamp?.let { Date(it) }
                            // Note: fileAnnotation.getToolTip(branchDocLineNumber) might give a pre-formatted string
                            // Or fileAnnotation.getLineCommitMessage(branchDocLineNumber) if available
                            // For more details, one might need to fetch VcsFullCommitDetails using revisionInfo
                            // This is a simplified version:
                            val commitMessage = fileAnnotation.getCommitMessage(branchDocLineNumber) ?: "N/A"


                            tooltipText = "Branch: $selectedBranchName\n" +
                                    (author?.let { "Author: $it\n" } ?: "") +
                                    (date?.let { "Date: ${DateFormatUtil.formatPrettyDateTime(it)}\n" } ?: "") +
                                    "Commit: ${revisionInfo?.asString() ?: "N/A"}\n" +
                                    "Message: ${commitMessage.lines().firstOrNull() ?: ""}" // First line of commit message
                        } else {
                            tooltipText = "Branch: $selectedBranchName\nError: Line $branchDocLineNumber out of bounds in branch annotation."
                        }

                    } else { // Line was INSERTED in current document (not in branch)
                        tooltipText = "Line added compared to branch '$selectedBranchName'"
                    }

                    val renderer = BranchCompareGutterIconRenderer(tooltipText)
                    lines.add(
                        LineMarkerInfo(
                            firstElementOnLine, // Attach marker to the first element on the line
                            firstElementOnLine.textRange,
                            renderer.icon, // Use icon from renderer
                            null, // Pass.LINE_MARKERS is implicit for collectSlowLineMarkers
                            { renderer.tooltipText }, // Tooltip provider
                            null, // No specific navigation handler
                            renderer.alignment,
                            { "BranchCompareMarker" }
                        )
                    )
                    break // Found the fragment for this line, move to next element/line
                }
            }
        }
    }
}
