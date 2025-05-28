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
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
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
        val future = CompletableFuture<List<Change>>()
        val repository = getCurrentRepository()

        if (repository == null) {
            future.complete(emptyList())
            return future
        }

        object : Task.Backgroundable(project, "Loading Git Changes...") {
            override fun run(indicator: ProgressIndicator) {
                logger.info("Getting changes in working tree compared to branch: $branchNameToCompare for repository ${repository.root.path}")
                try {
                    // Compare the specified branch with the current working tree state.
                    // The 'null' argument for filePaths means all files. 'true' is for detectMoves.
                    val changes = GitChangeUtils.getDiffWithWorkingTree(project, repository.root, branchNameToCompare, null, true)?.toList() ?: emptyList()
                    future.complete(changes)
                } catch (e: VcsException) {
                    logger.error("Error getting diff with working tree: ${e.message}", e)
                    future.completeExceptionally(e)
                }
            }
        }.queue()

        return future
    }

    fun getLocalChangesAgainstHEAD(): CompletableFuture<List<Change>> {
        val future = CompletableFuture<List<Change>>()

        object : Task.Backgroundable(project, "Fetching Local Changes...", true /* canBeCancelled */) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Accessing changelist manager..." // Optional progress text
                    val changeListManager = ChangeListManager.getInstance(project)
                    val localChanges = changeListManager.allChanges.toList()
                    future.complete(localChanges)
                } catch (e: Exception) {
                    logger.error("Error fetching local changes", e)
                    future.completeExceptionally(e)
                }
            }

            override fun onCancel() {
                logger.info("Fetching local changes was cancelled.")
                future.cancel(true)
            }
        }.queue()

        return future
    }
}