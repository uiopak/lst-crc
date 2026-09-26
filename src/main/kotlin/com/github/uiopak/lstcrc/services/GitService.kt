package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.utils.isCommitHash
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
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
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

private const val DIFF_FILTER_PARAM = "--diff-filter=ADCMRUXT"
private const val IGNORE_CR_AT_EOL_PARAM = "--ignore-cr-at-eol"

/** Revision contents kept by [GitService]; larger files are loaded every time. */
private const val REVISION_CONTENT_CACHE_ENTRIES = 64
private const val REVISION_CONTENT_CACHE_MAX_CHARS = 512 * 1024


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
    val lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>,
    /** False when the load skipped line stats because "Show line stats" was off. */
    val lineStatsIncluded: Boolean = true
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

    private val logger = thisLogger()

    private data class ParsedDiffStatus(
        val changeType: Change.Type,
        val fileStatus: FileStatus
    )

    private data class RevisionContentKey(
        val root: String,
        val commitHash: String,
        val relativePath: String,
        val charset: Charset
    )

    /**
     * File contents at a commit. Refreshes while typing (the unsaved-edit overlay) and gutter loads
     * ask for the same content again and again, and every miss runs `git show`. Entries are keyed by
     * commit hash rather than branch name, so they never go stale.
     */
    private val revisionContentCache =
        object : LinkedHashMap<RevisionContentKey, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RevisionContentKey, String>): Boolean =
                size > REVISION_CONTENT_CACHE_ENTRIES
        }

    /** What decides a repository's changes on disk, apart from the files themselves. */
    private data class DiskChangesKey(
        val target: String,
        val includeLineStats: Boolean,
        val includeUntracked: Boolean
    )

    /**
     * A repository's changes before unsaved edits are overlaid: tracked changes against the target,
     * untracked files, and their line stats. [overlayTarget] is the revision unsaved edits compare to.
     */
    private data class DiskChanges(
        val key: DiskChangesKey,
        val loaded: LoadedChanges,
        val overlayTarget: String
    )

    /**
     * The last [DiskChanges] per repository root. A refresh caused only by unsaved edits
     * (`reuseDiskChanges`) reuses them instead of running `git diff` and `git ls-files` again,
     * because nothing changed on disk; every other refresh reloads and replaces them.
     */
    private val lastDiskChanges = ConcurrentHashMap<String, DiskChanges>()

    private data class LoadedChanges(
        val changes: List<Change>,
        val lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
    ) {
        companion object {
            val EMPTY = LoadedChanges(emptyList(), emptyMap())
        }
    }

    internal fun getRepositoryForFile(file: VirtualFile): GitRepository? =
        GitRepositoryManager.getInstance(project).getRepositoryForFile(file)

    fun getRepositories(): List<GitRepository> = GitRepositoryManager.getInstance(project).repositories

    /**
     * Gets the "primary" repository for the project. This is useful for context where a single
     * repository is needed (e.g., startup). The logic prefers the repository containing the
     * project's base directory.
     */
    internal fun getPrimaryRepository(): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories
        if (repositories.isEmpty()) return null
        if (repositories.size == 1) return repositories.first()

        val projectBasePath = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        if (projectBasePath != null) {
            repositoryManager.getRepositoryForFile(projectBasePath)?.let { return it }
        }

        logger.warn("Multiple Git repositories found, but none contains the project base path. Using the first one: ${repositories.first().root.path}")
        return repositories.first()
    }

    fun getBranchSnapshot(repository: GitRepository?): BranchSnapshot {
        val repo = repository ?: getPrimaryRepository() ?: run {
            logger.debug { "getBranchSnapshot() called with no repository available." }
            return BranchSnapshot(emptyList(), emptyList())
        }
        repo.update()
        val branches = repo.branches
        val snapshot = BranchSnapshot(
            branches.localBranches.map { it.name },
            branches.remoteBranches.map { it.name }
        )
        logger.debug {
            "Loaded branch snapshot for repo '${repo.root.name}': " +
                "${snapshot.localBranches.size} local, ${snapshot.remoteBranches.size} remote branches."
        }
        return snapshot
    }

    /**
     * Loads the comparison for [tabInfo] (`HEAD` when null). With [reuseDiskChanges] (the refresh was
     * caused only by unsaved edits) the last `git diff` result of each repository is reused when its
     * target and settings still match, and only the unsaved-edit overlay is rebuilt.
     */
    suspend fun getChanges(
        tabInfo: TabInfo?,
        reuseDiskChanges: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): GetChangesResult {
        val repositories = getRepositories()
        val profileName = tabInfo?.branchName ?: "HEAD"

        logger.debug { "getChanges called for profile: $profileName" }

        if (repositories.isEmpty()) {
            return GetChangesResult(
                CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap()),
                emptyMap()
            )
        }

        // Line stats cost an extra `git diff --numstat` per repository plus in-process diffs of
        // untracked/unsaved files, so they are only computed while the tree shows them.
        val includeLineStats = ToolWindowSettingsProvider.isShowLineStatsInTree()
        return withBackgroundProgress(project, LstCrcBundle.message("git.task.loading.changes")) {
            withContext(dispatcher) {
                loadChangesResult(repositories, tabInfo, includeLineStats, reuseDiskChanges)
            }
        }
    }

    fun resolveComparisonTarget(repo: GitRepository, tabInfo: TabInfo?): String {
        if (tabInfo == null) return "HEAD"
        return tabInfo.comparisonMap[repo.root.path] ?: tabInfo.branchName
    }

    private fun loadChangesResult(
        repositories: List<GitRepository>,
        tabInfo: TabInfo?,
        includeLineStats: Boolean,
        reuseDiskChanges: Boolean
    ): GetChangesResult {
        val allChanges = mutableListOf<Change>()
        val comparisonContext = mutableMapOf<String, String>()
        val lineStatsByChange = linkedMapOf<ChangeLineStatsKey, ChangeLineStats>()
        // Only comparison tabs report missing targets; the HEAD tab never has one.
        val failures = if (tabInfo == null) null else mutableMapOf<GitRepository, String>()
        for (repo in repositories) {
            val target = resolveComparisonTarget(repo, tabInfo)
            comparisonContext[repo.root.path] = target
            logger.debug { "Repo '${repo.root.path}': using target '$target'" }
            val loadedChanges = loadChanges(repo, target, includeLineStats, failures, reuseDiskChanges)
            allChanges.addAll(loadedChanges.changes)
            lineStatsByChange.putAll(loadedChanges.lineStatsByChange)
        }
        val categorizedChanges = buildCategorizedChanges(allChanges, comparisonContext, lineStatsByChange)
        return GetChangesResult(categorizedChanges.copy(lineStatsIncluded = includeLineStats), failures.orEmpty())
    }

    /**
     * Compares the working tree (including untracked files and unsaved documents) of [repo]
     * against [target] (HEAD for the HEAD tab). A per-repository override only changes the target;
     * it is never a commit-to-commit comparison, so the tree matches what the gutter markers show.
     *
     * If `git diff` fails, a comparison tab records [target] in [failures] and shows nothing for the
     * repository; the HEAD tab ([failures] == null) still shows untracked files and unsaved edits.
     */
    private fun loadChanges(
        repo: GitRepository,
        target: String,
        includeLineStats: Boolean,
        failures: MutableMap<GitRepository, String>?,
        reuseDiskChanges: Boolean
    ): LoadedChanges {
        val key = DiskChangesKey(target, includeLineStats, ToolWindowSettingsProvider.isShowUntrackedFilesAsNew())
        val diskChanges = lastDiskChanges[repo.root.path]?.takeIf { reuseDiskChanges && it.key == key }
            ?: loadDiskChanges(repo, key, failures)
            ?: return LoadedChanges.EMPTY
        return overlayUnsavedDocuments(repo, diskChanges)
    }

    /**
     * Runs git for [repo]'s changes on disk and remembers them for [loadChanges]. Returns null when a
     * comparison tab's target cannot be resolved (recorded in [failures]).
     */
    private fun loadDiskChanges(
        repo: GitRepository,
        key: DiskChangesKey,
        failures: MutableMap<GitRepository, String>?
    ): DiskChanges? {
        repo.update()
        var cacheable = true
        val trackedChanges = if (repo.isFresh) {
            logger.debug { "Repo '${repo.root.name}' is fresh. Showing only untracked files and unsaved edits for target '${key.target}'." }
            LoadedChanges.EMPTY
        } else {
            try {
                loadTrackedChangesAgainstWorkingTree(repo, key.target, key.includeLineStats)
            } catch (e: VcsException) {
                logger.warn("git diff failed for repo '${repo.root.name}' against target '${key.target}': ${e.message}")
                if (failures != null) {
                    failures[repo] = key.target
                    lastDiskChanges.remove(repo.root.path)
                    return null
                }
                cacheable = false
                LoadedChanges.EMPTY
            }
        }

        val untrackedChanges = if (key.includeUntracked) loadUntrackedChanges(repo) else emptyList()
        val changes = trackedChanges.changes + untrackedChanges
        val loaded = LoadedChanges(
            changes = changes,
            lineStatsByChange = if (key.includeLineStats) buildLineStats(changes, trackedChanges.lineStatsByChange, emptySet()) else emptyMap()
        )
        val diskChanges = DiskChanges(key, loaded, overlayTarget = if (repo.isFresh) "HEAD" else key.target)
        if (cacheable) lastDiskChanges[repo.root.path] = diskChanges else lastDiskChanges.remove(repo.root.path)
        return diskChanges
    }

    private fun loadTrackedChangesAgainstWorkingTree(repo: GitRepository, target: String, includeLineStats: Boolean): LoadedChanges {
        val targetRevision = GitRevisionNumber(target)
        val changes = runGitDiff(repo, "--name-status", DIFF_FILTER_PARAM, "-M", target)
            .lineSequence()
            .mapNotNull { parseDiffLine(repo, targetRevision, it) }
            .toList()

        return LoadedChanges(
            changes = changes,
            lineStatsByChange = if (includeLineStats) loadTrackedLineStats(repo, changes, target) else emptyMap()
        )
    }

    private fun loadTrackedLineStats(
        repo: GitRepository,
        changes: List<Change>,
        target: String
    ): Map<ChangeLineStatsKey, ChangeLineStats> {
        if (changes.isEmpty()) return emptyMap()
        val output = runGitDiff(repo, *trackedLineStatsDiffArgs(target).toTypedArray())
        return parseTrackedLineStats(repo, changes, output)
    }

    /** Runs a silent `git diff` in [repo] and returns its stdout, throwing [VcsException] on failure. */
    @Suppress("UsePropertyAccessSyntax")
    private fun runGitDiff(repo: GitRepository, vararg params: String): String {
        val handler = GitLineHandler(project, repo.root, GitCommand.DIFF)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        handler.addParameters(*params)
        return Git.getInstance().runCommand(handler).getOutputOrThrow()
    }

    /**
     * Parses `git diff --numstat -z` output. Each record is `added<TAB>removed<TAB>path<NUL>`, or for
     * renames `added<TAB>removed<TAB><NUL>old<NUL>new<NUL>`; binary files report `-` counts and are
     * skipped. Paths are unquoted with `-z`, so renames map to their [Change] instead of falling back
     * to an in-process diff (without `-z` git writes them as `dir/{old => new}`).
     */
    private fun parseTrackedLineStats(
        repo: GitRepository,
        changes: List<Change>,
        output: String
    ): Map<ChangeLineStatsKey, ChangeLineStats> {
        val keys = changes.mapTo(LinkedHashSet(), ChangeLineStatsKey::from)
        val byAfterPath = keys.filter { it.afterPath != null }.associateBy { it.afterPath!! }
        val byBeforePath = keys.filter { it.beforePath != null }.associateBy { it.beforePath!! }
        val root = repo.root.path

        val result = linkedMapOf<ChangeLineStatsKey, ChangeLineStats>()
        val fields = output.split('\u0000').iterator()
        while (fields.hasNext()) {
            val tokens = fields.next().trim('\n').split('\t')
            if (tokens.size < 3) continue
            val key = if (tokens[2].isEmpty()) {
                // Rename or copy: the old and new paths follow as separate fields.
                val oldPath = if (fields.hasNext()) fields.next() else break
                val newPath = if (fields.hasNext()) fields.next() else break
                ChangeLineStatsKey.fromPaths("$root/$oldPath", "$root/$newPath").takeIf { it in keys }
            } else {
                val path = "$root/${tokens[2]}".replace('\\', '/')
                byAfterPath[path] ?: byBeforePath[path]
            } ?: continue
            val addedLines = tokens[0].toIntOrNull() ?: continue
            val removedLines = tokens[1].toIntOrNull() ?: continue
            result[key] = ChangeLineStats(addedLines = addedLines, removedLines = removedLines)
        }
        return result
    }

    /**
     * Parses one `git diff --name-status <target>` line. The "before" side is [targetRevision],
     * the "after" side is the current working tree.
     */
    private fun parseDiffLine(repo: GitRepository, targetRevision: GitRevisionNumber, line: String): Change? {
        val tokens = line.split('\t')
        val parsedStatus = parseDiffStatus(tokens.first()) ?: return null
        val requiredTokens = if (parsedStatus.changeType == Change.Type.MOVED) 3 else 2
        if (tokens.size < requiredTokens) return null

        fun path(index: Int) = GitContentRevision.createPathFromEscaped(repo.root, tokens[index])
        fun before(path: FilePath) = GitContentRevision.createRevision(path, targetRevision, project)
        fun after(path: FilePath) = GitContentRevision.createRevision(path, null, project)

        return when (parsedStatus.changeType) {
            Change.Type.NEW -> Change(null, after(path(1)), parsedStatus.fileStatus)
            Change.Type.DELETED -> Change(before(path(1)), null, parsedStatus.fileStatus)
            Change.Type.MODIFICATION -> path(1).let { Change(before(it), after(it), parsedStatus.fileStatus) }
            Change.Type.MOVED -> Change(before(path(1)), after(path(2)), parsedStatus.fileStatus)
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

    /** Splits changes into created/modified/moved/deleted files in one pass. */
    private fun buildCategorizedChanges(
        allChanges: List<Change>,
        comparisonContext: Map<String, String>,
        lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
    ): CategorizedChanges {
        val created = LinkedHashSet<VirtualFile>()
        val modified = LinkedHashSet<VirtualFile>()
        val moved = LinkedHashSet<VirtualFile>()
        val deleted = LinkedHashSet<VirtualFile>()
        for (change in allChanges) {
            val before = change.beforeRevision
            val after = change.afterRevision
            when {
                before == null && after != null -> createComparisonVirtualFile(after)?.let(created::add)
                before != null && after == null -> createDeletedVirtualFile(before)?.let(deleted::add)
                before != null && after != null -> {
                    val target = if (before.file.path == after.file.path) modified else moved
                    createComparisonVirtualFile(after)?.let(target::add)
                }
            }
        }

        return CategorizedChanges(
            allChanges = allChanges.distinct(),
            createdFiles = created.toList(),
            modifiedFiles = modified.toList(),
            movedFiles = moved.toList(),
            deletedFiles = deleted.toList(),
            comparisonContext = comparisonContext,
            lineStatsByChange = lineStatsByChange
        )
    }

    /** Overlays [repo]'s unsaved documents on [diskChanges] and adds line stats for the edited files. */
    private fun overlayUnsavedDocuments(repo: GitRepository, diskChanges: DiskChanges): LoadedChanges {
        val unsavedChanges = collectUnsavedDocumentChanges(repo, diskChanges.overlayTarget)
        val allChanges = overlayUnsavedDocumentChanges(diskChanges.loaded.changes, unsavedChanges)
        if (!diskChanges.key.includeLineStats) return LoadedChanges(allChanges, emptyMap())
        return LoadedChanges(
            changes = allChanges,
            lineStatsByChange = buildLineStats(
                changes = allChanges,
                trackedLineStats = diskChanges.loaded.lineStatsByChange,
                forceRecompute = unsavedChanges.mapTo(linkedSetOf()) { ChangeLineStatsKey.from(it) },
                knownKeys = diskChanges.loaded.changes.mapTo(HashSet()) { ChangeLineStatsKey.from(it) }
            )
        )
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
        // FilePath equality follows the file system's case sensitivity.
        val mergedChanges = LinkedHashMap<FilePath, Change>()
        baseChanges.forEach { change -> mergedChanges[ChangesUtil.getFilePath(change)] = change }

        unsavedChanges.forEach { change ->
            val key = ChangesUtil.getFilePath(change)
            mergedChanges[key] = mergeUnsavedOverlayChange(mergedChanges[key], change)
        }

        return mergedChanges.values.toList()
    }

    /**
     * Line stats for [changes]: taken from [trackedLineStats] where present, otherwise computed in
     * process. Keys in [forceRecompute] (unsaved edits) are always computed; keys in [knownKeys] were
     * already handled when [trackedLineStats] was built, so a failed computation is not retried.
     */
    private fun buildLineStats(
        changes: List<Change>,
        trackedLineStats: Map<ChangeLineStatsKey, ChangeLineStats>,
        forceRecompute: Set<ChangeLineStatsKey>,
        knownKeys: Set<ChangeLineStatsKey> = emptySet()
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
            if (key in forceRecompute || (key !in lineStats && key !in knownKeys)) {
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
            logger.debug(error) { "Failed to compute fallback line stats for '${change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path}'." }
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
            val beforeRevision = createTargetContentRevision(repo, file, targetRevision) ?: return null
            val afterRevision = createLiveDocumentContentRevision(file)
            Change(beforeRevision, afterRevision, FileStatus.MODIFIED)
        } catch (e: Exception) {
            logger.warn("Failed to create unsaved document change for '${file.path}' against '$targetRevision'", e)
            null
        }
    }

    private fun createComparisonVirtualFile(afterRevision: ContentRevision): VirtualFile? {
        return afterRevision.file.virtualFile
            ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(afterRevision.file.path)
            ?: runCatching { ContentRevisionVirtualFile.create(afterRevision) }
                .onFailure { logger.warn("Failed to create VcsVirtualFile for comparison file: ${afterRevision.file.path}", it) }
                .getOrNull()
    }

    private fun createDeletedVirtualFile(beforeRevision: ContentRevision): VirtualFile? {
        return runCatching { ContentRevisionVirtualFile.create(beforeRevision) }
            .onFailure { logger.warn("Failed to create VcsVirtualFile for deleted file: ${beforeRevision.file.path}", it) }
            .getOrNull()
    }

    fun getFileContentForRevision(revision: String, file: VirtualFile): String? {
        val repository = getRepositoryForFile(file)

        if (repository == null) {
            logger.warn("Cannot get file content for revision '$revision' for file '${file.path}', no repository found for this file.")
            return null
        }

        logger.debug { "GUTTER_GIT_SERVICE: Preparing to fetch content for revision:'${revision}' file:'${file.path}'" }

        val relativePath = VfsUtilCore.getRelativePath(file, repository.root, '/')
            ?: throw IllegalStateException("Could not calculate relative path for file '${file.path}' against repo root '${repository.root.path}'.")

        val normalizedContent = loadRevisionText(repository, revision, relativePath, file.charset)

        logger.debug { "GUTTER_GIT_SERVICE: Successfully fetched content for '${relativePath}' in revision '${revision}'." }
        return normalizedContent
    }

    /**
     * [relativePath]'s text at [revision], with LF line endings. Throws [VcsException] when git fails,
     * for example when the file does not exist in that revision; failures are not cached.
     */
    private fun loadRevisionText(repo: GitRepository, revision: String, relativePath: String, charset: Charset): String {
        val commitHash = resolveCommitHash(repo, revision)
            ?: return loadRevisionTextContent(project, repo.root, revision, relativePath, charset)
        val key = RevisionContentKey(repo.root.path, commitHash, relativePath, charset)
        synchronized(revisionContentCache) { revisionContentCache[key] }?.let { return it }

        val content = loadRevisionTextContent(project, repo.root, commitHash, relativePath, charset)
        if (content.length <= REVISION_CONTENT_CACHE_MAX_CHARS) {
            synchronized(revisionContentCache) { revisionContentCache[key] = content }
        }
        return content
    }

    /** [file]'s content at [revision] as a [ContentRevision], or null when it cannot be loaded. */
    private fun createTargetContentRevision(repo: GitRepository, file: VirtualFile, revision: String): ContentRevision? {
        val relativePath = VfsUtilCore.getRelativePath(file, repo.root, '/') ?: return null
        val content = runCatching { loadRevisionText(repo, revision, relativePath, file.charset) }.getOrElse { return null }
        val filePath = VcsUtil.getFilePath(file)

        return object : ContentRevision {
            override fun getFile(): FilePath = filePath

            override fun getContent(): String = content

            override fun getRevisionNumber(): VcsRevisionNumber = GitRevisionNumber(revision)
        }
    }
}

/**
 * The commit [revision] points to, read from Git4Idea's in-memory repository state (no git call), or
 * null when that state cannot tell: tags, abbreviated hashes, a repository without commits.
 */
internal fun resolveCommitHash(repo: GitRepository, revision: String): String? {
    if (revision == "HEAD") return repo.currentRevision
    val branches = repo.branches
    branches.findBranchByName(revision)?.let { return branches.getHash(it)?.asString() }
    return revision.takeIf { it.length == 40 && isCommitHash(it) }
}

internal fun calculateLineStats(beforeContent: String, afterContent: String): ChangeLineStats {
    val normalizedBeforeContent = StringUtil.convertLineSeparators(beforeContent)
    val normalizedAfterContent = StringUtil.convertLineSeparators(afterContent)
    val fragments = ComparisonManager.getInstance().compareLines(
        normalizedBeforeContent,
        normalizedAfterContent,
        ComparisonPolicy.DEFAULT,
        DumbProgressIndicator.INSTANCE
    ).toList()
    return ChangeLineStats(
        addedLines = fragments.sumOf { it.endLine2 - it.startLine2 },
        removedLines = fragments.sumOf { it.endLine1 - it.startLine1 }
    )
}

internal fun trackedLineStatsDiffArgs(vararg revisions: String): List<String> = buildList {
    add("--numstat")
    add("-z")
    add(DIFF_FILTER_PARAM)
    add("-M")
    add(IGNORE_CR_AT_EOL_PARAM)
    addAll(revisions)
}

internal fun createLiveDocumentContentRevision(file: VirtualFile): ContentRevision {
    val filePath = VcsUtil.getFilePath(file)
    val content = ApplicationManager.getApplication().runReadAction<String> {
        FileDocumentManager.getInstance().getDocument(file)?.immutableCharSequence?.toString()
            ?: VfsUtilCore.loadText(file)
    }

    return object : ContentRevision {
        override fun getFile(): FilePath = filePath

        override fun getContent(): String = content

        override fun getRevisionNumber(): VcsRevisionNumber = object : VcsRevisionNumber {
            override fun asString(): String = "LOCAL"

            override fun compareTo(other: VcsRevisionNumber): Int = 0
        }
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