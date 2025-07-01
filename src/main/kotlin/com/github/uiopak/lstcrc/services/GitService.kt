package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import git4idea.util.StringScanner
import java.util.concurrent.CompletableFuture

/**
 * Holds the result of a Git diff, with files categorized by their change type.
 *
 * @param allChanges The raw list of [Change] objects from the VCS API.
 * @param createdFiles A list of files considered new in the comparison.
 * @param modifiedFiles A list of files with content modifications.
 * @param movedFiles A list of files that were moved or renamed.
 * @param comparisonContext A map of a repository root path to the branch/revision it was compared against.
 */
data class CategorizedChanges(
    val allChanges: List<Change>,
    val createdFiles: List<VirtualFile>,
    val modifiedFiles: List<VirtualFile>,
    val movedFiles: List<VirtualFile>,
    val comparisonContext: Map<String, String>
)

/**
 * Encapsulates the complete result of a `getChanges` operation, including both successfully
 * retrieved changes and any failures that occurred for specific repositories.
 *
 * @param categorizedChanges Successfully categorized changes from all valid repositories.
 * @param failures A map of repositories that failed to a string representing the invalid revision.
 */
data class GetChangesResult(
    val categorizedChanges: CategorizedChanges,
    val failures: Map<GitRepository, String>
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

    fun getChanges(tabInfo: TabInfo?): CompletableFuture<GetChangesResult> {
        val future = CompletableFuture<GetChangesResult>()
        val repositories = getRepositories()
        val isLoadingHead = tabInfo == null
        val profileName = tabInfo?.branchName ?: "HEAD"

        logger.debug("getChanges called for profile: $profileName")

        if (repositories.isEmpty()) {
            val emptyChanges = CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
            future.complete(GetChangesResult(emptyChanges, emptyMap()))
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
                    val failures = mutableMapOf<GitRepository, String>()

                    if (isLoadingHead) {
                        logger.debug("Using ChangeListManager for HEAD across all repositories.")
                        allChanges.addAll(ChangeListManager.getInstance(project).allChanges)
                        repositories.forEach { repo ->
                            comparisonContext[repo.root.path] = "HEAD"
                        }
                    } else {
                        val primaryRevision = tabInfo.branchName
                        val overrides = tabInfo.comparisonMap

                        for (repo in repositories) {
                            indicator.text = "Checking repository: ${repo.root.name}"
                            // Use the override if it exists, otherwise use the tab's primary revision.
                            // The `git diff` command will fail if the revision is invalid for this repo,
                            // which is caught below. This correctly handles commit hashes.
                            val target: String = overrides[repo.root.path] ?: primaryRevision
                            comparisonContext[repo.root.path] = target
                            logger.debug("Repo '${repo.root.path}': using target '$target'")

                            if (target == "HEAD") {
                                val allLocalChanges = ChangeListManager.getInstance(project).allChanges
                                val repoChanges = allLocalChanges.filter { change ->
                                    val vf = change.virtualFile
                                    vf != null && VfsUtilCore.isAncestor(repo.root, vf, false)
                                }
                                allChanges.addAll(repoChanges)
                            } else {
                                // Use the low-level Git command to prevent fatal exceptions for bad revisions.
                                val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
                                handler.setSilent(true)
                                // Use --no-renames and then handle R-lines manually to avoid issues with move score thresholding
                                handler.addParameters("--name-status", "--diff-filter=ACDMRTUXB", target)
                                val result = Git.getInstance().runCommand(handler)

                                if (result.exitCode == 0) {
                                    val output = result.outputAsJoinedString
                                    val parsedChanges = parseNameStatusOutput(repo, target, output)
                                    allChanges.addAll(parsedChanges)
                                } else {
                                    // An exit code of 128 often means "bad revision".
                                    logger.warn(
                                        "git diff command failed for repo '${repo.root.name}' against target '$target' " +
                                                "with exit code ${result.exitCode}. Assuming revision is invalid. Error: ${result.errorOutputAsJoinedString}"
                                    )
                                    failures[repo] = target
                                }
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
                    future.complete(GetChangesResult(categorizedChanges, failures))

                } catch (e: Exception) {
                    // This will now only catch truly unexpected errors.
                    logger.error("Unexpected error getting changes for $profileName: ${e.message}", e)
                    future.completeExceptionally(e)
                }
            }
        }.queue()

        return future
    }

    private fun parseNameStatusOutput(repo: GitRepository, targetRevision: String, output: String): List<Change> {
        val changes = mutableListOf<Change>()

        val targetRevisionNumber: GitRevisionNumber? = try {
            GitChangeUtils.resolveReference(project, repo.root, targetRevision)
        } catch (e: VcsException) {
            logger.warn("Could not resolve reference '$targetRevision' for repo '${repo.root.name}' after successful diff. Using null revision.", e)
            null
        }

        val scanner = StringScanner(output)
        while (scanner.hasMoreData()) {
            if (scanner.isEol) {
                scanner.nextLine()
                continue
            }
            if ("CADUMRT".indexOf(scanner.peek()) == -1) {
                logger.warn("Exiting status line parse loop due to unexpected char: '${scanner.peek()}'")
                break
            }
            val line = scanner.line()
            val tokens = line.split('\t')

            val status: FileStatus
            val beforeFilePath: FilePath?
            val afterFilePath: FilePath?

            try {
                when (tokens[0].first()) {
                    'A' -> {
                        status = FileStatus.ADDED
                        afterFilePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[1])
                        beforeFilePath = null
                    }
                    'M', 'T' -> { // Modified, Type-Changed
                        status = FileStatus.MODIFIED
                        afterFilePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[1])
                        beforeFilePath = afterFilePath
                    }
                    'D' -> {
                        status = FileStatus.DELETED
                        beforeFilePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[1])
                        afterFilePath = null
                    }
                    'R' -> { // Renamed
                        status = FileStatus.MODIFIED
                        beforeFilePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[1])
                        afterFilePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[2])
                    }
                    'C' -> { // Copied
                        status = FileStatus.ADDED
                        beforeFilePath = null
                        afterFilePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[2])
                    }
                    'U' -> {
                        status = FileStatus.MERGED_WITH_CONFLICTS
                        afterFilePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[1])
                        beforeFilePath = afterFilePath
                    }
                    else -> {
                        logger.warn("Unknown git status char in line: $line")
                        continue
                    }
                }

                val beforeRevision: ContentRevision? = if (beforeFilePath != null)
                    GitContentRevision.createRevision(beforeFilePath, targetRevisionNumber, project)
                else null

                val afterRevision: ContentRevision? = if (afterFilePath != null)
                    GitContentRevision.createRevision(afterFilePath, null, project) // Working tree content
                else null

                changes.add(Change(beforeRevision, afterRevision, status))
            } catch (e: Exception) {
                logger.error("Failed to parse git diff line: '$line'", e)
            }
        }
        return changes
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