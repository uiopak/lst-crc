package com.github.uiopak.lstcrc.plugin.steps

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.component
import com.intellij.remoterobot.utils.waitFor
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration

/**
 * Shared helper operations for the LST-CRC Remote Robot UI suite.
 */
class PluginUiTestSteps(private val remoteRobot: RemoteRobot) {
    companion object {
        // Flag to track whether we've already handled the "Don't ask again" dialog
        private var dialogHandled = false
    }

    /**
     * Creates a new file with the given name and content
     */
    fun createNewFile(fileName: String, content: String) = step("Create new file: $fileName") {
        writeProjectFile(fileName, content)
        handleAddFileToGitDialogIfPresent()
        waitForGitIdle()
    }

    /**
     * Modifies an existing file by clicking on it and adding content
     */
    fun modifyFile(fileName: String, content: String) = step("Modify file: $fileName") {
        writeProjectFile(fileName, content)
        waitForGitIdle()
    }

    /**
     * Performs a Git commit with the given commit message
     */
    fun commitChanges(commitMessage: String) = step("Commit changes with message: $commitMessage") {
        runGitCommand("add", "-A")
        runGitCommand("commit", "-m", commitMessage, "--no-gpg-sign")
        refreshProjectAfterGitCommand()

        waitForNoLocalChanges()
    }

    /**
     * Creates a new Git branch
     */
    fun createBranch(branchName: String) = step("Create branch: $branchName") {
        runGitCommand("checkout", "-B", branchName)
        refreshProjectAfterGitCommand()

        waitForBranch(branchName)
    }

    /**
     * Switches to a Git branch
     */
    fun checkoutBranch(branchName: String) = step("Checkout branch: $branchName") {
        runGitCommand("checkout", branchName)
        refreshProjectAfterGitCommand()

        waitForBranch(branchName)
    }

    fun defaultBranchName(): String = step("Resolve default branch name") {
        val branchName = currentBranchName()
        check(branchName.isNotBlank()) { "Could not resolve current Git branch name" }
        branchName
    }

    fun gitRevision(reference: String): String = step("Resolve git revision for $reference") {
        runGitCommand("rev-parse", reference)
    }

