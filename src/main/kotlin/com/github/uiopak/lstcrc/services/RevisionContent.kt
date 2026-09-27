package com.github.uiopak.lstcrc.services

import com.github.uiopak.lstcrc.LstCrcConstants.HEAD
import com.github.uiopak.lstcrc.utils.isCommitHash
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

// File contents at a revision: loading them with git, caching them by commit, and the revisions built from them.

/** Revision contents kept by [RevisionContentCache]; larger files are loaded every time. */
private const val REVISION_CONTENT_CACHE_ENTRIES = 64
private const val REVISION_CONTENT_CACHE_MAX_CHARS = 512 * 1024

/**
 * File contents at a commit, or the fact that the file does not exist in it. Refreshes while typing (the
 * unsaved-edit overlay) and gutter loads ask for the same content again and again, and every miss runs
 * `git show`. Entries are keyed by commit hash rather than branch name, so they never go stale.
 */
internal class RevisionContentCache {
    private data class Key(
        val root: String,
        val commitHash: String,
        val relativePath: String,
        val charset: Charset
    )

    /** A null value means the file does not exist in that commit. */
    private val entries =
        object : LinkedHashMap<Key, String?>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, String?>): Boolean =
                size > REVISION_CONTENT_CACHE_ENTRIES
        }

    /**
     * [relativePath]'s text at [commitHash], from the cache or [load]. Throws [VcsException] when the file does not
     * exist in the commit (remembered: untracked files with unsaved edits would otherwise run a failing `git show`
     * on every refresh) or when [load] fails otherwise (not remembered).
     */
    fun get(root: String, commitHash: String, relativePath: String, charset: Charset, load: () -> String): String {
        val key = Key(root, commitHash, relativePath, charset)
        synchronized(entries) {
            if (entries.containsKey(key)) {
                return entries[key] ?: throw VcsException("Path '$relativePath' does not exist in '$commitHash'")
            }
        }

        val content = try {
            load()
        } catch (e: VcsException) {
            if (isFileMissingInRevision(e)) synchronized(entries) { entries[key] = null }
            throw e
        }
        if (content.length <= REVISION_CONTENT_CACHE_MAX_CHARS) {
            synchronized(entries) { entries[key] = content }
        }
        return content
    }
}

/**
 * True when [error] (or a cause) is git saying the file does not exist in the requested revision, as opposed to
 * a failure worth reporting.
 */
internal fun isFileMissingInRevision(error: Throwable): Boolean {
    val message = generateSequence(error) { it.cause }.firstOrNull { it is VcsException }?.message.orEmpty()
    return message.contains("does not exist in", ignoreCase = true) ||
        message.contains("exists on disk, but not in", ignoreCase = true)
}

/** True when git resolves [revision] to a commit in [root]; false for a missing branch, tag or hash. */
@Suppress("UsePropertyAccessSyntax")
internal fun revisionExists(project: Project, root: VirtualFile, revision: String): Boolean {
    val handler = GitLineHandler(project, root, GitCommand.REV_PARSE)
    handler.setSilent(true)
    handler.addParameters("--verify", "--quiet", "$revision^{commit}")
    return Git.getInstance().runCommand(handler).exitCode == 0
}

/**
 * The commit [revision] points to, read from Git4Idea's in-memory repository state (no git call), or
 * null when that state cannot tell: tags, abbreviated hashes, a repository without commits.
 */
internal fun resolveCommitHash(repo: GitRepository, revision: String): String? {
    if (revision == HEAD) return repo.currentRevision
    val branches = repo.branches
    branches.findBranchByName(revision)?.let { return branches.getHash(it)?.asString() }
    return revision.takeIf { it.length == 40 && isCommitHash(it) }
}

internal fun loadRevisionTextContent(
    project: Project,
    repoRoot: VirtualFile,
    revision: String,
    relativePath: String,
    charset: Charset
): String {
    val revisionContentBytes = GitFileUtils.getFileContent(project, repoRoot, revision, relativePath)
    val rawContent = BOMInputStream.builder()
        .setInputStream(ByteArrayInputStream(revisionContentBytes))
        .setByteOrderMarks(
            ByteOrderMark.UTF_8,
            ByteOrderMark.UTF_16LE,
            ByteOrderMark.UTF_16BE,
            ByteOrderMark.UTF_32LE,
            ByteOrderMark.UTF_32BE
        )
        .get()
        .use { it.reader(charset).readText() }

    // The IntelliJ Document model requires LF ('\n') line endings, but Git on Windows might return CRLF ('\r\n').
    return StringUtil.convertLineSeparators(rawContent)
}

/**
 * A revision whose text is already loaded: the target side and the live-document side of an unsaved edit.
 * Unlike an anonymous [ContentRevision], two loads of the same text are equal, so a refresh that finds the
 * same unsaved content is recognized as unchanged (see `ProjectActiveDiffDataService.updateActiveDiff`).
 */
internal data class TextContentRevision(
    private val filePath: FilePath,
    private val text: String,
    private val revision: VcsRevisionNumber
) : ContentRevision {
    override fun getFile(): FilePath = filePath

    override fun getContent(): String = text

    override fun getRevisionNumber(): VcsRevisionNumber = revision
}

/** The revision number of live-document content. One instance, so equal content means equal revisions. */
private object LocalRevisionNumber : VcsRevisionNumber {
    override fun asString(): String = "LOCAL"

    override fun compareTo(other: VcsRevisionNumber): Int = 0
}

internal fun createLiveDocumentContentRevision(file: VirtualFile): ContentRevision {
    val content = ApplicationManager.getApplication().runReadAction<String> {
        FileDocumentManager.getInstance().getDocument(file)?.immutableCharSequence?.toString()
            ?: VfsUtilCore.loadText(file)
    }
    return TextContentRevision(VcsUtil.getFilePath(file), content, LocalRevisionNumber)
}
