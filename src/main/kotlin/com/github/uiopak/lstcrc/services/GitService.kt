package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
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
 * @param comparisonContext A map of repository root path to the branch/revision it was compared against.
 */
data class CategorizedChanges(
    val allChanges: List<Change>,
    val createdFiles: List<VirtualFile>,
    val modifiedFiles: List<VirtualFile>,
    val movedFiles: List<VirtualFile>,
    val comparisonContext: Map<String, String>
)

/**
 * A project-level service responsible for all interactions with the Git4Idea plugin API.
 * It provides asynchronous methods to fetch branches, calculate diffs, and retrieve file content
 * from specific revisions.
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    private val logger = thisLogger()

    internal fun getRepositoryForFile(file: VirtualFile): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        return repositoryManager.getRepositoryForFile(file)
    }

    fun getRepositories(): List<GitRepository> {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        return repositoryManager.repositories
    }

    /**
     * Gets the "primary" repository for the project. This is useful for context where a single
     * repository is needed (e.g., startup). The logic prefers the repository containing the
     * project's base directory.
     */
    internal fun getPrimaryRepository(): GitRepository? {
        logger.debug("getPrimaryRepository() called.")
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories
        logger.debug("Found ${repositories.size} repositories.")

        if (repositories.isEmpty()) {
            logger.info("No Git repositories found. Returning null.")
            return null
        }
        if (repositories.size == 1) {
            logger.info("Exactly one Git repository found: ${repositories.first().root.path}")
            return repositories.first()
        }

        // For multiple repositories, prefer the one that contains the project's base path.
        val projectBasePath = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        if (projectBasePath != null) {
            val repoForProjectRoot = repositoryManager.getRepositoryForFile(projectBasePath)
            if (repoForProjectRoot != null) {
                logger.info("Multiple repositories found. Using the one for the project root: ${repoForProjectRoot.root.path}")
                return repoForProjectRoot
            }
        }

        // Fallback to the first repository if no better match is found.
        logger.warn("Multiple Git repositories found, but none contains the project base path. Using the first one: ${repositories.first().root.path}")
        return repositories.first()
    }

    fun getLocalBranches(): List<String> {
        val allBranches = getRepositories().flatMap { repo ->
            repo.branches.localBranches.map { it.name }
        }
        return allBranches.distinct().sorted()
    }

    fun getRemoteBranches(): List<String> {
        val allBranches = getRepositories().flatMap { repo ->
            repo.branches.remoteBranches.map { it.name }
        }
        return allBranches.distinct().sorted()
    }

    fun getChanges(tabInfo: TabInfo?): CompletableFuture<CategorizedChanges> {
        val future = CompletableFuture<CategorizedChanges>()
        val repositories = getRepositories()
        val isLoadingHead = tabInfo == null
        val profileName = tabInfo?.branchName ?: "HEAD"

        logger.debug("getChanges called for profile: $profileName")

        if (repositories.isEmpty()) {
            future.complete(CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()))
            return future
        }

        object : Task.Backgroundable(project, LstCrcBundle.message("git.task.loading.changes")) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val allChanges = mutableListOf<Change>()
                    val allCreatedFiles = mutableListOf<VirtualFile>()
                    val allModifiedFiles = mutableListOf<VirtualFile>()
                    val allMovedFiles = mutableListOf<VirtualFile>()
                    val comparisonContext = mutableMapOf<String, String>()

                    if (isLoadingHead) {
                        logger.debug("Using ChangeListManager for HEAD across all repositories.")
                        allChanges.addAll(ChangeListManager.getInstance(project).allChanges)
                        repositories.forEach { repo ->
                            comparisonContext[repo.root.path] = "HEAD"
                        }
                    } else {
                        // For a specific profile, iterate through each repository.
                        val primaryBranch = tabInfo!!.branchName
                        val overrides = tabInfo.comparisonMap

                        for (repo in repositories) {
                            indicator.text = "Checking repository: ${repo.root.name}"

                            // Determine the comparison target for this repository
                            val target: String = overrides[repo.root.path]
                                ?: if (repo.branches.findBranchByName(primaryBranch) != null) primaryBranch else "HEAD"

                            comparisonContext[repo.root.path] = target
                            logger.debug("Repo '${repo.root.path}': using target '$target'")

                            val repoChanges = if (target == "HEAD") {
                                // For HEAD, we want only the changes from this specific repo
                                val allLocalChanges = ChangeListManager.getInstance(project).allChanges
                                allLocalChanges.filter { change ->
                                    val vf = change.virtualFile
                                    vf != null && VfsUtilCore.isAncestor(repo.root, vf, false)
                                }
                            } else {
                                GitChangeUtils.getDiffWithWorkingTree(repo, target, true)
                            }

                            if (repoChanges != null) {
                                allChanges.addAll(repoChanges)
                            }
                        }
                    }

                    for (change in allChanges) {
                        when (change.type) {
                            Change.Type.NEW -> change.afterRevision?.file?.virtualFile?.let { allCreatedFiles.add(it) }
                            Change.Type.MODIFICATION -> change.afterRevision?.file?.virtualFile?.let { allModifiedFiles.add(it) }
                            Change.Type.MOVED -> change.afterRevision?.file?.virtualFile?.let { allMovedFiles.add(it) }
                            else -> { /* Other types like DELETED are ignored for categorization */ }
                        }
                    }

                    val categorizedChanges = CategorizedChanges(
                        allChanges.distinct(),
                        allCreatedFiles.distinct(),
                        allModifiedFiles.distinct(),
                        allMovedFiles.distinct(),
                        comparisonContext
                    )
                    future.complete(categorizedChanges)
                } catch (e: VcsException) {
                    logger.error("Error getting changes for $profileName: ${e.message}", e)
                    future.completeExceptionally(e)
                } catch (e: Exception) {
                    logger.error("Unexpected error getting changes for $profileName: ${e.message}", e)
                    future.completeExceptionally(e)
                }
            }
        }.queue()

        return future
    }

    fun getFileContentForRevision(revision: String, file: VirtualFile): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        val repository = getRepositoryForFile(file)

        if (repository == null) {
            logger.warn("Cannot get file content for revision '$revision' for file '${file.path}', no repository found for this file.")
            future.complete(null)
            return future
        }

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                logger.debug("GUTTER_GIT_SERVICE: Preparing to fetch content for revision:'${revision}' file:'${file.path}'")

                val relativePath = VfsUtilCore.getRelativePath(file, repository.root, '/')
                if (relativePath == null) {
                    val errorMessage = "Could not calculate relative path for file '${file.path}' against repo root '${repository.root.path}'.'"
                    logger.error("GUTTER_GIT_SERVICE: $errorMessage")
                    future.completeExceptionally(IllegalStateException(errorMessage))
                    return@executeOnPooledThread
                }

                val revisionContentBytes = GitFileUtils.getFileContent(project, repository.root, revision, relativePath)
                val rawContent = String(revisionContentBytes, file.charset)

                // The IntelliJ Document model requires LF ('\n') line endings, but Git on Windows might return CRLF ('\r\n').
                // We must convert them to prevent a "Wrong line separators" AssertionError from the line status tracker.
                val normalizedContent = StringUtil.convertLineSeparators(rawContent)

                logger.info("GUTTER_GIT_SERVICE: Successfully fetched content for '${relativePath}' in revision '${revision}'.")
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