    fun initializeGitRepository() = with(remoteRobot) {
        step("Initialize Git repository") {
            var lastFailure: Throwable? = null

            repeat(3) { attempt ->
                val initialized = runCatching {
                    closeAllEditors()
                    resetProjectFiles()
                    runGitCommand("init")
                    configureGitIdentity()
                    enableGitVcsIntegration()
                    refreshProjectAfterExternalChange()
                    waitForGitRepository()
                    waitForGitIdle()
                }

                if (initialized.isSuccess) {
                    return@step
                }

                lastFailure = initialized.exceptionOrNull()
                if (attempt < 2) {
                    waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(250)) {
                        runCatching {
                            callJs<Boolean>(
                                "com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects().length > 0"
                            )
                        }.getOrDefault(false)
                    }
                }
            }

            throw IllegalStateException("Failed to initialize Git repository after 3 attempts", lastFailure)
        }
    }

    /**
     * Switches to the Project view
     */
    fun switchToProjectView() = with(remoteRobot) {
        step("Switch to Project view") {
            runCatching {
                component("//div[@accessiblename='Project' and @class='SquareStripeButton']")
                    .click()
            }
        }
    }

    /**
     * Deletes a file
     */
    fun deleteFile(fileName: String) = with(remoteRobot) {
        step("Delete file: $fileName") {
            val path = resolveProjectPath(fileName)
            Files.deleteIfExists(path)
            refreshProjectAfterExternalChange()
        }
    }

    /**
     * Renames/Moves a file
     */
    fun renameFile(oldName: String, newName: String) = with(remoteRobot) {
        step("Rename file from $oldName to $newName") {
            val source = resolveProjectPath(oldName)
            val target = resolveProjectPath(newName)
            Files.createDirectories(target.parent ?: projectBasePath())
            Files.deleteIfExists(target)
            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
            refreshProjectAfterExternalChange()
        }
    }

    private fun currentBranchName(): String = with(remoteRobot) {
        runGitCommand("rev-parse", "--abbrev-ref", "HEAD")
    }

    private fun enableGitVcsIntegration() = with(remoteRobot) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        const basePath = project.getBasePath();
                        if (!basePath) {
                            return;
                        }

                        const baseDir = project.getBaseDir();
                        const vcsManager = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project);
                        const vcsManagerEx = com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx.getInstanceEx(project);
                        vcsManager.setDirectoryMapping(basePath, "Git");
                        vcsManager.scheduleMappedRootsUpdate();
                        vcsManagerEx
                            .getConfirmation(com.intellij.openapi.vcs.VcsConfiguration.StandardConfirmation.ADD)
                            .setValue(com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);

                        com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                    }
                }));
            }
            """.trimIndent(),
            true
        )
    }

    private fun configureGitIdentity() = with(remoteRobot) {
        runGitCommand("config", "user.name", "LST-CRC UI Tests")
        runGitCommand("config", "user.email", "lst-crc-ui-tests@example.invalid")
    }

    private fun runGitCommand(vararg args: String): String = with(remoteRobot) {
        val commandArguments = listOf("git", *args).joinToString(", ") { "\"$it\"" }
        waitForGitIdle()

        fun executeGitCommand(): String = callJs<String>(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            let output = "";
            let exitCode = -1;
            if (project) {
                const builder = new java.lang.ProcessBuilder();
                builder.command(java.util.Arrays.asList($commandArguments));
                builder.directory(new java.io.File(project.getBasePath()));
                builder.redirectErrorStream(true);

                const process = builder.start();
                const scanner = new java.util.Scanner(process.getInputStream(), "UTF-8").useDelimiter("\\A");
                output = scanner.hasNext() ? scanner.next() : "";
                scanner.close();

                exitCode = process.waitFor();
            }
            exitCode + "\n" + output.trim();
            """,
            true
        ).trim()

        var result = executeGitCommand()

        val exitCode = result.substringBefore('\n').toIntOrNull()
            ?: error("Could not parse git command exit code from: $result")
        var output = result.substringAfter('\n', "").trim()
        if (exitCode != 0 && output.contains("index.lock")) {
            waitForGitIdle()
            result = executeGitCommand()
            output = result.substringAfter('\n', "").trim()
            val retriedExitCode = result.substringBefore('\n').toIntOrNull()
                ?: error("Could not parse git command exit code from retry result: $result")
            check(retriedExitCode == 0) { if (output.isNotBlank()) output else "git ${args.joinToString(" ")} failed with exit code $retriedExitCode" }
            return@with output
        }

        check(exitCode == 0) { if (output.isNotBlank()) output else "git ${args.joinToString(" ")} failed with exit code $exitCode" }
        output
    }

    private fun writeProjectFile(fileName: String, content: String) = with(remoteRobot) {
        val path = resolveProjectPath(fileName)
        Files.createDirectories(path.parent ?: projectBasePath())
        Files.writeString(
            path,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        refreshProjectAfterExternalChange()
    }

    private fun handleAddFileToGitDialogIfPresent() = with(remoteRobot) {
        val dialogXpath = "//div[@class='MyDialog' and (@title='Add File to Git' or .//div[@accessiblename='Add File to Git'])]"
        val addButtonXpath = "$dialogXpath//div[@accessiblename='Add' and @class='JButton']"
        val dontAskAgainXpath = "$dialogXpath//div[@accessiblename=\"Don't ask again\" and @class='JCheckBox']"

        runCatching {
            waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(250)) {
                val addButtons = findAll<ComponentFixture>(byXpath(addButtonXpath))
                if (addButtons.isEmpty()) {
                    return@waitFor false
                }

                if (!dialogHandled) {
                    findAll<ComponentFixture>(byXpath(dontAskAgainXpath)).firstOrNull()?.click()
                    dialogHandled = true
                }

                addButtons.first().click()
                findAll<ComponentFixture>(byXpath(addButtonXpath)).isEmpty()
            }
        }
    }

    private fun toJsStringLiteral(value: String): String {
        return buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }
    }

    private fun refreshProjectAfterGitCommand() = with(remoteRobot) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({
                    run: function() {
                        const vcsManager = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project);
                        vcsManager.scheduleMappedRootsUpdate();
                        com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                    }
                }));
            }
            """,
            true
        )
    }

    private fun refreshProjectAfterExternalChange() = with(remoteRobot) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        const basePath = project.getBasePath();
                        if (basePath != null) {
                            const fileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance();
                            const normalizedBasePath = String(basePath).split('\\\\').join('/');
                            const projectDir = fileSystem.refreshAndFindFileByPath(normalizedBasePath);
                            if (projectDir != null) {
                                projectDir.refresh(false, true);
                                const gitDir = projectDir.findChild('.git');
                                if (gitDir != null) {
                                    gitDir.refresh(false, true);
                                }
                            }
                        }

                        const vcsManager = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project);
                        vcsManager.scheduleMappedRootsUpdate();
                        com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                    }
                }));
            }
            """.trimIndent(),
            true
        )
    }

    private fun closeAllEditors() = with(remoteRobot) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).closeAllFiles();
            }
            """.trimIndent(),
            true
        )
    }

    private fun projectBasePath(): Path {
        val basePath = with(remoteRobot) {
            callJs<String>(
                """
                (function() {
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    return project ? String(project.getBasePath()) : "";
                })();
                """.trimIndent(),
                true
            )
        }.trim()

        check(basePath.isNotEmpty()) { "Could not resolve project base path" }
        return Path.of(basePath)
    }

    private fun resolveProjectPath(relativePath: String): Path {
        val normalizedPath = relativePath.replace('\\', '/').split('/').filter { it.isNotBlank() }
        var path = projectBasePath()
        normalizedPath.forEach { segment ->
            path = path.resolve(segment)
        }
        return path
    }

    private fun resetProjectFiles() {
        val basePath = projectBasePath()
        val projectFileName = "$${'$'}{basePath.fileName}.iml"
        Files.list(basePath).use { children ->
            children.forEach { child ->
                val childName = child.fileName.toString()
                if (childName != ".idea" && childName != projectFileName) {
                    deleteRecursively(child)
                }
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) {
            return
        }

        if (Files.isDirectory(path)) {
            Files.list(path).use { children ->
                children.forEach { child -> deleteRecursively(child) }
            }
        }

        Files.deleteIfExists(path)
    }

    private fun waitForGitRepository() = with(remoteRobot) {
        waitFor(Duration.ofSeconds(30), interval = Duration.ofSeconds(1)) {
            runCatching {
                callJs<Boolean>(
                    """
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) {
                        false;
                    }
                    else {
                        const basePath = project.getBasePath();
                        if (!basePath) {
                            false;
                        }
                        else {
                            const baseDir = project.getBaseDir();
                            const vcsManager = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project);
                            const gitDir = new java.io.File(basePath, ".git");
                            const statusBuilder = new java.lang.ProcessBuilder();
                            statusBuilder.command(java.util.Arrays.asList("git", "status", "--porcelain"));
                            statusBuilder.directory(new java.io.File(basePath));
                            statusBuilder.redirectErrorStream(true);
                            const statusProcess = statusBuilder.start();
                            const statusExitCode = statusProcess.waitFor();
                            gitDir.exists() && baseDir != null && vcsManager.checkVcsIsActive("Git") && vcsManager.getVcsFor(baseDir) != null && statusExitCode === 0;
                        }
                    }
                    """.trimIndent(),
                    true
                )
            }.getOrDefault(false)
        }
    }

    private fun waitForGitIdle() = with(remoteRobot) {
        val timeout = if (System.getenv("GITHUB_ACTIONS") == "true") Duration.ofSeconds(60) else Duration.ofSeconds(20)
        waitFor(timeout, interval = Duration.ofMillis(500)) {
            runCatching {
                callJs<Boolean>(
                    """
                    const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                    if (!project) {
                        false;
                    }
                    else {
                        const basePath = project.getBasePath();
                        if (!basePath) {
                            false;
                        }
                        else {
                            const gitDir = new java.io.File(basePath, ".git");
                            if (!gitDir.exists()) {
                                true;
                            }
                            else {
                                const lockFile = new java.io.File(gitDir, "index.lock");
                                if (lockFile.exists()) {
                                    false;
                                }
                                else {
                                    const builder = new java.lang.ProcessBuilder();
                                    builder.command(java.util.Arrays.asList("git", "status", "--porcelain"));
                                    builder.directory(new java.io.File(basePath));
                                    builder.redirectErrorStream(true);
                                    const process = builder.start();
                                    process.waitFor() === 0;
                                }
                            }
                        }
                    }
                    """.trimIndent(),
                    true
                )
            }.getOrDefault(false)
        }
    }

    private fun waitForBranch(branchName: String) = waitFor(Duration.ofSeconds(30), interval = Duration.ofSeconds(1)) {
        currentBranchName() == branchName
    }

    private fun waitForNoLocalChanges() = waitFor(Duration.ofSeconds(30), interval = Duration.ofSeconds(1)) {
        runGitCommand("status", "--porcelain")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .none { line ->
                val path = line.substringAfter(' ').substringAfter(' ').trim()
                !path.startsWith(".idea/") && !path.endsWith(".iml")
            }
    }
}
