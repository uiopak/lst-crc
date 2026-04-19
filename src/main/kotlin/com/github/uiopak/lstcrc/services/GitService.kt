package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitContentRevision
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import kotlinx.collections.immutable.toImmutableMap
import java.util.concurrent.CompletableFuture

/**
 * Holds the result of a Git diff, with files categorized by their change type.
 *
 * @param allChanges The raw list of [Change] objects from the VCS API.
 * @param createdFiles A list of files considered new in the comparison.
 * @param modifiedFiles A list of files with content modifications.
 * @param movedFiles A list of files that were moved or renamed.
 * @param deletedFiles A list of virtual files representing deleted files.
 * @param comparisonContext A map of a repository root path to the branch/revision it was compared against.
 */
data class CategorizedChanges(
    val allChanges: List<Change>,
    val createdFiles: List<VirtualFile>,
    val modifiedFiles: List<VirtualFile>,
    val movedFiles: List<VirtualFile>,
    val deletedFiles: List<VirtualFile>,
    val comparisonContext: Map<String, String>
)

data class BranchSnapshot(
    val localBranches: List<String>,
    val remoteBranches: List<String>
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

    fun getBranchSnapshot(repository: GitRepository?): BranchSnapshot {
        val targetRepository = repository ?: getPrimaryRepository()
        val branchRoot = targetRepository?.root ?: project.basePath
            ?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it) }
            ?.takeIf { it.findChild(".git") != null }

        if (branchRoot == null) {
            logger.debug("getBranchSnapshot() called with no repository available.")
            return BranchSnapshot(emptyList(), emptyList())
        }

        if (targetRepository == null) {
            logger.info("getBranchSnapshot() is using the project base path fallback: ${branchRoot.path}")
        } else {
            targetRepository.update()
        }

        val localBranches = runBranchList(branchRoot, includeRemoteBranches = false)
        val remoteBranches = runBranchList(branchRoot, includeRemoteBranches = true)

        val snapshot = if (localBranches.isNotEmpty() || remoteBranches.isNotEmpty()) {
            BranchSnapshot(localBranches, remoteBranches)
        } else if (targetRepository != null) {
            BranchSnapshot(
                targetRepository.branches.localBranches.map { it.name },
                targetRepository.branches.remoteBranches.map { it.name }
            )
        } else {
            BranchSnapshot(emptyList(), emptyList())
        }

        logger.info(
            "Loaded branch snapshot for repo '${branchRoot.name}': " +
                "${snapshot.localBranches.size} local, ${snapshot.remoteBranches.size} remote branches."
        )
        return snapshot
    }

    fun getChanges(tabInfo: TabInfo?): CompletableFuture<GetChangesResult> {
        val future = CompletableFuture<GetChangesResult>()
        val repositories = getRepositories()
        val isLoadingHead = tabInfo == null
        val profileName = tabInfo?.branchName ?: "HEAD"

        logger.debug("getChanges called for profile: $profileName")

        if (repositories.isEmpty()) {
            val emptyChanges = CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
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
                    val allDeletedFiles = mutableListOf<VirtualFile>()
                    val comparisonContext = mutableMapOf<String, String>()
                    val failures = mutableMapOf<GitRepository, String>()

                    if (isLoadingHead) {
                        logger.debug("Using direct Git status for HEAD across all repositories.")
                        repositories.forEach { repo ->
                            allChanges.addAll(loadLocalChanges(repo))
                            comparisonContext[repo.root.path] = "HEAD"
                        }
                    } else {
                        val primaryRevision = tabInfo.branchName
                        val overrides = tabInfo.comparisonMap.toImmutableMap()

                        for (repo in repositories) {
                            indicator.text = "Checking repository: ${repo.root.name}"
                            // Use the override if it exists, otherwise use the tab's primary revision.
                            val target: String = overrides[repo.root.path] ?: primaryRevision
                            comparisonContext[repo.root.path] = target
                            logger.debug("Repo '${repo.root.path}': using target '$target'")

                            // If repo is fresh or target is HEAD, compare against local changes.
                            if (repo.isFresh || target == "HEAD") {
                                if (repo.isFresh) {
                                    logger.info("Repo '${repo.root.name}' is fresh. Falling back to comparing against HEAD for target '$target'.")
                                } else { // target == "HEAD"
                                    logger.debug("Repo '${repo.root.name}' is targeting HEAD. Comparing against local changes.")
                                }

                                allChanges.addAll(loadLocalChanges(repo))
                            } else {
                                try {
                                    val changes = GitChangeUtils.getDiffWithWorkingDir(
                                        project, repo.root, target, null, false, false
                                    )
                                    allChanges.addAll(changes)
                                } catch (e: VcsException) {
                                    logger.warn(
                                        "git diff failed for repo '${repo.root.name}' against target '$target'. " +
                                                "Assuming revision is invalid. Error: ${e.message}"
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
                            Change.Type.DELETED -> change.beforeRevision?.let { beforeRevision ->
                                createDeletedVirtualFile(beforeRevision)?.let { allDeletedFiles.add(it) }
                            }
                            else -> { /* Other types are ignored */ }
                        }
                    }

                    val categorizedChanges = CategorizedChanges(
                        allChanges.distinct(),
                        allCreatedFiles.distinct(),
                        allModifiedFiles.distinct(),
                        allMovedFiles.distinct(),
                        allDeletedFiles.distinct(),
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

    private fun runBranchList(root: VirtualFile, includeRemoteBranches: Boolean): List<String> {
        val handler = GitLineHandler(project, root, GitCommand.BRANCH)
        handler.setSilent(true)
        handler.addParameters("--list")
        handler.addParameters("--format=%(refname:short)")
        if (includeRemoteBranches) {
            handler.addParameters("--remotes")
        }

        val result = Git.getInstance().runCommand(handler)
        if (result.exitCode != 0) {
            logger.warn(
                "Failed to load ${if (includeRemoteBranches) "remote" else "local"} branches for repo '${root.name}': " +
                    result.errorOutputAsJoinedString
            )
            return emptyList()
        }

        return result.outputAsJoinedString
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { includeRemoteBranches && it.endsWith("/HEAD") }
            .toList()
    }

    private fun loadLocalChanges(repo: GitRepository): List<Change> {
        val trackedChanges = loadTrackedChangesAgainstHead(repo)
        val untrackedChanges = loadUntrackedChanges(repo)
        return (trackedChanges + untrackedChanges).distinctBy { changeKey(it) }
    }

    private fun loadTrackedChangesAgainstHead(repo: GitRepository): List<Change> {
        if (repo.isFresh) {
            return emptyList()
        }

        return try {
            GitChangeUtils.getDiffWithWorkingDir(project, repo.root, "HEAD", null, false, true).toList()
        } catch (e: VcsException) {
            logger.warn("Failed to load tracked local changes for repo '${repo.root.name}' against HEAD: ${e.message}")
            emptyList()
        }
    }

    private fun loadUntrackedChanges(repo: GitRepository): List<Change> {
        val handler = GitLineHandler(project, repo.root, GitCommand.LS_FILES)
        handler.setSilent(true)
        handler.addParameters("--others", "--exclude-standard", "-z")
        val result = Git.getInstance().runCommand(handler)

        if (result.exitCode != 0) {
            logger.warn(
                "Failed to load untracked local changes for repo '${repo.root.name}': " +
                    result.errorOutputAsJoinedString
            )
            return emptyList()
        }

        return result.outputAsJoinedString
            .split('\u0000')
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { relativePath ->
                try {
                    val afterFilePath = GitContentRevision.createPathFromEscaped(repo.root, relativePath)
                    val afterRevision = GitContentRevision.createRevision(afterFilePath, null, project)
                    Change(null, afterRevision, FileStatus.ADDED)
                } catch (e: Exception) {
                    logger.error("Failed to parse untracked file path '$relativePath' for repo '${repo.root.name}'", e)
                    null
                }
            }
            .toList()
    }

    private fun changeKey(change: Change): String {
        val beforePath = change.beforeRevision?.file?.path.orEmpty()
        val afterPath = change.afterRevision?.file?.path.orEmpty()
        return "${change.type}:$beforePath->$afterPath"
    }

    private fun createDeletedVirtualFile(beforeRevision: ContentRevision): VirtualFile? {
        return try {
            logger.debug("Prepared lazy deleted-file virtual file for '${beforeRevision.file.path}'.")
            ContentRevisionVirtualFile.create(beforeRevision)
        } catch (e: Exception) {
            logger.warn("Failed to create VcsVirtualFile for deleted file: ${beforeRevision.file.path}", e)
            null
        }
    }



    suspend fun getFileContentForRevision(revision: String, file: VirtualFile): String? {
        val repository = getRepositoryForFile(file)

        if (repository == null) {
            logger.warn("Cannot get file content for revision '$revision' for file '${file.path}', no repository found for this file.")
            return null
        }

        logger.debug("GUTTER_GIT_SERVICE: Preparing to fetch content for revision:'${revision}' file:'${file.path}'")

        val relativePath = VfsUtilCore.getRelativePath(file, repository.root, '/')
            ?: throw IllegalStateException("Could not calculate relative path for file '${file.path}' against repo root '${repository.root.path}'.")

        val revisionContentBytes = GitFileUtils.getFileContent(project, repository.root, revision, relativePath)
        val rawContent = org.apache.commons.io.input.BOMInputStream.builder()
            .setInputStream(java.io.ByteArrayInputStream(revisionContentBytes))
            .setByteOrderMarks(
                org.apache.commons.io.ByteOrderMark.UTF_8,
                org.apache.commons.io.ByteOrderMark.UTF_16LE,
                org.apache.commons.io.ByteOrderMark.UTF_16BE,
                org.apache.commons.io.ByteOrderMark.UTF_32LE,
                org.apache.commons.io.ByteOrderMark.UTF_32BE
            )
            .get()
            .use {
                it.reader(file.charset).readText()
            }

        // The IntelliJ Document model requires LF ('\n') line endings, but Git on Windows might return CRLF ('\r\n').
        val normalizedContent = StringUtil.convertLineSeparators(rawContent)

        logger.info("GUTTER_GIT_SERVICE: Successfully fetched content for '${relativePath}' in revision '${revision}'.")
        return normalizedContent
    }
}