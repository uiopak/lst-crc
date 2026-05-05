package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils


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

    private companion object {
        private val COMMIT_HASH_REGEX = Regex("^[0-9a-fA-F]{7,40}$")
    }

    private val logger = thisLogger()

    private data class ParsedDiffStatus(
        val changeType: Change.Type,
        val fileStatus: FileStatus
    )

    private data class ChangeLoadContext(
        val allChanges: MutableList<Change> = mutableListOf(),
        val comparisonContext: MutableMap<String, String> = mutableMapOf(),
        val failures: MutableMap<GitRepository, String> = mutableMapOf()
    )

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
        val repo = repository ?: getPrimaryRepository() ?: run {
            logger.debug("getBranchSnapshot() called with no repository available.")
            return BranchSnapshot(emptyList(), emptyList())
        }
        repo.update()
        val branches = repo.branches
        val snapshot = BranchSnapshot(
            branches.localBranches.map { it.name },
            branches.remoteBranches.map { it.name }
        )
        logger.info(
            "Loaded branch snapshot for repo '${repo.root.name}': " +
                "${snapshot.localBranches.size} local, ${snapshot.remoteBranches.size} remote branches."
        )
        return snapshot
    }

    suspend fun getChanges(tabInfo: TabInfo?): GetChangesResult {
        val repositories = getRepositories()
        val profileName = tabInfo?.branchName ?: "HEAD"

        logger.debug("getChanges called for profile: $profileName")

        if (repositories.isEmpty()) {
            return GetChangesResult(
                CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()),
                emptyMap()
            )
        }

        return withBackgroundProgress(project, LstCrcBundle.message("git.task.loading.changes")) {
            withContext(Dispatchers.IO) {
                loadChangesResult(repositories, tabInfo)
            }
        }
    }

    private fun loadChangesResult(
        repositories: List<GitRepository>,
        tabInfo: TabInfo?
    ): GetChangesResult {
        val context = ChangeLoadContext()
        if (tabInfo == null) {
            logger.debug("Using direct Git status for HEAD across all repositories.")
            for (repo in repositories) {
                context.allChanges.addAll(loadLocalChanges(repo))
                context.comparisonContext[repo.root.path] = "HEAD"
            }
        } else {
            val primaryRevision = tabInfo.branchName
            for (repo in repositories) {
                val target = tabInfo.comparisonMap[repo.root.path] ?: primaryRevision
                context.comparisonContext[repo.root.path] = target
                logger.debug("Repo '${repo.root.path}': using target '$target'")
                context.allChanges.addAll(loadChangesForTarget(repo, primaryRevision, target, context.failures))
            }
        }
        return GetChangesResult(buildCategorizedChanges(context.allChanges, context.comparisonContext), context.failures)
    }

    private fun loadChangesForTarget(
        repo: GitRepository,
        primaryRevision: String,
        target: String,
        failures: MutableMap<GitRepository, String>
    ): List<Change> {
        repo.update()

        if (repo.isFresh) {
            logLocalComparisonFallback(repo, target)
            return loadLocalChanges(repo)
        }

        val primaryRevisionExistsInRepo =
            target == "HEAD" || target == primaryRevision || revisionExistsInRepo(repo, primaryRevision)

        if (shouldCompareAgainstWorkingTree(primaryRevision, target, primaryRevisionExistsInRepo)) {
            logLocalComparisonFallback(repo, target)
            return try {
                loadChangesAgainstWorkingTree(repo, target)
            } catch (e: VcsException) {
                logger.warn(
                    "git diff failed for repo '${repo.root.name}' against target '$target'. " +
                        "Assuming revision is invalid. Error: ${e.message}"
                )
                failures[repo] = target
                emptyList()
            }
        }

        return try {
            loadTrackedChangesBetweenRevisions(repo, primaryRevision, target)
        } catch (e: VcsException) {
            logger.warn(
                "git diff failed for repo '${repo.root.name}' against target '$target'. " +
                    "Assuming revision is invalid. Error: ${e.message}"
            )
            failures[repo] = target
            emptyList()
        }
    }

    internal fun shouldCompareAgainstWorkingTree(
        primaryRevision: String,
        target: String,
        primaryRevisionExistsInRepo: Boolean = true
    ): Boolean {
        return target == "HEAD" || target == primaryRevision || !primaryRevisionExistsInRepo
    }

    internal fun isExplicitRevisionTarget(target: String): Boolean {
        return COMMIT_HASH_REGEX.matches(target)
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun revisionExistsInRepo(repo: GitRepository, revision: String): Boolean {
        val handler = GitLineHandler(project, repo.root, GitCommand.REV_PARSE)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters("--verify", "--quiet", revision)
        return Git.getInstance().runCommand(handler).exitCode == 0
    }

    private fun loadChangesAgainstWorkingTree(repo: GitRepository, target: String): List<Change> {
        val trackedChanges = loadTrackedChangesAgainstWorkingTree(repo, target)
        val untrackedChanges = loadOptionalUntrackedChanges(repo)
        return overlayUnsavedDocumentChanges(repo, target, trackedChanges + untrackedChanges)
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun loadTrackedChangesAgainstWorkingTree(repo: GitRepository, target: String): List<Change> {
        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters("--name-status", "--diff-filter=ADCMRUXT", "-M", target)

        val output = Git.getInstance().runCommand(handler).getOutputOrThrow()
        val targetRevision = GitRevisionNumber(target)

        return output.lineSequence()
            .mapNotNull { parseWorkingTreeDiffLine(repo, targetRevision, it) }
            .toList()
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun loadTrackedChangesBetweenRevisions(
        repo: GitRepository,
        baseRevision: String,
        targetRevision: String
    ): List<Change> {
        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters("--name-status", "--diff-filter=ADCMRUXT", "-M", baseRevision, targetRevision)

        val output = Git.getInstance().runCommand(handler).getOutputOrThrow()
        val beforeRevision = GitRevisionNumber(baseRevision)
        val afterRevision = GitRevisionNumber(targetRevision)

        return output.lineSequence()
            .mapNotNull { parseRevisionDiffLine(repo, beforeRevision, afterRevision, it) }
            .toList()
    }

    private fun parseWorkingTreeDiffLine(
        repo: GitRepository,
        targetRevision: GitRevisionNumber,
        line: String
    ): Change? {
        return parseDiffLine(
            repo = repo,
            line = line,
            beforeRevisionAt = { path -> GitContentRevision.createRevision(path, targetRevision, project) },
            afterRevisionAt = { path -> GitContentRevision.createRevision(path, null, project) }
        )
    }

    private fun parseRevisionDiffLine(
        repo: GitRepository,
        beforeRevisionNumber: GitRevisionNumber,
        afterRevisionNumber: GitRevisionNumber,
        line: String
    ): Change? {
        return parseDiffLine(
            repo = repo,
            line = line,
            beforeRevisionAt = { path -> GitContentRevision.createRevision(path, beforeRevisionNumber, project) },
            afterRevisionAt = { path -> GitContentRevision.createRevision(path, afterRevisionNumber, project) }
        )
    }

    private fun parseDiffLine(
        repo: GitRepository,
        line: String,
        beforeRevisionAt: (com.intellij.openapi.vcs.FilePath) -> ContentRevision,
        afterRevisionAt: (com.intellij.openapi.vcs.FilePath) -> ContentRevision
    ): Change? {
        if (line.isBlank()) {
            return null
        }

        val tokens = line.split('\t')
        val statusToken = tokens.firstOrNull().orEmpty()
        val parsedStatus = parseDiffStatus(statusToken) ?: return null

        fun revisionPath(index: Int) = GitContentRevision.createPathFromEscaped(repo.root, tokens[index])

        return when (parsedStatus.changeType) {
            Change.Type.NEW -> {
                if (tokens.size < 2) return null
                Change(null, afterRevisionAt(revisionPath(1)), parsedStatus.fileStatus)
            }
            Change.Type.DELETED -> {
                if (tokens.size < 2) return null
                Change(beforeRevisionAt(revisionPath(1)), null, parsedStatus.fileStatus)
            }
            Change.Type.MODIFICATION -> {
                if (tokens.size < 2) return null
                val path = revisionPath(1)
                Change(beforeRevisionAt(path), afterRevisionAt(path), parsedStatus.fileStatus)
            }
            Change.Type.MOVED -> {
                if (tokens.size < 3) return null
                Change(beforeRevisionAt(revisionPath(1)), afterRevisionAt(revisionPath(2)), parsedStatus.fileStatus)
            }
        }
    }

    private fun parseDiffStatus(statusToken: String): ParsedDiffStatus? {
        return when (statusToken.firstOrNull()) {
            'A' -> ParsedDiffStatus(Change.Type.NEW, FileStatus.ADDED)
            'D' -> ParsedDiffStatus(Change.Type.DELETED, FileStatus.DELETED)
            'M', 'T', 'U', 'X' -> ParsedDiffStatus(Change.Type.MODIFICATION, FileStatus.MODIFIED)
            'R', 'C' -> ParsedDiffStatus(Change.Type.MOVED, FileStatus.MODIFIED)
            else -> null
        }
    }

    private fun logLocalComparisonFallback(repo: GitRepository, target: String) {
        if (repo.isFresh) {
            logger.info("Repo '${repo.root.name}' is fresh. Falling back to comparing against HEAD for target '$target'.")
            return
        }

        logger.debug("Repo '${repo.root.name}' is targeting HEAD. Comparing against local changes.")
    }

    private fun buildCategorizedChanges(
        allChanges: List<Change>,
        comparisonContext: Map<String, String>
    ): CategorizedChanges {
        val created = mutableListOf<VirtualFile>()
        val modified = mutableListOf<VirtualFile>()
        val moved = mutableListOf<VirtualFile>()
        val deleted = mutableListOf<VirtualFile>()

        allChanges.forEach { change ->
            val beforeRevision = change.beforeRevision
            val afterRevision = change.afterRevision
            when {
                beforeRevision == null && afterRevision != null -> {
                    createComparisonVirtualFile(afterRevision)?.let(created::add)
                }
                beforeRevision != null && afterRevision == null -> {
                    createDeletedVirtualFile(beforeRevision)?.let(deleted::add)
                }
                beforeRevision != null && afterRevision != null -> {
                    val beforePath = beforeRevision.file.path
                    val afterPath = afterRevision.file.path
                    val targetCollection = if (beforePath != afterPath) moved else modified
                    createComparisonVirtualFile(afterRevision)?.let(targetCollection::add)
                }
            }
        }

        return CategorizedChanges(
            allChanges = allChanges.distinct(),
            createdFiles = created.distinct(),
            modifiedFiles = modified.distinct(),
            movedFiles = moved.distinct(),
            deletedFiles = deleted.distinct(),
            comparisonContext = comparisonContext
        )
    }

    private fun loadLocalChanges(repo: GitRepository): List<Change> {
        val trackedChanges = loadTrackedChangesAgainstHead(repo)
        val untrackedChanges = loadOptionalUntrackedChanges(repo)
        return overlayUnsavedDocumentChanges(repo, "HEAD", trackedChanges + untrackedChanges)
    }

    private fun loadOptionalUntrackedChanges(repo: GitRepository): List<Change> {
        return if (ToolWindowSettingsProvider.isShowUntrackedFilesAsNew()) {
            loadUntrackedChanges(repo)
        } else {
            emptyList()
        }
    }

    private fun loadTrackedChangesAgainstHead(repo: GitRepository): List<Change> {
        repo.update()

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

    @Suppress("UsePropertyAccessSyntax")
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
                    Change(null, afterRevision, FileStatus.UNKNOWN)
                } catch (e: Exception) {
                    logger.error("Failed to parse untracked file path '$relativePath' for repo '${repo.root.name}'", e)
                    null
                }
            }
            .toList()
    }

    private fun overlayUnsavedDocumentChanges(
        repo: GitRepository,
        targetRevision: String,
        baseChanges: List<Change>
    ): List<Change> {
        val mergedChanges = LinkedHashMap<String, Change>()
        baseChanges.forEach { change ->
            mergedChanges[unsavedOverlayKey(change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path.orEmpty())] = change
        }

        collectUnsavedDocumentChanges(repo, targetRevision).forEach { change ->
            val path = change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path.orEmpty()
            val key = unsavedOverlayKey(path)
            mergedChanges[key] = mergeUnsavedOverlayChange(mergedChanges[key], change)
        }

        return mergedChanges.values.toList()
    }

    private fun collectUnsavedDocumentChanges(repo: GitRepository, targetRevision: String): List<Change> {
        val fileDocumentManager = FileDocumentManager.getInstance()

        return fileDocumentManager.unsavedDocuments.asSequence()
            .mapNotNull { document -> fileDocumentManager.getFile(document) }
            .filter { file ->
                file.isValid &&
                    VfsUtilCore.isAncestor(repo.root, file, false) &&
                    fileDocumentManager.isFileModified(file)
            }
            .mapNotNull { file -> createUnsavedDocumentChange(file, targetRevision) }
            .toList()
    }

    private fun createUnsavedDocumentChange(file: VirtualFile, targetRevision: String): Change? {
        return try {
            val filePath = VcsUtil.getFilePath(file)
            val beforeRevision = GitContentRevision.createRevision(filePath, GitRevisionNumber(targetRevision), project)
            val afterRevision = CurrentContentRevision.create(filePath)
            Change(beforeRevision, afterRevision, FileStatus.MODIFIED)
        } catch (e: Exception) {
            logger.warn("Failed to create unsaved document change for '${file.path}' against '$targetRevision'", e)
            null
        }
    }

    private fun unsavedOverlayKey(path: String): String = path.lowercase()

    private fun createComparisonVirtualFile(afterRevision: ContentRevision): VirtualFile? {
        return afterRevision.file.virtualFile
            ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(afterRevision.file.path)
            ?: runCatching { ContentRevisionVirtualFile.create(afterRevision) }
                .onFailure { logger.warn("Failed to create VcsVirtualFile for comparison file: ${afterRevision.file.path}", it) }
                .getOrNull()
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



    fun getFileContentForRevision(revision: String, file: VirtualFile): String? {
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

internal fun mergeUnsavedOverlayChange(existingChange: Change?, unsavedChange: Change): Change {
    if (existingChange?.type == Change.Type.NEW && unsavedChange.afterRevision != null) {
        return Change(null, unsavedChange.afterRevision, FileStatus.ADDED)
    }

    return unsavedChange
}