package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.LstCrcConstants.HEAD
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
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
import org.jetbrains.annotations.TestOnly
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

    @Volatile
    private var repositoriesForTest: List<GitRepository>? = null
    @Volatile
    private var workObserverForTest: ((String, Int) -> Unit)? = null

    /** Uses real test repositories and counts load work without modifying the platform registry. */
    @TestOnly
    internal fun setLoadObserverForTest(repositories: List<GitRepository>?, observer: ((String, Int) -> Unit)? = null) {
        repositoriesForTest = repositories
        workObserverForTest = observer
    }

    @Volatile
    private var beforeLoadForTest: ((TabInfo?) -> Unit)? = null

    /** Controls load timing and failures in tests without accessing platform repository internals. */
    @TestOnly
    internal fun setBeforeLoadForTest(beforeLoad: ((TabInfo?) -> Unit)?) {
        beforeLoadForTest = beforeLoad
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
    private inner class DiskChanges(
        val key: DiskChangesKey,
        val loaded: LoadedChanges,
        val overlayTarget: String
    ) {
        val withoutUnsaved: LoadedChanges by lazy { deduplicateDiskChanges(loaded) }
        val indexes: DiskFileIndexes by lazy {
            workObserverForTest?.invoke("overlay indexes", loaded.changes.size)
            DiskFileIndexes(
                newFilePaths(loaded.changes),
                movedSourcePaths(loaded.changes),
                loaded.changes.mapTo(HashSet()) { ChangesUtil.getFilePath(it).path },
                withoutUnsaved.changes.associateBy(ChangesUtil::getFilePath)
            )
        }
    }

    /** Derived once per disk result, only when a repository has an unsaved document to overlay. */
    private data class DiskFileIndexes(
        val newPaths: Set<String>,
        val movedFrom: Map<String, FilePath>,
        val paths: Set<String>,
        val changesByPath: Map<FilePath, Change>
    )

    private data class CategorizedResult(
        val loaded: List<LoadedChanges>,
        val result: GetChangesResult,
        val hasFailures: Boolean
    )

    @Volatile
    private var lastCategorizedResult: CategorizedResult? = null

    /**
     * The last [DiskChanges] per repository root. A refresh caused only by unsaved edits
     * (`reuseDiskChanges`) reuses them instead of running `git diff` and `git ls-files` again,
     * because nothing changed on disk; every other refresh reloads and replaces them.
     */
    private val lastDiskChanges = ConcurrentHashMap<String, DiskChanges>()

    internal fun getRepositoryForFile(file: VirtualFile): GitRepository? =
        GitRepositoryManager.getInstance(project).getRepositoryForFile(file)

    fun getRepositories(): List<GitRepository> = repositoriesForTest ?: GitRepositoryManager.getInstance(project).repositories

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
        beforeLoadForTest?.let { beforeLoad ->
            withContext(dispatcher) { beforeLoad(tabInfo) }
        }
        val repositories = getRepositories()
        val profileName = tabInfo?.branchName ?: HEAD

        logger.debug { "getChanges called for profile: $profileName" }

        if (repositories.isEmpty()) {
            return GetChangesResult(CategorizedChanges.EMPTY, emptyMap())
        }

        // Line stats add `--numstat` to the tracked diff plus in-process diffs of untracked/unsaved
        // files, so they are only computed while the tree shows them.
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
        val loaded = mutableListOf<LoadedChanges>()
        val comparisonContext = mutableMapOf<String, String>()
        // Only comparison tabs report missing targets; the HEAD tab never has one.
        val failures = if (tabInfo == null) null else mutableMapOf<GitRepository, String>()
        for (repo in repositories) {
            val target = resolveComparisonTarget(repo, tabInfo)
            comparisonContext[repo.root.path] = target
            logger.debug { "Repo '${repo.root.path}': using target '$target'" }
            val loadedChanges = loadChanges(repo, target, includeLineStats, failures, reuseDiskChanges)
            loaded.add(loadedChanges)
        }
        val previous = lastCategorizedResult
        val hasFailures = !failures.isNullOrEmpty()
        if (reuseDiskChanges && !hasFailures && previous != null && !previous.hasFailures &&
            previous.result.categorizedChanges.comparisonContext == comparisonContext &&
            previous.result.categorizedChanges.lineStatsIncluded == includeLineStats &&
            previous.loaded.size == loaded.size && loaded.indices.all { loaded[it] === previous.loaded[it] }) {
            return previous.result
        }
        val allChanges = loaded.flatMap { it.changes }
        val lineStatsByChange = linkedMapOf<ChangeLineStatsKey, ChangeLineStats>()
        loaded.forEach { lineStatsByChange.putAll(it.lineStatsByChange) }
        val categorizedChanges = buildCategorizedChanges(allChanges, comparisonContext, lineStatsByChange)
        return GetChangesResult(categorizedChanges.copy(lineStatsIncluded = includeLineStats), failures.orEmpty()).also {
            lastCategorizedResult = CategorizedResult(loaded, it, hasFailures)
        }
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
        // Git already supplied every tracked count, including the absence of counts for binary files.
        val loaded = if (untrackedChanges.isEmpty()) trackedChanges else LoadedChanges(
            changes = trackedChanges.changes + untrackedChanges,
            lineStatsByChange = if (key.includeLineStats) LinkedHashMap(trackedChanges.lineStatsByChange).apply {
                putAll(buildLineStats(untrackedChanges, emptyMap(), emptySet()))
            } else emptyMap(),
            contentOnlyBlobIds = trackedChanges.contentOnlyBlobIds
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
    private fun runGitDiff(repo: GitRepository, vararg params: String): String {
        workObserverForTest?.invoke("git diff", 1)
        return runSilentGit(project, repo.root, GitCommand.DIFF, *params).getOutputOrThrow()
    }

    /** Splits changes into created/modified/moved/deleted files in one pass. */
    private fun buildCategorizedChanges(
        allChanges: List<Change>,
        comparisonContext: Map<String, String>,
        lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
    ): CategorizedChanges {
        workObserverForTest?.invoke("categorization", allChanges.size)
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
        val unsavedFiles = collectUnsavedFiles(repo)
        if (unsavedFiles.isEmpty()) return diskChanges.withoutUnsaved
        val indexes = diskChanges.indexes
        val unsavedChanges = collectUnsavedDocumentChanges(
            repo,
            diskChanges.overlayTarget,
            unsavedFiles,
            indexes.newPaths,
            indexes.movedFrom
        )
        if (unsavedChanges.isEmpty()) return diskChanges.withoutUnsaved
        val restoredKeys = HashSet<ChangeLineStatsKey>()
        val allChanges = overlayUnsavedDocumentChanges(indexes.changesByPath, unsavedChanges).filterNot { change ->
            val before = change.beforeRevision as? TextContentRevision ?: return@filterNot false
            val after = change.afterRevision as? TextContentRevision ?: return@filterNot false
            if (before.file.path != after.file.path || before.content != after.content) return@filterNot false
            val restored = if (after.file.path !in indexes.paths) true else {
                val targetBlob = diskChanges.loaded.contentOnlyBlobIds[after.file.path] ?: return@filterNot false
                liveDocumentMatchesBlob(repo, after, targetBlob)
            }
            if (restored) restoredKeys.add(ChangeLineStatsKey.from(change))
            restored
        }
        if (!diskChanges.key.includeLineStats) return LoadedChanges(allChanges, emptyMap())
        workObserverForTest?.invoke("line stats", unsavedChanges.size)
        val lineStats = LinkedHashMap(diskChanges.withoutUnsaved.lineStatsByChange)
        for (change in unsavedChanges) {
            val key = ChangeLineStatsKey.from(change)
            indexes.changesByPath[ChangesUtil.getFilePath(change)]?.let { lineStats.remove(ChangeLineStatsKey.from(it)) }
            lineStats.remove(key)
            if (key !in restoredKeys) computeFallbackLineStats(change)?.let { lineStats[key] = it }
        }
        return LoadedChanges(allChanges, lineStats)
    }

    private fun loadUntrackedChanges(repo: GitRepository): List<Change> {
        workObserverForTest?.invoke("git ls-files", 1)
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

    /** Compare restored editor bytes with the target, including encoding, BOM, line endings and Git filters. */
    private fun liveDocumentMatchesBlob(repo: GitRepository, revision: TextContentRevision, targetBlob: String): Boolean {
        val file = revision.file.virtualFile?.takeIf { it.isValid } ?: return false
        val relativePath = FileUtil.getRelativePath(repo.root.path, file.path, '/') ?: return false
        val bytes = ApplicationManager.getApplication().runReadAction<ByteArray> {
            val separator = FileDocumentManager.getInstance().getLineSeparator(file, project)
            val charset = file.charset
            val encoded = StringUtil.convertLineSeparators(revision.content, separator).toByteArray(charset)
            val bom = file.bom
            // Detect an encoder-generated BOM independently of a leading U+FEFF text character.
            val encoderAddsBom = bom != null && "\u0000".toByteArray(charset).take(bom.size).toByteArray().contentEquals(bom)
            if (bom == null || encoderAddsBom) encoded else bom + encoded
        }
        return try {
            workObserverForTest?.invoke("git restored content", 1)
            runSilentGit(project, repo.root, GitCommand.HASH_OBJECT, arrayOf("--path=$relativePath", "--stdin"), bytes)
                .getOutputOrThrow().trim().startsWith(targetBlob)
        } catch (e: VcsException) {
            logger.debug(e) { "Could not verify restored unsaved content for '${file.path}'." }
            false
        }
    }

    private fun overlayUnsavedDocumentChanges(
        baseChanges: List<Change>,
        unsavedChanges: List<Change>
    ): List<Change> {
        workObserverForTest?.invoke("overlay path lookups", baseChanges.size)
        // FilePath equality follows the file system's case sensitivity.
        val mergedChanges = LinkedHashMap<FilePath, Change>()
        baseChanges.forEach { change -> mergedChanges[ChangesUtil.getFilePath(change)] = change }
        return overlayUnsavedDocumentChanges(mergedChanges, unsavedChanges)
    }

    private fun overlayUnsavedDocumentChanges(
        baseChanges: Map<FilePath, Change>,
        unsavedChanges: List<Change>
    ): List<Change> {
        val mergedChanges = LinkedHashMap(baseChanges)
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
        workObserverForTest?.invoke("line stats", changes.size)
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
        return try {
            // A missing side is empty; an existing revision with no text (such as a binary file) has no line stats.
            val beforeContent = change.beforeRevision?.let { it.content ?: return null } ?: ""
            val afterContent = change.afterRevision?.let { it.content ?: return null } ?: ""
            calculateLineStats(beforeContent, afterContent)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug(e) { "Failed to compute fallback line stats for '${change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path}'." }
            null
        }
    }

    /**
     * Changes for [repo]'s unsaved documents against [targetRevision]. A moved file is compared with its old
     * path ([movedFrom]), which is where the target has it. Files already new in the comparison
     * ([newPaths]) overlay their live text without loading a before revision.
     */
    internal fun collectUnsavedDocumentChanges(
        repo: GitRepository,
        targetRevision: String,
        newPaths: Set<String>,
        movedFrom: Map<String, FilePath>
    ): List<Change> = collectUnsavedDocumentChanges(repo, targetRevision, collectUnsavedFiles(repo), newPaths, movedFrom)

    private fun collectUnsavedFiles(repo: GitRepository): List<VirtualFile> {
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
            .filter { file ->
                // A nested repository owns its files even though the parent root is also an ancestor.
                val owner = getRepositoryForFile(file)
                owner == null || owner.root == repo.root
            }
            .toList()
    }

    private fun collectUnsavedDocumentChanges(
        repo: GitRepository,
        targetRevision: String,
        files: List<VirtualFile>,
        newPaths: Set<String>,
        movedFrom: Map<String, FilePath>
    ): List<Change> = files.mapNotNull { file ->
        createUnsavedDocumentChange(repo, file, targetRevision, file.path in newPaths, movedFrom[file.path])
    }

    private fun createUnsavedDocumentChange(repo: GitRepository, file: VirtualFile, targetRevision: String, isNew: Boolean, movedFrom: FilePath?): Change? {
        return try {
            val beforeRevision = if (isNew) null else {
                val targetPath = movedFrom ?: VcsUtil.getFilePath(file)
                createTargetContentRevision(repo, targetPath, file.charset, targetRevision) ?: return null
            }
            val afterRevision = createLiveDocumentContentRevision(file)
            Change(beforeRevision, afterRevision, if (isNew) FileStatus.ADDED else FileStatus.MODIFIED)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
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
            ?: run {
                workObserverForTest?.invoke("git target content", 1)
                return loadRevisionTextContent(project, repo.root, revision, relativePath, charset)
            }
        return revisionContentCache.get(repo.root.path, commitHash, relativePath, charset) {
            workObserverForTest?.invoke("git target content", 1)
            loadRevisionTextContent(project, repo.root, commitHash, relativePath, charset)
        }
    }

    /** The content of [path] at [revision] as a [ContentRevision], or null when it cannot be loaded. */
    private fun createTargetContentRevision(repo: GitRepository, path: FilePath, charset: Charset, revision: String): ContentRevision? {
        val relativePath = FileUtil.getRelativePath(repo.root.path, path.path, '/') ?: return null
        val content = try {
            loadRevisionText(repo, revision, relativePath, charset)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return TextContentRevision(path, content, GitRevisionNumber(revision))
    }
}

/** Runs git [command] in [root] without echoing it or its output to the VCS console. */
internal fun runSilentGit(project: Project, root: VirtualFile, command: GitCommand, vararg params: String): GitCommandResult {
    return runSilentGit(project, root, command, params, null)
}

/** The same silent command setup, with optional bytes for commands such as `hash-object --stdin`. */
@Suppress("UsePropertyAccessSyntax")
private fun runSilentGit(project: Project, root: VirtualFile, command: GitCommand, params: Array<out String>, input: ByteArray?): GitCommandResult {
    val handler = GitLineHandler(project, root, command)
    handler.setSilent(true)
    handler.setStdoutSuppressed(true)
    handler.addParameters(*params)
    if (input != null) handler.setInputProcessor { output -> output.use { it.write(input) } }
    return Git.getInstance().runCommand(handler)
}
