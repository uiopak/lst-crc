package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.changes.GitChangeUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import java.util.concurrent.CompletableFuture

/**
 * Holds the result of a Git diff, with files categorized by their change type.
 *
 * @param allChanges The raw list of [Change] objects from the VCS API.
 * @param createdFiles A list of files considered new in the comparison.
 * @param modifiedFiles A list of files with content modifications.
 * @param movedFiles A list of files that were moved or renamed.
 */
data class CategorizedChanges(
    val allChanges: List<Change>,
    val createdFiles: List<VirtualFile>,
    val modifiedFiles: List<VirtualFile>,
    val movedFiles: List<VirtualFile>
)

/**
 * A project-level service responsible for all interactions with the Git4Idea plugin API.
 * It provides asynchronous methods to fetch branches, calculate diffs, and retrieve file content
 * from specific revisions.
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    private val logger = thisLogger()

    internal fun getCurrentRepository(): GitRepository? {
        logger.debug("getCurrentRepository() called.")

        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories
        logger.debug("Found ${repositories.size} repositories by GitRepositoryManager.")

        return when (repositories.size) {
            0 -> {
                logger.info("No Git repositories found by manager. Returning null.")
                null
            }
            1 -> {
                logger.info("Exactly one Git repository found: ${repositories.first().root.path}")
                repositories.first()
            }
            else -> {
                logger.warn("Multiple Git repositories found (${repositories.size}). Using the first one: ${repositories.first().root.path}")
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

    /**
     * Gets the changes between the current working state and the specified branch name.
     * These changes represent what is in `branchNameToCompare` that is different from the working state.
     * - Files added in `branchNameToCompare` (not in HEAD) will be `Change.Type.NEW`.
     * - Files deleted in `branchNameToCompare` (present in HEAD) will be `Change.Type.DELETED`.
     * - Files modified will be `Change.Type.MODIFICATION`.
     */
    fun getChanges(branchNameToCompare: String): CompletableFuture<CategorizedChanges> {
        logger.debug("getChanges called with branchNameToCompare: $branchNameToCompare")
        val future = CompletableFuture<CategorizedChanges>()
        val repository = getCurrentRepository()

        if (repository == null) {
            future.complete(CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList()))
            return future
        }

        val currentActualBranchName = repository.currentBranchName

        object : Task.Backgroundable(project, LstCrcBundle.message("git.task.loading.changes")) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val changes: List<Change>
                    // For the current branch or HEAD, ChangeListManager provides the most accurate view of local changes.
                    if (branchNameToCompare == currentActualBranchName ||
                        (branchNameToCompare == "HEAD" && currentActualBranchName != null /* i.e., not in detached HEAD state */)) {
                        logger.debug("Using ChangeListManager for target: $branchNameToCompare (current actual: $currentActualBranchName)")
                        changes = ChangeListManager.getInstance(project).allChanges.toList()
                        logger.debug("ChangeListManager found ${changes.size} total changes.")
                    } else {
                        // For other branches, diff the working tree against the specified branch to show "current work vs. other branch".
                        logger.debug("Using GitChangeUtils.getDiffWithWorkingTree for target: $branchNameToCompare")
                        changes = GitChangeUtils.getDiffWithWorkingTree(repository, branchNameToCompare, true)?.toList() ?: emptyList()
                        logger.debug("GitChangeUtils.getDiffWithWorkingTree found ${changes.size} total changes for target $branchNameToCompare.")
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
                            Change.Type.DELETED -> {
                                // DELETED changes are included in `allChanges` but are not needed for coloring/gutter marks
                                // of existing files, as the file is gone.
                            }
                            else -> {
                                // Other change types (e.g., UNVERSIONED) are ignored.
                            }
                        }
                    }
                    val categorizedChanges = CategorizedChanges(changes, createdFiles, modifiedFiles, movedFiles)
                    future.complete(categorizedChanges)
                } catch (e: VcsException) {
                    logger.error("Error getting changes for $branchNameToCompare: ${e.message}", e)
                    future.completeExceptionally(e)
                } catch (e: Exception) {
                    logger.error("Unexpected error getting changes for $branchNameToCompare: ${e.message}", e)
                    future.completeExceptionally(e)
                }
            }
        }.queue()

        return future
    }

    /**
     * Asynchronously loads the content of a file from a specific git revision (branch, tag, commit hash).
     *
     * @param revision The git revision to load the file from.
     * @param file The virtual file whose content is to be loaded.
     * @return A CompletableFuture that will complete with the file content as a String, or null if the file
     *         does not exist in that revision. The future completes exceptionally on other errors.
     */
    fun getFileContentForRevision(revision: String, file: VirtualFile): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        val repository = getCurrentRepository()

        if (repository == null) {
            logger.warn("Cannot get file content for revision '$revision' for file '${file.path}', no repository found.")
            future.complete(null)
            return future
        }

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                logger.debug("GUTTER_GIT_SERVICE: Preparing to fetch content for revision:'$revision' file:'${file.path}'")

                val relativePath = VfsUtilCore.getRelativePath(file, repository.root, '/')
                if (relativePath == null) {
                    val errorMessage = "Could not calculate relative path for file '${file.path}' against repo root '${repository.root.path}'."
                    logger.error("GUTTER_GIT_SERVICE: $errorMessage")
                    future.completeExceptionally(IllegalStateException(errorMessage))
                    return@executeOnPooledThread
                }

                val revisionContentBytes = GitFileUtils.getFileContent(project, repository.root, revision, relativePath)
                val rawContent = String(revisionContentBytes, file.charset)

                // The IntelliJ Document model requires LF ('\n') line endings, but Git on Windows might return CRLF ('\r\n').
                // We must convert them to prevent a "Wrong line separators" AssertionError from the line status tracker.
                val normalizedContent = StringUtil.convertLineSeparators(rawContent)

                logger.info("GUTTER_GIT_SERVICE: Successfully fetched content for '$relativePath' in revision '$revision'.")
                future.complete(normalizedContent)
            } catch (e: VcsException) {
                logger.warn("GUTTER_GIT_SERVICE: VcsException while getting content for '${file.path}' in revision '$revision'. Message: ${e.message}")
                future.completeExceptionally(e)
            } catch (e: Exception) {
                logger.error("GUTTER_GIT_SERVICE: Unhandled exception while getting content for '${file.path}' in revision '$revision'.", e)
                future.completeExceptionally(e)
            }
        }
        return future
    }
}