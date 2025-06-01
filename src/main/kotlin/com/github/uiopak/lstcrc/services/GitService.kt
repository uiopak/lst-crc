package com.github.uiopak.lstcrc.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import git4idea.changes.GitChangeUtils
import java.util.concurrent.CompletableFuture
import com.intellij.openapi.vfs.LocalFileSystem // Added import
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    private val logger = thisLogger()

    internal fun getCurrentRepository(): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories
        return when {
            repositories.isEmpty() -> {
                logger.warn("No Git repositories found in the project.")
                null
            }
            repositories.size > 1 -> {
                logger.info("Multiple Git repositories found. Using the first one: ${repositories.first().root.path}")
                repositories.first() // Or implement logic to select one
            }
            else -> repositories.first()
        }
    }

    fun getLocalBranches(): List<String> {
        val repository = getCurrentRepository() ?: return emptyList()
        return repository.branches.localBranches.map { it.name }
    }

    fun getRemoteBranches(): List<String> {
        val repository = getCurrentRepository() ?: return emptyList()
        return repository.branches.remoteBranches.map { it.name }
    }

    fun getAllBranches(): List<String> {
        val repository = getCurrentRepository() ?: return emptyList()
        return (repository.branches.localBranches + repository.branches.remoteBranches)
            .map { it.name }
            .distinct()
            .sorted() // Optional: sort for better UX
    }

    fun getCurrentBranch(): String? {
        val repository = getCurrentRepository() ?: return null
        return repository.currentBranchName
    }

    /**
     * Gets the changes between the current HEAD and the specified branch name.
     * These changes represent what is in `branchNameToCompare` that is different from HEAD.
     * - Files added in `branchNameToCompare` (not in HEAD) will be `Change.Type.NEW`.
     * - Files deleted in `branchNameToCompare` (present in HEAD) will be `Change.Type.DELETED`.
     * - Files modified will be `Change.Type.MODIFICATION`.
     */
    fun getChanges(branchNameToCompare: String): CompletableFuture<List<Change>> {
        logger.warn("DIAGNOSTIC: GitService.getChanges called with branchNameToCompare: $branchNameToCompare")
        val future = CompletableFuture<List<Change>>()
        val repository = getCurrentRepository()

        if (repository == null) {
            future.complete(emptyList())
            return future
        }

        // Determine current actual branch name
        val currentActualBranchName = repository.currentBranchName

        object : Task.Backgroundable(project, "Loading Git Changes...") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val changes: List<Change>
                    // If target is current branch or HEAD, prioritize ChangeListManager for live local changes
                    if (branchNameToCompare == currentActualBranchName ||
                        (branchNameToCompare == "HEAD" && currentActualBranchName != null /* i.e., not detached HEAD */)) {
                        logger.warn("DIAGNOSTIC: GitService.getChanges - Using ChangeListManager for target: $branchNameToCompare (current actual: $currentActualBranchName)")
                        changes = ChangeListManager.getInstance(project).allChanges.toList()
                        logger.warn("DIAGNOSTIC: GitService.getChanges - ChangeListManager found ${changes.size} changes.")
                    } else {
                        // Otherwise, compare working tree against the specified branch/commit
                        // This shows "current work vs. other branch/commit"
                        logger.warn("DIAGNOSTIC: GitService.getChanges - Using GitChangeUtils.getDiffWithWorkingTree for target: $branchNameToCompare")
                        changes = GitChangeUtils.getDiffWithWorkingTree(repository, branchNameToCompare, true)?.toList() ?: emptyList()
                        logger.warn("DIAGNOSTIC: GitService.getChanges - GitChangeUtils.getDiffWithWorkingTree found ${changes.size} changes for target $branchNameToCompare.")
                    }
                    future.complete(changes)
                } catch (e: VcsException) {
                    logger.error("Error getting changes for $branchNameToCompare: ${e.message}", e)
                    future.completeExceptionally(e)
                } catch (e: Exception) { // Catch other potential exceptions
                    logger.error("Unexpected error getting changes for $branchNameToCompare: ${e.message}", e)
                    future.completeExceptionally(e)
                }
            }
        }.queue()

        return future
    }

    fun calculateEditorTabColor(filePath: String, comparisonBranch: String): String {
        logger.info("Calculating editor tab color for file: $filePath against branch: $comparisonBranch")

        val repository = getCurrentRepository()
        if (repository == null) {
            logger.warn("No Git repository found.")
            return ""
        }

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
        // Note: virtualFile can be null if the file is deleted in the working tree.

        val actualComparisonBranch = if (comparisonBranch.equals("current_branch", ignoreCase = true)) {
            repository.currentBranchName ?: "HEAD"
        } else {
            comparisonBranch
        }
        logger.info("Effective comparison target: $actualComparisonBranch")

        // Logic for "HEAD" or current branch (uses ChangeListManager for working tree changes)
        if (actualComparisonBranch.equals("HEAD", ignoreCase = true) || actualComparisonBranch == repository.currentBranchName) {
            logger.info("Comparing with HEAD or current branch. Using ChangeListManager for local changes.")
            if (virtualFile == null) {
                // If file doesn't exist in working tree, it might have been deleted.
                // ChangeListManager might not report it directly unless deletion is staged.
                // To be accurate for "deleted from working tree compared to HEAD", a diff is needed.
                // However, the original logic for virtualFile == null returned "", let's refine this.
                // Check if it existed in HEAD (more complex, deferring to GitChangeUtils below for non-HEAD for now)
                logger.warn("File $filePath not found in working tree. Assuming deleted if it was in HEAD.")
                // This case is tricky without doing a full diff to see if it *was* in HEAD.
                // For now, let's assume it would be covered by a diff if comparisonBranch was specific.
                // If we are comparing to HEAD, and file is null, it is deleted from WT.
                // We need to check if it existed in HEAD. A simple way:
                try {
                    val contentInHead = GitChangeUtils.resolveReference(repository, "HEAD", filePath)
                    if (contentInHead != null) {
                         logger.info("File $filePath is DELETED from working tree (was in HEAD).")
                         return "#FF0000" // Red
                    } else {
                         logger.info("File $filePath not in working tree and not in HEAD (likely path error or never existed).")
                         return "" // Not in HEAD, not in WT.
                    }
                } catch (e: VcsException) {
                    logger.warn("Could not resolve $filePath in HEAD: ${e.message}")
                    return "" // Error condition
                }
            }

            val changeListManager = ChangeListManager.getInstance(project)
            val change = changeListManager.getChange(virtualFile) // virtualFile is not null here

            if (change == null) {
                return if (changeListManager.isUnversioned(virtualFile)) {
                    logger.info("File $filePath is UNVERSIONED.")
                    "#00FF00" // Green
                } else {
                    logger.info("File $filePath is versioned and unchanged from HEAD.")
                    "" // No change
                }
            }

            logger.info("Change type for $filePath (vs HEAD): ${change.type}")
            return when (change.type) {
                Change.Type.NEW -> "#00FF00" // Green (typically means staged new file)
                Change.Type.MODIFICATION -> "#0000FF" // Blue
                Change.Type.DELETED -> "#FF0000" // Red (staged deletion)
                else -> "" // Other types like MOVED
            }
        } else {
            // Logic for comparing with a specific, different branch
            logger.info("Comparing working tree with specific branch: $actualComparisonBranch")
            if (virtualFile == null) {
                // File is not in the working tree. Is it in actualComparisonBranch?
                // If yes, it's "deleted from working tree compared to actualComparisonBranch".
                try {
                    val contentInTargetBranch = GitChangeUtils.resolveReference(repository, actualComparisonBranch, filePath)
                    if (contentInTargetBranch != null) {
                        logger.info("File $filePath is DELETED from working tree (exists in $actualComparisonBranch).")
                        return "#FF0000" // Red
                    } else {
                        logger.info("File $filePath not in working tree and not in $actualComparisonBranch.")
                        return "" // Not in target branch, not in WT.
                    }
                } catch (e: VcsException) {
                    logger.warn("Could not resolve $filePath in $actualComparisonBranch: ${e.message}")
                    return "" // Error condition
                }
            }

            // File exists in working tree. Compare it with actualComparisonBranch.
            // We need to find the specific change for `virtualFile` within the diff.
            val changesAgainstBranch: List<Change> = try {
                GitChangeUtils.getDiffWithWorkingTree(repository, actualComparisonBranch, true) ?: emptyList()
            } catch (e: VcsException) {
                logger.error("Error diffing with $actualComparisonBranch: ${e.message}", e)
                return "" // Error
            }

            val changeForFile = changesAgainstBranch.find {
                it.afterRevision?.file == virtualFile || it.beforeRevision?.file == virtualFile
            }

            if (changeForFile == null) {
                // No change means the file in the working tree is identical to the one in actualComparisonBranch.
                // OR, the file is in the working tree but not tracked and also not in actualComparisonBranch (unversioned locally, and not in target branch).
                // To be sure it's not "new" relative to the target branch, we'd need to know if it's tracked locally.
                // For simplicity, if no specific diff entry, assume it's "unchanged" relative to that branch,
                // or it's a new local file not present in the target branch (which getDiffWithWorkingTree should show as NEW).
                // This case needs careful thought: if a file exists locally but is untracked, getDiffWithWorkingTree for another branch might not list it.
                // Let's assume if it's not in changes, it's considered "same" or "new untracked not related to branch history".
                // A file present in WT but not in the target branch *should* appear as Type.NEW.
                // If it's not in the list, it implies it's identical in WT and target branch OR it's untracked and not relevant to target branch.
                logger.info("No specific diff found for $filePath against $actualComparisonBranch. Assuming unchanged or untracked new.")
                // If it's untracked locally, it's new.
                if (ChangeListManager.getInstance(project).isUnversioned(virtualFile)) {
                     logger.info("File $filePath is UNVERSIONED locally (and no diff to $actualComparisonBranch, implies not in target branch).")
                     return "#00FF00" // Green
                }
                logger.info("File $filePath is versioned locally and no diff to $actualComparisonBranch (implies same as in target branch).")
                return "" // No change
            }

            logger.info("Change type for $filePath (vs $actualComparisonBranch): ${changeForFile.type}")
            return when (changeForFile.type) {
                Change.Type.NEW -> "#00FF00" // Green (exists in WT, not in comparisonBranch)
                Change.Type.MODIFICATION -> "#0000FF" // Blue
                Change.Type.DELETED -> "#FF0000" // Red (exists in comparisonBranch, not in WT - this case handled by virtualFile == null check above)
                else -> ""
            }
        }
    }
}
