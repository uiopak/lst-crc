package com.github.uiopak.lstcrc.services

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository

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
) {
    companion object {
        val EMPTY = CategorizedChanges(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())
    }
}

data class ChangeLineStats(
    val addedLines: Int,
    val removedLines: Int
)

data class ChangeLineStatsKey(
    val beforePath: String?,
    val afterPath: String?
) {
    companion object {
        fun from(change: Change): ChangeLineStatsKey =
            fromPaths(change.beforeRevision?.file?.path, change.afterRevision?.file?.path)

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
