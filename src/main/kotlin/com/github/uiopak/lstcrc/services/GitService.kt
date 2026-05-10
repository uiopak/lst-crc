package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
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
import git4idea.GitUtil
import git4idea.util.GitFileUtils
import java.nio.charset.Charset


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
    val comparisonContext: Map<String, String>,
    val lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
)

data class ChangeLineStats(
    val addedLines: Int,
    val removedLines: Int
)

data class ChangeLineStatsKey(
    val beforePath: String?,
    val afterPath: String?
) {
    companion object {
        fun from(change: Change): ChangeLineStatsKey = ChangeLineStatsKey(
            beforePath = normalizePath(change.beforeRevision?.file?.path),
            afterPath = normalizePath(change.afterRevision?.file?.path)
        )

        fun fromPaths(beforePath: String?, afterPath: String?): ChangeLineStatsKey = ChangeLineStatsKey(
            beforePath = normalizePath(beforePath),
            afterPath = normalizePath(afterPath)
        )

        private fun normalizePath(path: String?): String? = path?.replace('\\', '/')
    }
}

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
        private const val DIFF_FILTER_PARAM = "--diff-filter=ADCMRUXT"
    }

    private val logger = thisLogger()

    private data class ParsedDiffStatus(
        val changeType: Change.Type,
        val fileStatus: FileStatus
    )

    private data class ChangeLoadContext(
        val allChanges: MutableList<Change> = mutableListOf(),
        val comparisonContext: MutableMap<String, String> = mutableMapOf(),
        val lineStatsByChange: MutableMap<ChangeLineStatsKey, ChangeLineStats> = linkedMapOf(),
        val failures: MutableMap<GitRepository, String> = mutableMapOf()
    )

    private data class LoadedChanges(
        val changes: List<Change>,
        val lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
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

    suspend fun getChanges(
        tabInfo: TabInfo?,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): GetChangesResult {
        val repositories = getRepositories()
        val profileName = tabInfo?.branchName ?: "HEAD"

        logger.debug("getChanges called for profile: $profileName")

        if (repositories.isEmpty()) {
            return GetChangesResult(
                CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap()),
                emptyMap()
            )
        }

        return withBackgroundProgress(project, LstCrcBundle.message("git.task.loading.changes")) {
            withContext(dispatcher) {
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
                val loadedChanges = loadLocalChanges(repo)
                context.allChanges.addAll(loadedChanges.changes)
                context.lineStatsByChange.putAll(loadedChanges.lineStatsByChange)
                context.comparisonContext[repo.root.path] = "HEAD"
            }
        } else {
            val primaryRevision = tabInfo.branchName
            for (repo in repositories) {
                val target = tabInfo.comparisonMap[repo.root.path] ?: primaryRevision
                context.comparisonContext[repo.root.path] = target
                logger.debug("Repo '${repo.root.path}': using target '$target'")
                val loadedChanges = loadChangesForTarget(repo, primaryRevision, target, context.failures)
                context.allChanges.addAll(loadedChanges.changes)
                context.lineStatsByChange.putAll(loadedChanges.lineStatsByChange)
            }
        }
        return GetChangesResult(buildCategorizedChanges(context.allChanges, context.comparisonContext, context.lineStatsByChange), context.failures)
    }

    private fun loadChangesForTarget(
        repo: GitRepository,
        primaryRevision: String,
        target: String,
        failures: MutableMap<GitRepository, String>
    ): LoadedChanges {
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
                    "git diff (working tree) failed for repo '${repo.root.name}' against target '$target'. " +
                        "Assuming revision is invalid. Error: ${e.message}"
                )
                failures[repo] = target
                LoadedChanges(emptyList(), emptyMap())
            }
        }

        return try {
            loadTrackedChangesBetweenRevisions(repo, primaryRevision, target)
        } catch (e: VcsException) {
            logger.warn(
                "git diff (between revisions) failed for repo '${repo.root.name}' against target '$target'. " +
                    "Assuming revision is invalid. Error: ${e.message}"
            )
            failures[repo] = target
            LoadedChanges(emptyList(), emptyMap())
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
        return GitUtil.isHashString(target, false)
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun revisionExistsInRepo(repo: GitRepository, revision: String): Boolean {
        val handler = GitLineHandler(project, repo.root, GitCommand.REV_PARSE)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters("--verify", "--quiet", revision)
        return Git.getInstance().runCommand(handler).exitCode == 0
    }

    private fun loadChangesAgainstWorkingTree(repo: GitRepository, target: String): LoadedChanges {
        val trackedChanges = loadTrackedChangesAgainstWorkingTree(repo, target)
        val untrackedChanges = loadOptionalUntrackedChanges(repo)
        val unsavedChanges = collectUnsavedDocumentChanges(repo, target)
        val allChanges = overlayUnsavedDocumentChanges(trackedChanges.changes + untrackedChanges, unsavedChanges)
        return LoadedChanges(
            changes = allChanges,
            lineStatsByChange = buildLineStats(
                changes = allChanges,
                trackedLineStats = trackedChanges.lineStatsByChange,
                forceRecompute = unsavedChanges.mapTo(linkedSetOf()) { ChangeLineStatsKey.from(it) }
            )
        )
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun loadTrackedChangesAgainstWorkingTree(repo: GitRepository, target: String): LoadedChanges {
        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters("--name-status", DIFF_FILTER_PARAM, "-M", target)

        val output = Git.getInstance().runCommand(handler).getOutputOrThrow()
        val targetRevision = GitRevisionNumber(target)

        val changes = output.lineSequence()
            .mapNotNull { parseWorkingTreeDiffLine(repo, targetRevision, it) }
            .toList()

        return LoadedChanges(
            changes = changes,
            lineStatsByChange = loadTrackedLineStats(repo, changes, target)
        )
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun loadTrackedChangesBetweenRevisions(
        repo: GitRepository,
        baseRevision: String,
        targetRevision: String
    ): LoadedChanges {
        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters("--name-status", DIFF_FILTER_PARAM, "-M", baseRevision, targetRevision)

        val output = Git.getInstance().runCommand(handler).getOutputOrThrow()
        val beforeRevision = GitRevisionNumber(baseRevision)
        val afterRevision = GitRevisionNumber(targetRevision)

        val changes = output.lineSequence()
            .mapNotNull { parseRevisionDiffLine(repo, beforeRevision, afterRevision, it) }
            .toList()

        return LoadedChanges(
            changes = changes,
            lineStatsByChange = loadTrackedLineStats(repo, changes, baseRevision, targetRevision)
        )
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun loadTrackedLineStats(
        repo: GitRepository,
        changes: List<Change>,
        vararg revisions: String
    ): Map<ChangeLineStatsKey, ChangeLineStats> {
        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters("--numstat", DIFF_FILTER_PARAM, "-M")
        handler.addParameters(*revisions)

        val output = Git.getInstance().runCommand(handler).getOutputOrThrow()
        return parseTrackedLineStats(repo, changes, output.lineSequence())
    }

    private fun parseTrackedLineStats(
        repo: GitRepository,
        changes: List<Change>,
        lines: Sequence<String>
    ): Map<ChangeLineStatsKey, ChangeLineStats> {
        if (changes.isEmpty()) return emptyMap()

        val renameLookup = changes.associate { change ->
            val key = ChangeLineStatsKey.from(change)
            key to key
        }
        val afterPathLookup = changes
            .mapNotNull { change ->
                val afterPath = ChangeLineStatsKey.from(change).afterPath ?: return@mapNotNull null
                afterPath to ChangeLineStatsKey.from(change)
            }
            .toMap()
        val beforePathLookup = changes
            .mapNotNull { change ->
                val beforePath = ChangeLineStatsKey.from(change).beforePath ?: return@mapNotNull null
                beforePath to ChangeLineStatsKey.from(change)
            }
            .toMap()

        return lines.mapNotNull { line ->
            parseTrackedLineStat(repo, line, renameLookup, afterPathLookup, beforePathLookup)
        }.toMap(linkedMapOf())
    }

    private fun parseTrackedLineStat(
        repo: GitRepository,
        line: String,
        renameLookup: Map<ChangeLineStatsKey, ChangeLineStatsKey>,
        afterPathLookup: Map<String, ChangeLineStatsKey>,
        beforePathLookup: Map<String, ChangeLineStatsKey>
    ): Pair<ChangeLineStatsKey, ChangeLineStats>? {
        if (line.isBlank()) return null

        val tokens = line.split('\t')
        if (tokens.size < 3) return null

        val addedLines = tokens[0].toIntOrNull() ?: return null
        val removedLines = tokens[1].toIntOrNull() ?: return null
        val key = if (tokens.size >= 4) {
            val beforePath = GitContentRevision.createPathFromEscaped(repo.root, tokens[2]).path
            val afterPath = GitContentRevision.createPathFromEscaped(repo.root, tokens[3]).path
            renameLookup[ChangeLineStatsKey.fromPaths(beforePath, afterPath)]
        } else {
            val path = GitContentRevision.createPathFromEscaped(repo.root, tokens[2]).path
            afterPathLookup[path.replace('\\', '/')] ?: beforePathLookup[path.replace('\\', '/')]
        } ?: return null

        return key to ChangeLineStats(addedLines = addedLines, removedLines = removedLines)
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
        comparisonContext: Map<String, String>,
        lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
    ): CategorizedChanges {
        val created = allChanges.filter { it.beforeRevision == null && it.afterRevision != null }
            .mapNotNull { createComparisonVirtualFile(it.afterRevision!!) }
            .distinct()

        val deleted = allChanges.filter { it.beforeRevision != null && it.afterRevision == null }
            .mapNotNull { createDeletedVirtualFile(it.beforeRevision!!) }
            .distinct()

        val movedAndModified = allChanges.filter { it.beforeRevision != null && it.afterRevision != null }

        val moved = movedAndModified.filter { it.beforeRevision!!.file.path != it.afterRevision!!.file.path }
            .mapNotNull { createComparisonVirtualFile(it.afterRevision!!) }
            .distinct()

        val modified = movedAndModified.filter { it.beforeRevision!!.file.path == it.afterRevision!!.file.path }
            .mapNotNull { createComparisonVirtualFile(it.afterRevision!!) }
            .distinct()

        return CategorizedChanges(
            allChanges = allChanges.distinct(),
            createdFiles = created,
            modifiedFiles = modified,
            movedFiles = moved,
            deletedFiles = deleted,
            comparisonContext = comparisonContext,
            lineStatsByChange = lineStatsByChange
        )
    }

    private fun loadLocalChanges(repo: GitRepository): LoadedChanges {
        val trackedChanges = loadTrackedChangesAgainstHead(repo)
        val untrackedChanges = loadOptionalUntrackedChanges(repo)
        val unsavedChanges = collectUnsavedDocumentChanges(repo, "HEAD")
        val allChanges = overlayUnsavedDocumentChanges(trackedChanges.changes + untrackedChanges, unsavedChanges)
        return LoadedChanges(
            changes = allChanges,
            lineStatsByChange = buildLineStats(
                changes = allChanges,
                trackedLineStats = trackedChanges.lineStatsByChange,
                forceRecompute = unsavedChanges.mapTo(linkedSetOf()) { ChangeLineStatsKey.from(it) }
            )
        )
    }

    private fun loadOptionalUntrackedChanges(repo: GitRepository): List<Change> {
        return if (ToolWindowSettingsProvider.isShowUntrackedFilesAsNew()) {
            loadUntrackedChanges(repo)
        } else {
            emptyList()
        }
    }

    private fun loadTrackedChangesAgainstHead(repo: GitRepository): LoadedChanges {
        repo.update()

        if (repo.isFresh) {
            return LoadedChanges(emptyList(), emptyMap())
        }

        return try {
            val changes = GitChangeUtils.getDiffWithWorkingDir(project, repo.root, "HEAD", null, false, true).toList()
            LoadedChanges(
                changes = changes,
                lineStatsByChange = buildLineStats(changes, emptyMap(), emptySet())
            )
        } catch (e: VcsException) {
            logger.warn("Failed to load tracked local changes for repo '${repo.root.name}' against HEAD: ${e.message}")
            LoadedChanges(emptyList(), emptyMap())
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
        baseChanges: List<Change>,
        unsavedChanges: List<Change>
    ): List<Change> {
        val mergedChanges = LinkedHashMap<String, Change>()
        baseChanges.forEach { change ->
            mergedChanges[unsavedOverlayKey(change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path.orEmpty())] = change
        }

        unsavedChanges.forEach { change ->
            val path = change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path.orEmpty()
            val key = unsavedOverlayKey(path)
            mergedChanges[key] = mergeUnsavedOverlayChange(mergedChanges[key], change)
        }

        return mergedChanges.values.toList()
    }

    private fun buildLineStats(
        changes: List<Change>,
        trackedLineStats: Map<ChangeLineStatsKey, ChangeLineStats>,
        forceRecompute: Set<ChangeLineStatsKey>
    ): Map<ChangeLineStatsKey, ChangeLineStats> {
        val lineStats = linkedMapOf<ChangeLineStatsKey, ChangeLineStats>()
        val keys = changes.map(ChangeLineStatsKey::from).toSet()

        trackedLineStats.forEach { (key, stats) ->
            if (key in keys && key !in forceRecompute) {
                lineStats[key] = stats
            }
        }

        changes.forEach { change ->
            val key = ChangeLineStatsKey.from(change)
            if (key in forceRecompute || key !in lineStats) {
                computeFallbackLineStats(change)?.let { lineStats[key] = it }
            }
        }

        return lineStats
    }

    private fun computeFallbackLineStats(change: Change): ChangeLineStats? {
        val beforeContent = change.beforeRevision?.content ?: ""
        val afterContent = change.afterRevision?.content ?: ""

        return runCatching {
            calculateLineStats(beforeContent, afterContent)
        }.getOrElse { error ->
            logger.debug("Failed to compute fallback line stats for '${change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path}'.", error)
            null
        }
    }

    private fun collectUnsavedDocumentChanges(repo: GitRepository, targetRevision: String): List<Change> {
        val fileDocumentManager = FileDocumentManager.getInstance()
        val unsavedFiles = ApplicationManager.getApplication().runReadAction<List<VirtualFile>> {
            fileDocumentManager.unsavedDocuments.asSequence()
                .mapNotNull { document -> fileDocumentManager.getFile(document) }
                .filter { file ->
                    file.isValid &&
                        VfsUtilCore.isAncestor(repo.root, file, false) &&
                        fileDocumentManager.isFileModified(file)
                }
                .toList()
        }

        return unsavedFiles.asSequence()
            .mapNotNull { file -> createUnsavedDocumentChange(repo, file, targetRevision) }
            .toList()
    }

    private fun createUnsavedDocumentChange(repo: GitRepository, file: VirtualFile, targetRevision: String): Change? {
        return try {
            val beforeRevision = createTargetContentRevision(project, repo, file, targetRevision) ?: return null
            val afterRevision = createLiveDocumentContentRevision(file)
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

        val normalizedContent = loadRevisionTextContent(project, repository.root, revision, relativePath, file.charset)

        logger.info("GUTTER_GIT_SERVICE: Successfully fetched content for '${relativePath}' in revision '${revision}'.")
        return normalizedContent
    }
}

internal fun calculateLineStats(beforeContent: String, afterContent: String): ChangeLineStats {
    val fragments = ComparisonManager.getInstance().compareLines(
        beforeContent,
        afterContent,
        ComparisonPolicy.DEFAULT,
        DumbProgressIndicator.INSTANCE
    ).toList()
    return ChangeLineStats(
        addedLines = fragments.sumOf { it.endLine2 - it.startLine2 },
        removedLines = fragments.sumOf { it.endLine1 - it.startLine1 }
    )
}

internal fun createLiveDocumentContentRevision(file: VirtualFile): ContentRevision {
    val filePath = VcsUtil.getFilePath(file)
    val content = ApplicationManager.getApplication().runReadAction<String> {
        FileDocumentManager.getInstance().getDocument(file)?.immutableCharSequence?.toString()
            ?: VfsUtilCore.loadText(file)
    }

    return object : ContentRevision {
        override fun getFile(): com.intellij.openapi.vcs.FilePath = filePath

        override fun getContent(): String = content

        override fun getRevisionNumber(): VcsRevisionNumber = object : VcsRevisionNumber {
            override fun asString(): String = "LOCAL"

            override fun compareTo(other: VcsRevisionNumber): Int = 0
        }
    }
}

internal fun createTargetContentRevision(
    project: Project,
    repo: GitRepository,
    file: VirtualFile,
    revision: String
): ContentRevision? {
    val filePath = VcsUtil.getFilePath(file)
    val relativePath = VfsUtilCore.getRelativePath(file, repo.root, '/') ?: return null
    val content = runCatching {
        loadRevisionTextContent(project, repo.root, revision, relativePath, file.charset)
    }.getOrElse {
        return null
    }

    return object : ContentRevision {
        override fun getFile(): com.intellij.openapi.vcs.FilePath = filePath

        override fun getContent(): String = content

        override fun getRevisionNumber(): VcsRevisionNumber = GitRevisionNumber(revision)
    }
}

internal fun loadRevisionTextContent(
    project: Project,
    repoRoot: VirtualFile,
    revision: String,
    relativePath: String,
    charset: Charset
): String {
    val revisionContentBytes = GitFileUtils.getFileContent(project, repoRoot, revision, relativePath)
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
            it.reader(charset).readText()
        }

    // The IntelliJ Document model requires LF ('\n') line endings, but Git on Windows might return CRLF ('\r\n').
    return StringUtil.convertLineSeparators(rawContent)
}

internal fun mergeUnsavedOverlayChange(existingChange: Change?, unsavedChange: Change): Change {
    if (existingChange?.type == Change.Type.NEW && unsavedChange.afterRevision != null) {
        return Change(null, unsavedChange.afterRevision, FileStatus.ADDED)
    }

    return unsavedChange
}