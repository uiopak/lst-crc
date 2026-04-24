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

    fun writeFile(relativePath: String, content: String) {
        val file = path.resolve(relativePath)
        val existed = Files.exists(file)
        file.parent?.createDirectories()
        file.writeText(content)
        if (!existed && path.resolve(".git").exists()) {
            git("add", "--intent-to-add", relativePath)
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

    fun commitAll(message: String) {
        git("add", "-A")
        git("commit", "-m", message, "--no-gpg-sign")
    }

    fun createBranch(branchName: String) {
        git("checkout", "-B", branchName)
    }

    fun checkout(branchName: String) {
        git("checkout", branchName)
    }

    fun deleteBranch(branchName: String) {
        git("branch", "-D", branchName)
    }

    fun defaultBranchName(): String = git("rev-parse", "--abbrev-ref", "HEAD")

    fun gitRevision(reference: String): String = git("rev-parse", reference)

    private fun git(vararg args: String): String {
        fun execute(): Pair<Int, String> {
            val process = ProcessBuilder(listOf("git", *args))
                .directory(path.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            return process.waitFor() to output
        }

        waitForGitIdle()
        var (exitCode, output) = execute()
        if (exitCode != 0 && output.contains("index.lock")) {
            waitForGitIdle()
            val retried = execute()
            exitCode = retried.first
            output = retried.second
        }

        check(exitCode == 0) {
            if (output.isNotBlank()) output else "git ${args.joinToString(" ")} failed with exit code $exitCode"
        }
        return output
    }

    private fun waitForGitIdle() {
        val deadline = System.nanoTime() + 30.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            val lockFile = path.resolve(".git").resolve("index.lock")
            if (!lockFile.exists()) {
                val statusProcess = ProcessBuilder(listOf("git", "status", "--porcelain"))
                    .directory(path.toFile())
                    .redirectErrorStream(true)
                    .start()
                statusProcess.inputStream.bufferedReader().use { it.readText() }
                if (statusProcess.waitFor() == 0) {
                    return
                }
            }
            Thread.sleep(250.milliseconds.inWholeMilliseconds)
        }

        error("Timed out waiting for Git to become idle in '$path'.")
    }
}