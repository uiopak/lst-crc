package com.github.uiopak.lstcrc.services

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber

// Pure helpers of GitService: git diff options, parsing its output and the change lists built from it.

private const val DIFF_FILTER_PARAM = "--diff-filter=ADCMRUXT"
private const val IGNORE_CR_AT_EOL_PARAM = "--ignore-cr-at-eol"

/** Changes of one repository and the line stats known for them. */
internal data class LoadedChanges(
    val changes: List<Change>,
    val lineStatsByChange: Map<ChangeLineStatsKey, ChangeLineStats>
) {
    companion object {
        val EMPTY = LoadedChanges(emptyList(), emptyMap())
    }
}

/**
 * Parses `git diff --raw -z` output, optionally followed by `--numstat -z` output. The "before" side of
 * each change is [targetRevision], the "after" side is the working tree. Paths are unquoted with `-z`.
 *
 * - Raw records are `:<modes> <hashes> <status><NUL><path><NUL>`, with a second path for renames and copies.
 * - Numstat records are `added<TAB>removed<TAB>path<NUL>`, or for renames `added<TAB>removed<TAB><NUL>old<NUL>new<NUL>`.
 *   Binary files report `-` counts and get no stats.
 */
internal fun parseTrackedDiff(project: Project, repoRoot: VirtualFile, targetRevision: GitRevisionNumber, output: String): LoadedChanges {
    val root = repoRoot.path
    fun path(relativePath: String) = GitContentRevision.createPath(repoRoot, relativePath)
    fun before(path: FilePath) = GitContentRevision.createRevision(path, targetRevision, project)
    fun after(path: FilePath) = GitContentRevision.createRevision(path, null, project)

    val changes = mutableListOf<Change>()
    val stats = mutableListOf<Pair<ChangeLineStatsKey, ChangeLineStats?>>()
    val fields = output.split('\u0000').iterator()
    while (fields.hasNext()) {
        val field = fields.next().trim('\n')
        if (field.startsWith(':')) {
            val status = field.substringAfterLast(' ').firstOrNull()
            val first = if (fields.hasNext()) fields.next() else break
            changes += when (status) {
                'A' -> Change(null, after(path(first)), FileStatus.ADDED)
                'D' -> Change(before(path(first)), null, FileStatus.DELETED)
                'M', 'T', 'U', 'X' -> path(first).let { Change(before(it), after(it), FileStatus.MODIFIED) }
                'R', 'C' -> {
                    val second = if (fields.hasNext()) fields.next() else break
                    Change(before(path(first)), after(path(second)), FileStatus.MODIFIED)
                }
                else -> continue
            }
            continue
        }
        val tokens = field.split('\t')
        if (tokens.size < 3) continue
        val key = if (tokens[2].isEmpty()) {
            // Rename or copy: the old and new paths follow as separate fields.
            val oldPath = if (fields.hasNext()) fields.next() else break
            val newPath = if (fields.hasNext()) fields.next() else break
            ChangeLineStatsKey.fromPaths("$root/$oldPath", "$root/$newPath")
        } else {
            ChangeLineStatsKey.fromPaths(null, "$root/${tokens[2]}")
        }
        val addedLines = tokens[0].toIntOrNull()
        val removedLines = tokens[1].toIntOrNull()
        stats += key to if (addedLines != null && removedLines != null) ChangeLineStats(addedLines, removedLines) else null
    }
    return LoadedChanges(changes, matchLineStats(changes, stats))
}

/**
 * Maps numstat entries to the keys of [changes]. A plain path matches a change by its after path, else by
 * its before path (deletions); a rename entry must match a change's key exactly.
 */
private fun matchLineStats(
    changes: List<Change>,
    stats: List<Pair<ChangeLineStatsKey, ChangeLineStats?>>
): Map<ChangeLineStatsKey, ChangeLineStats> {
    if (stats.isEmpty()) return emptyMap()
    val keys = changes.mapTo(LinkedHashSet(), ChangeLineStatsKey::from)
    val byAfterPath = keys.filter { it.afterPath != null }.associateBy { it.afterPath!! }
    val byBeforePath = keys.filter { it.beforePath != null }.associateBy { it.beforePath!! }
    val result = linkedMapOf<ChangeLineStatsKey, ChangeLineStats>()
    for ((entry, lineStats) in stats) {
        val key = if (entry.beforePath != null) {
            entry.takeIf { it in keys }
        } else {
            byAfterPath[entry.afterPath] ?: byBeforePath[entry.afterPath]
        } ?: continue
        result[key] = lineStats ?: continue
    }
    return result
}

/**
 * `git diff` options for the tracked changes against [target]; with [includeLineStats] also `--numstat`.
 * The trailing `--` keeps git from reading a branch named like a file or folder (`docs`) as a path.
 */
internal fun trackedDiffArgs(target: String, includeLineStats: Boolean): List<String> = buildList {
    if (includeLineStats) {
        add("--numstat")
        add(IGNORE_CR_AT_EOL_PARAM)
    }
    add("-z")
    add(DIFF_FILTER_PARAM)
    add("-M")
    add(target)
    add("--")
}

/**
 * Changes for the NUL-separated paths of `git ls-files --others -z` in [root]. `-z` paths are not quoted,
 * so they must not be unescaped: a backslash in a file name is part of the name.
 */
internal fun untrackedChanges(project: Project, root: VirtualFile, output: String): List<Change> =
    output.split('\u0000').filter(String::isNotBlank).map { relativePath ->
        Change(null, GitContentRevision.createRevision(GitContentRevision.createPath(root, relativePath), null, project), FileStatus.UNKNOWN)
    }

/**
 * Paths `git diff` reported as added. They have no content in the target, so loading it for the unsaved-edit
 * overlay could only fail. Untracked files (status UNKNOWN) are not included: they can exist in the target.
 */
internal fun trackedAddedPaths(changes: List<Change>): Set<String> =
    changes.mapNotNullTo(HashSet()) { change -> change.afterRevision?.file?.path?.takeIf { change.fileStatus == FileStatus.ADDED } }

/** The old path of each moved (renamed or copied) file in [changes], by its new path. */
internal fun movedSourcePaths(changes: List<Change>): Map<String, FilePath> =
    changes.mapNotNull { change ->
        val before = change.beforeRevision?.file ?: return@mapNotNull null
        val after = change.afterRevision?.file ?: return@mapNotNull null
        (after.path to before).takeIf { before.path != after.path }
    }.toMap()

internal fun mergeUnsavedOverlayChange(existingChange: Change?, unsavedChange: Change): Change {
    if (existingChange?.type == Change.Type.NEW && unsavedChange.afterRevision != null) {
        return Change(null, unsavedChange.afterRevision, FileStatus.ADDED)
    }

    return unsavedChange
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
