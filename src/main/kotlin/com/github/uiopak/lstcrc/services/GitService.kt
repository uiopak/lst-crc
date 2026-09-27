package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.LstCrcConstants.HEAD
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import git4idea.GitRevisionNumber
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * A project-level service responsible for all interactions with the Git4Idea plugin API.
 * It provides asynchronous methods to fetch branches, calculate diffs, and retrieve file content
 * from specific revisions.
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    private val logger = thisLogger()
    private val revisionContentCache = RevisionContentCache()

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

        // A normal multi-root layout (no repository at the project root), so not worth a warning on every call.
        logger.debug { "Multiple Git repositories found, but none contains the project base path. Using the first one: ${repositories.first().root.path}" }
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
        val profileName = tabInfo?.branchName ?: HEAD

        logger.debug { "getChanges called for profile: $profileName" }

        if (repositories.isEmpty()) {
            return GetChangesResult(CategorizedChanges.EMPTY, emptyMap())
        }

        // Line stats cost an extra `git diff --numstat` per repository plus in-process diffs of
        // untracked/unsaved files, so they are only computed while the tree shows them.
        val includeLineStats = ToolWindowSettingsProvider.isShowLineStatsInTree()
        // An edit-only refresh that can reuse every repository's last git result runs no git diff, so it shows
        // no progress indicator (it would flicker in the status bar after every pause in typing).
        val load: suspend () -> GetChangesResult = {
            withContext(dispatcher) { loadChangesResult(repositories, tabInfo, includeLineStats, reuseDiskChanges) }
        }
        val reusesAll = reuseDiskChanges && repositories.all { repo ->
            lastDiskChanges[repo.root.path]?.key == diskChangesKey(resolveComparisonTarget(repo, tabInfo), includeLineStats)
        }
        return if (reusesAll) load() else withBackgroundProgress(project, LstCrcBundle.message("git.task.loading.changes")) { load() }
    }

    private fun diskChangesKey(target: String, includeLineStats: Boolean) =
        DiskChangesKey(target, includeLineStats, ToolWindowSettingsProvider.isShowUntrackedFilesAsNew())

    fun resolveComparisonTarget(repo: GitRepository, tabInfo: TabInfo?): String {
        if (tabInfo == null) return HEAD
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
     * If `git diff` fails because [target] does not exist, a comparison tab records it in [failures] and shows
     * nothing for the repository. Otherwise the repository shows untracked files and unsaved edits only.
     */
    private fun loadChanges(
        repo: GitRepository,
        target: String,
        includeLineStats: Boolean,
        failures: MutableMap<GitRepository, String>?,
        reuseDiskChanges: Boolean
    ): LoadedChanges {
        val key = diskChangesKey(target, includeLineStats)
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
        val trackedChanges = if (repo.isFresh) {
            logger.debug { "Repo '${repo.root.name}' is fresh. Showing only untracked files and unsaved edits for target '${key.target}'." }
            LoadedChanges.EMPTY
        } else {
            try {
                loadTrackedChangesAgainstWorkingTree(repo, key.target, key.includeLineStats)
            } catch (e: VcsException) {
                logger.warn("git diff failed for repo '${repo.root.name}' against target '${key.target}': ${e.message}")
                var targetMissing = false
                if (failures != null && !revisionExists(project, repo.root, key.target)) {
                    failures[repo] = key.target
                    targetMissing = true
                }
                val lastResult = lastDiskChanges[repo.root.path]?.takeIf { it.key == key }
                return diskChangesAfterDiffFailure(targetMissing, lastResult) {
                    buildDiskChanges(repo, key, LoadedChanges.EMPTY)
                }.also { if (it !== lastResult) lastDiskChanges.remove(repo.root.path) }
            }
        }

        return buildDiskChanges(repo, key, trackedChanges).also { lastDiskChanges[repo.root.path] = it }
    }

    /** [trackedChanges] plus [repo]'s untracked files (when shown) and, with line stats on, their counts. */
    private fun buildDiskChanges(repo: GitRepository, key: DiskChangesKey, trackedChanges: LoadedChanges): DiskChanges {
        val untrackedChanges = if (key.includeUntracked) loadUntrackedChanges(repo) else emptyList()
        val changes = trackedChanges.changes + untrackedChanges
        val loaded = LoadedChanges(
            changes = changes,
            lineStatsByChange = if (key.includeLineStats) buildLineStats(changes, trackedChanges.lineStatsByChange, emptySet()) else emptyMap()
        )
        return DiskChanges(key, loaded, overlayTarget = if (repo.isFresh) HEAD else key.target)
    }

    /**
     * Loads the tracked changes against [target] and, with [includeLineStats], their line counts, from one
     * `git diff --raw [--numstat] -z` run. `--ignore-cr-at-eol` only changes the counts: raw records list
     * every changed file whatever the whitespace options.
     */
    private fun loadTrackedChangesAgainstWorkingTree(repo: GitRepository, target: String, includeLineStats: Boolean): LoadedChanges {
        val output = runGitDiff(repo, "--raw", *trackedDiffArgs(target, includeLineStats).toTypedArray())
        return parseTrackedDiff(project, repo.root, GitRevisionNumber(target), output)
    }

    /** Runs `git diff` in [repo] and returns its stdout, throwing [VcsException] on failure. */
    private fun runGitDiff(repo: GitRepository, vararg params: String): String =
        runSilentGit(project, repo.root, GitCommand.DIFF, *params).getOutputOrThrow()

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
        val diskChangeList = diskChanges.loaded.changes
        val unsavedChanges = collectUnsavedDocumentChanges(
            repo,
            diskChanges.overlayTarget,
            trackedAddedPaths(diskChangeList),
            movedSourcePaths(diskChangeList)
        )
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

    private fun loadUntrackedChanges(repo: GitRepository): List<Change> {
        val result = runSilentGit(project, repo.root, GitCommand.LS_FILES, "--others", "--exclude-standard", "-z")

        if (result.exitCode != 0) {
            logger.warn(
                "Failed to load untracked local changes for repo '${repo.root.name}': " +
                    result.errorOutputAsJoinedString
            )
            return emptyList()
        }

        return untrackedChanges(project, repo.root, result.outputAsJoinedString)
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
        val keyedChanges = changes.map { ChangeLineStatsKey.from(it) to it }
        val keys = keyedChanges.mapTo(HashSet()) { it.first }

        trackedLineStats.forEach { (key, stats) ->
            if (key in keys && key !in forceRecompute) {
                lineStats[key] = stats
            }
        }

        keyedChanges.forEach { (key, change) ->
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

    /**
     * Changes for [repo]'s unsaved documents against [targetRevision]. A moved file is compared with its old
     * path ([movedFrom]), which is where the target has it.
     */
    internal fun collectUnsavedDocumentChanges(
        repo: GitRepository,
        targetRevision: String,
        addedPaths: Set<String>,
        movedFrom: Map<String, FilePath>
    ): List<Change> {
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
            .filter { file -> file.path !in addedPaths }
            .mapNotNull { file -> createUnsavedDocumentChange(repo, file, targetRevision, movedFrom[file.path]) }
            .toList()
    }

    private fun createUnsavedDocumentChange(repo: GitRepository, file: VirtualFile, targetRevision: String, movedFrom: FilePath?): Change? {
        return try {
            val targetPath = movedFrom ?: VcsUtil.getFilePath(file)
            val beforeRevision = createTargetContentRevision(repo, targetPath, file.charset, targetRevision) ?: return null
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

    /**
     * [file]'s content at [revision]. [pathInRevision] is the absolute path the file has there, when it differs
     * (a moved file is compared with its old path).
     */
    fun getFileContentForRevision(revision: String, file: VirtualFile, pathInRevision: String = file.path): String? {
        val repository = getRepositoryForFile(file)

        if (repository == null) {
            logger.warn("Cannot get file content for revision '$revision' for file '${file.path}', no repository found for this file.")
            return null
        }

        val relativePath = FileUtil.getRelativePath(repository.root.path, pathInRevision, '/')
            ?: throw IllegalStateException("Could not calculate relative path for '$pathInRevision' against repo root '${repository.root.path}'.")
        logger.debug { "GUTTER_GIT_SERVICE: Loading '$relativePath' at revision '$revision'." }
        return loadRevisionText(repository, revision, relativePath, file.charset)
    }

    /**
     * [relativePath]'s text at [revision], with LF line endings. Throws [VcsException] when git fails, for example
     * when the file does not exist in that revision. Content is cached when [revision] resolves to a commit.
     */
    private fun loadRevisionText(repo: GitRepository, revision: String, relativePath: String, charset: Charset): String {
        val commitHash = resolveCommitHash(repo, revision)
            ?: return loadRevisionTextContent(project, repo.root, revision, relativePath, charset)
        return revisionContentCache.get(repo.root.path, commitHash, relativePath, charset) {
            loadRevisionTextContent(project, repo.root, commitHash, relativePath, charset)
        }
    }

    /** The content of [path] at [revision] as a [ContentRevision], or null when it cannot be loaded. */
    private fun createTargetContentRevision(repo: GitRepository, path: FilePath, charset: Charset, revision: String): ContentRevision? {
        val relativePath = FileUtil.getRelativePath(repo.root.path, path.path, '/') ?: return null
        val content = runCatching { loadRevisionText(repo, revision, relativePath, charset) }.getOrElse { return null }
        return TextContentRevision(path, content, GitRevisionNumber(revision))
    }
}

/** Runs git [command] in [root] without echoing it or its output to the VCS console. */
@Suppress("UsePropertyAccessSyntax")
internal fun runSilentGit(project: Project, root: VirtualFile, command: GitCommand, vararg params: String): GitCommandResult {
    val handler = GitLineHandler(project, root, command)
    handler.setSilent(true)
    handler.setStdoutSuppressed(true)
    handler.addParameters(*params)
    return Git.getInstance().runCommand(handler)
}
