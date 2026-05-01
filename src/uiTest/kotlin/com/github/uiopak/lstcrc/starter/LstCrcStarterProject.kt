package com.github.uiopak.lstcrc.starter

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LstCrcStarterProject private constructor(val path: Path) {

    companion object {
        fun create(testName: String): LstCrcStarterProject {
            val sanitized = testName
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "starter-ui-test" }
            val directory = Path.of("build", "starter-ui-tests", "$sanitized-${System.currentTimeMillis()}")
            directory.createDirectories()
            return LstCrcStarterProject(directory)
        }
    }

    fun initializeGitRepository() {
        git("init")
        git("config", "user.name", "LST-CRC Starter UI Tests")
        git("config", "user.email", "lst-crc-starter-ui-tests@example.invalid")
    }

    fun initializeGitRepositoryAt(relativeRepoPath: String) {
        repoPath(relativeRepoPath).createDirectories()
        gitAt(relativeRepoPath, "init")
        gitAt(relativeRepoPath, "config", "user.name", "LST-CRC Starter UI Tests")
        gitAt(relativeRepoPath, "config", "user.email", "lst-crc-starter-ui-tests@example.invalid")
    }

    fun addLinkedWorktree(sourceRepoRelativePath: String, worktreeRelativePath: String, branchName: String, startPoint: String = "HEAD") {
        val worktreePath = repoPath(worktreeRelativePath)
        worktreePath.parent?.createDirectories()
        val absoluteWorktreePath = worktreePath.toAbsolutePath().normalize().toString()
        gitAt(sourceRepoRelativePath, "worktree", "add", "-b", branchName, absoluteWorktreePath, startPoint)
        gitAt(worktreeRelativePath, "config", "user.name", "LST-CRC Starter UI Tests")
        gitAt(worktreeRelativePath, "config", "user.email", "lst-crc-starter-ui-tests@example.invalid")
    }

    fun writeFile(relativePath: String, content: String) {
        val file = path.resolve(relativePath)
        val existed = Files.exists(file)
        file.parent?.createDirectories()
        file.writeText(content)
        if (!existed && path.resolve(".git").exists()) {
            git("add", "--intent-to-add", relativePath)
        }
    }

    fun writeFileInRepo(repoRelativePath: String, relativePath: String, content: String) {
        val repoRoot = repoPath(repoRelativePath)
        val file = repoRoot.resolve(relativePath)
        val existed = Files.exists(file)
        file.parent?.createDirectories()
        file.writeText(content)
        if (!existed && repoRoot.resolve(".git").exists()) {
            gitAt(repoRelativePath, "add", "--intent-to-add", relativePath)
        }
    }

    fun renameFile(oldPath: String, newPath: String) {
        val source = path.resolve(oldPath)
        val target = path.resolve(newPath)
        target.parent?.createDirectories()
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    fun deleteFile(relativePath: String) {
        path.resolve(relativePath).deleteIfExists()
    }

    fun deleteFileInRepo(repoRelativePath: String, relativePath: String) {
        repoPath(repoRelativePath).resolve(relativePath).deleteIfExists()
    }

    fun commitAll(message: String) {
        git("add", "-A")
        git("commit", "-m", message, "--no-gpg-sign")
    }

    fun commitAllInRepo(repoRelativePath: String, message: String) {
        gitAt(repoRelativePath, "add", "-A")
        gitAt(repoRelativePath, "commit", "-m", message, "--no-gpg-sign")
    }

    fun createBranch(branchName: String) {
        git("checkout", "-B", branchName)
    }

    fun createBranchInRepo(repoRelativePath: String, branchName: String) {
        gitAt(repoRelativePath, "checkout", "-B", branchName)
    }

    fun checkout(branchName: String) {
        git("checkout", branchName)
    }

    fun checkoutInRepo(repoRelativePath: String, branchName: String) {
        gitAt(repoRelativePath, "checkout", branchName)
    }

    fun deleteBranch(branchName: String) {
        git("branch", "-D", branchName)
    }

    fun deleteBranchInRepo(repoRelativePath: String, branchName: String) {
        gitAt(repoRelativePath, "branch", "-D", branchName)
    }

    fun defaultBranchName(): String = git("rev-parse", "--abbrev-ref", "HEAD")

    fun defaultBranchNameInRepo(repoRelativePath: String): String = gitAt(repoRelativePath, "rev-parse", "--abbrev-ref", "HEAD")

    fun gitRevision(reference: String): String = git("rev-parse", reference)

    fun gitRevisionInRepo(repoRelativePath: String, reference: String): String = gitAt(repoRelativePath, "rev-parse", reference)

    private fun repoPath(relativeRepoPath: String): Path = path.resolve(relativeRepoPath)

    private fun gitAt(relativeRepoPath: String, vararg args: String): String {
        return git(repoPath = repoPath(relativeRepoPath), args = args)
    }

    private fun git(vararg args: String): String {
        return git(path, args)
    }

    private fun git(repoPath: Path, args: Array<out String>): String {
        fun execute(): Pair<Int, String> {
            val process = ProcessBuilder(listOf("git", *args))
                .directory(repoPath.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            return process.waitFor() to output
        }

        waitForGitIdle(repoPath)
        var (exitCode, output) = execute()
        if (exitCode != 0 && output.contains("index.lock")) {
            waitForGitIdle(repoPath)
            val retried = execute()
            exitCode = retried.first
            output = retried.second
        }

        check(exitCode == 0) {
            if (output.isNotBlank()) output else "git ${args.joinToString(" ")} failed with exit code $exitCode"
        }
        return output
    }

    private fun waitForGitIdle(repoPath: Path) {
        val deadline = System.nanoTime() + 30.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            val lockFile = repoPath.resolve(".git").resolve("index.lock")
            if (!lockFile.exists()) {
                val statusProcess = ProcessBuilder(listOf("git", "status", "--porcelain"))
                    .directory(repoPath.toFile())
                    .redirectErrorStream(true)
                    .start()
                statusProcess.inputStream.bufferedReader().use { it.readText() }
                if (statusProcess.waitFor() == 0) {
                    return
                }
            }
            Thread.sleep(250.milliseconds.inWholeMilliseconds)
        }

        error("Timed out waiting for Git to become idle in '$repoPath'.")
    }
}