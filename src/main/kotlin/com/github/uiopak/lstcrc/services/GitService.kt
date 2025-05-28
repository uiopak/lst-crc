package com.github.uiopak.lstcrc.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.components.Service // Keep this, it's used
import com.intellij.openapi.diagnostic.thisLogger // Keep this, it's used
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
// FilePath import was removed as it's no longer needed
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
// ChangeListManager import is removed as getLocalChangesAgainstHEAD() is removed
// Keep other Git-related imports
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import java.util.concurrent.CompletableFuture
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
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
                    // Using the 4-argument overload: getDiffWithWorkingTree(Project, VirtualFile, String, boolean)
                    // This assumes the method returns a non-nullable Collection<Change> or List<Change>.
                    val changes = GitChangeUtils.getDiffWithWorkingTree(project, repository.root, branchNameToCompare, true).toList()
                    future.complete(changes)
                } catch (e: VcsException) {
                    logger.error("Error getting diff with working tree: ${e.message}", e)
                    future.completeExceptionally(e)
                }
            }
        }.queue()

        return future
    }
}