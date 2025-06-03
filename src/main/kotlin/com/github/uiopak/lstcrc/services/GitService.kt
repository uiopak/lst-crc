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
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

data class CategorizedChanges(
    val allChanges: List<Change>,
    val createdFiles: List<VirtualFile>,
    val modifiedFiles: List<VirtualFile>,
    val movedFiles: List<VirtualFile>
)

@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    private val logger = thisLogger()

    internal fun getCurrentRepository(): GitRepository? {
        logger.info("GIT_REPO_DETECT: getCurrentRepository() called.")

        val repositoryManager = GitRepositoryManager.getInstance(project)
        logger.info("GIT_REPO_DETECT: GitRepositoryManager instance: $repositoryManager")

        val repositories = repositoryManager.repositories
        logger.info("GIT_REPO_DETECT: Found ${repositories.size} repositories by GitRepositoryManager.")

        repositories.forEachIndexed { index, repo ->
            logger.info("GIT_REPO_DETECT: Repo $index: root=${repo.root.path}, presentableUrl=${repo.root.presentableUrl}, state=${repo.state}")
        }

        return when {
            repositories.isEmpty() -> {
                logger.warn("GIT_REPO_DETECT: No Git repositories found by manager. Returning null.")
                // Attempt to get project base path as a fallback clue, though not a repo itself
                val projectBasePath = project.basePath
                logger.warn("GIT_REPO_DETECT: Project base path for context: $projectBasePath")
                null
            }
            repositories.size > 1 -> {
                logger.info("GIT_REPO_DETECT: Multiple Git repositories found (${repositories.size}). Using the first one: ${repositories.first().root.path}")
                // Log all available repository roots for diagnostic purposes
                repositories.forEach { logger.info("GIT_REPO_DETECT: Available repo root: ${it.root.path}") }
                repositories.first()
            }
            else -> { // Exactly one repository
                logger.info("GIT_REPO_DETECT: Exactly one Git repository found: ${repositories.first().root.path}")
                repositories.first()
            }
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
    fun getChanges(branchNameToCompare: String): CompletableFuture<CategorizedChanges> {
        logger.warn("DIAGNOSTIC: GitService.getChanges called with branchNameToCompare: $branchNameToCompare")
        val future = CompletableFuture<CategorizedChanges>()
        val repository = getCurrentRepository()

        if (repository == null) {
            future.complete(CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList()))
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
                        logger.warn("DIAGNOSTIC: GitService.getChanges - ChangeListManager found ${changes.size} total changes.")
                    } else {
                        // Otherwise, compare working tree against the specified branch/commit
                        // This shows "current work vs. other branch/commit"
                        logger.warn("DIAGNOSTIC: GitService.getChanges - Using GitChangeUtils.getDiffWithWorkingTree for target: $branchNameToCompare")
                        changes = GitChangeUtils.getDiffWithWorkingTree(repository, branchNameToCompare, true)?.toList() ?: emptyList()
                        logger.warn("DIAGNOSTIC: GitService.getChanges - GitChangeUtils.getDiffWithWorkingTree found ${changes.size} total changes for target $branchNameToCompare.")
                    }

                    val createdFiles = mutableListOf<VirtualFile>()
                    val modifiedFiles = mutableListOf<VirtualFile>()
                    val movedFiles = mutableListOf<VirtualFile>()

                    for (change in changes) {
                        when (change.type) {
                            Change.Type.NEW -> {
                                change.afterRevision?.file?.virtualFile?.let { createdFiles.add(it) }
                            }
                            Change.Type.MODIFICATION -> {
                                change.afterRevision?.file?.virtualFile?.let { modifiedFiles.add(it) }
                            }
                            Change.Type.MOVED -> {
                                change.afterRevision?.file?.virtualFile?.let { movedFiles.add(it) }
                            }
                            else -> {
                                // Handle other types like DELETED if necessary, though for tab coloring
                                // we are mostly interested in files that currently exist or are new/modified/moved.
                            }
                        }
                    }
                    val categorizedChanges = CategorizedChanges(changes, createdFiles, modifiedFiles, movedFiles)
                    future.complete(categorizedChanges)
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
