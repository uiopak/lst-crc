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
}
