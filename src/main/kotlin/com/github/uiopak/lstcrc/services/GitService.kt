package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
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

private const val DIFF_FILTER_PARAM = "--diff-filter=ADCMRUXT"
private const val IGNORE_CR_AT_EOL_PARAM = "--ignore-cr-at-eol"


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

    suspend fun getChanges(
        tabInfo: TabInfo?,
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
                loadChangesResult(repositories, tabInfo, includeLineStats)
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
        includeLineStats: Boolean
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
            val loadedChanges = loadChanges(repo, target, includeLineStats, failures)
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
        failures: MutableMap<GitRepository, String>?
    ): LoadedChanges {
        repo.update()
        if (repo.isFresh) {
            logger.debug { "Repo '${repo.root.name}' is fresh. Showing only untracked files and unsaved edits for target '$target'." }
            return combineWithUntrackedAndUnsaved(repo, "HEAD", LoadedChanges.EMPTY, includeLineStats)
        }

        val trackedChanges = try {
            loadTrackedChangesAgainstWorkingTree(repo, target, includeLineStats)
        } catch (e: VcsException) {
            logger.warn("git diff failed for repo '${repo.root.name}' against target '$target': ${e.message}")
            if (failures != null) {
                failures[repo] = target
                return LoadedChanges.EMPTY
            }
            LoadedChanges.EMPTY
        }
        return combineWithUntrackedAndUnsaved(repo, target, trackedChanges, includeLineStats)
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

    private fun combineWithUntrackedAndUnsaved(
        repo: GitRepository,
        target: String,
        trackedChanges: LoadedChanges,
        includeLineStats: Boolean
    ): LoadedChanges {
        val untrackedChanges = if (ToolWindowSettingsProvider.isShowUntrackedFilesAsNew()) loadUntrackedChanges(repo) else emptyList()
        val unsavedChanges = collectUnsavedDocumentChanges(repo, target)
        val allChanges = overlayUnsavedDocumentChanges(trackedChanges.changes + untrackedChanges, unsavedChanges)
        if (!includeLineStats) return LoadedChanges(allChanges, emptyMap())
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
            val beforeRevision = createTargetContentRevision(project, repo, file, targetRevision) ?: return null
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

        val normalizedContent = loadRevisionTextContent(project, repository.root, revision, relativePath, file.charset)

        logger.debug { "GUTTER_GIT_SERVICE: Successfully fetched content for '${relativePath}' in revision '${revision}'." }
        return normalizedContent
    }
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
        override fun getFile(): FilePath = filePath

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