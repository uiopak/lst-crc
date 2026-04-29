package com.github.uiopak.lstcrc.plugin.steps

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.component
import com.intellij.remoterobot.utils.waitFor
import java.nio.file.AccessDeniedException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.FileSystemException
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
        createNewFile(fileName, content, stage = true)
    }

    fun createNewFile(fileName: String, content: String, stage: Boolean) = step("Create new file: $fileName") {
        writeProjectFile(fileName, content)
        if (stage) {
            stageAllGitChanges()
        }
        handleAddFileToGitDialogIfPresent()
        waitForGitIdle()
    }

    /**
     * Modifies an existing file by clicking on it and adding content
     */
    fun modifyFile(fileName: String, content: String) = step("Modify file: $fileName") {
        writeProjectFile(fileName, content)
        stageAllGitChanges()
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
                    saveAllDocuments()
                    closeAllEditors()
                    waitForGitIdle()
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
            stageAllGitChanges()
            waitForGitIdle()
        }
    }

    /**
     * Renames/Moves a file
     */
    fun renameFile(oldName: String, newName: String) = with(remoteRobot) {
        step("Rename file from $oldName to $newName") {
            val oldPathLiteral = toJsStringLiteral(oldName.replace('\\', '/'))
            val newPathLiteral = toJsStringLiteral(newName.replace('\\', '/'))
            runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                if (project) {
                    const oldPath = $oldPathLiteral;
                    const newPath = $newPathLiteral;
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                        run: function() {
                            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, new java.lang.Runnable({
                                run: function() {
                                    const baseDir = project.getBaseDir();
                                    if (!baseDir) {
                                        return;
                                    }

                                    const source = baseDir.findFileByRelativePath(oldPath);
                                    if (!source) {
                                        return;
                                    }

                                    const targetParentPath = newPath.lastIndexOf('/') >= 0 ? newPath.substring(0, newPath.lastIndexOf('/')) : "";
                                    const targetFileName = newPath.lastIndexOf('/') >= 0 ? newPath.substring(newPath.lastIndexOf('/') + 1) : newPath;
                                    const targetParent = targetParentPath
                                        ? com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing(baseDir, targetParentPath)
                                        : baseDir;

                                    if (!targetParent) {
                                        return;
                                    }

                                    const existingTarget = targetParent.findChild(targetFileName);
                                    if (existingTarget) {
                                        existingTarget.delete(this);
                                    }
                                    if (source.getParent() !== targetParent) {
                                        source.move(this, targetParent);
                                    }
                                    if (String(source.getName()) !== targetFileName) {
                                        source.rename(this, targetFileName);
                                    }

                                    com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                                }
                            }));
                        }
                    }));

                    const changeListManagerEx = com.intellij.openapi.vcs.changes.ChangeListManagerEx.getInstanceEx(project);
                    changeListManagerEx.waitForUpdate();
                }
                """.trimIndent(),
                false
            )
            stageAllGitChanges()
            waitForGitIdle()
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
                            .setValue(com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);

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
        val normalizedPath = fileName.replace('\\', '/')
        val pathLiteral = toJsStringLiteral(normalizedPath)
        val contentLiteral = toJsStringLiteral(content)

        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                const relativePath = $pathLiteral;
                const fileContent = $contentLiteral;
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, new java.lang.Runnable({
                            run: function() {
                                const baseDir = project.getBaseDir();
                                if (!baseDir) {
                                    return;
                                }

                                const parentPath = relativePath.lastIndexOf('/') >= 0
                                    ? relativePath.substring(0, relativePath.lastIndexOf('/'))
                                    : "";
                                const fileNameOnly = relativePath.lastIndexOf('/') >= 0
                                    ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                                    : relativePath;
                                const parentDir = parentPath
                                    ? com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing(baseDir, parentPath)
                                    : baseDir;
                                if (!parentDir) {
                                    return;
                                }

                                const file = parentDir.findChild(fileNameOnly) || parentDir.createChildData(this, fileNameOnly);
                                com.intellij.openapi.vfs.VfsUtil.saveText(file, fileContent);
                                com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                            }
                        }));
                    }
                }));

                const changeListManagerEx = com.intellij.openapi.vcs.changes.ChangeListManagerEx.getInstanceEx(project);
                changeListManagerEx.waitForUpdate();

                const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                if (plugin != null) {
                    const stateServiceClass = plugin.getPluginClassLoader()
                        .loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                    const stateService = project.getService(stateServiceClass);
                    if (stateService != null) {
                        stateService.refreshDataForCurrentSelection().join();
                    }
                }
            }
            """.trimIndent(),
            false
        )
    }

    private fun gitPath(relativePath: String): String = relativePath.replace('\\', '/')

    private fun waitForGitChange(vararg relativePaths: String) {
        val normalizedPaths = relativePaths
            .map(::gitPath)
            .distinct()

        waitFor(Duration.ofSeconds(30), interval = Duration.ofMillis(500)) {
            runCatching {
                val statusOutput = runGitCommand("status", "--porcelain")
                normalizedPaths.any { path ->
                    statusOutput.lineSequence().any { line ->
                        val trimmedLine = line.trim()
                        trimmedLine.endsWith(path) ||
                            trimmedLine.contains(" -> $path") ||
                            trimmedLine.contains("$path -> ")
                    }
                }
            }.getOrDefault(false)
        }
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
                const vcsManager = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project);
                vcsManager.scheduleMappedRootsUpdate();
                com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();

                const changeListManagerEx = com.intellij.openapi.vcs.changes.ChangeListManagerEx.getInstanceEx(project);
                changeListManagerEx.waitForUpdate();

                const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                if (plugin != null) {
                    const stateServiceClass = plugin.getPluginClassLoader()
                        .loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                    const stateService = project.getService(stateServiceClass);
                    if (stateService != null) {
                        stateService.refreshDataForCurrentSelection().join();
                    }
                }
            }
            """,
            false
        )
    }

    private fun stageAllGitChanges() {
        runGitCommand("add", "-A")
        refreshProjectAfterGitCommand()
    }

    private fun refreshProjectAfterExternalChange() = with(remoteRobot) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
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

                const changeListManagerEx = com.intellij.openapi.vcs.changes.ChangeListManagerEx.getInstanceEx(project);
                changeListManagerEx.waitForUpdate();

                const pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.uiopak.lstcrc");
                const plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId);
                if (plugin != null) {
                    const stateServiceClass = plugin.getPluginClassLoader()
                        .loadClass("com.github.uiopak.lstcrc.services.ToolWindowStateService");
                    const stateService = project.getService(stateServiceClass);
                    if (stateService != null) {
                        stateService.refreshDataForCurrentSelection().join();
                    }
                }
            }
            """.trimIndent(),
            false
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

    private fun saveAllDocuments() = with(remoteRobot) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(new java.lang.Runnable({
                run: function() {
                    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, new java.lang.Runnable({
                        run: function() {
                            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments();
                        }
                    }));
                }
            }));
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

        deleteWithRetries(path)
    }

    private fun deleteWithRetries(path: Path) {
        var lastFailure: Exception? = null

        repeat(20) { attempt ->
            try {
                Files.deleteIfExists(path)
                return
            } catch (exception: AccessDeniedException) {
                lastFailure = exception
            } catch (exception: FileSystemException) {
                lastFailure = exception
            }

            if (attempt < 19) {
                Thread.sleep(250)
            }
        }

        throw lastFailure ?: IllegalStateException("Failed to delete $path")
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
                val path = normalizedStatusPath(line)
                !isIgnoredUiTestPath(path)
            }
    }

    private fun normalizedStatusPath(statusLine: String): String {
        val pathPortion = statusLine.drop(3).trim()
        return pathPortion.substringAfter("->", pathPortion).trim().replace('\\', '/')
    }

    private fun isIgnoredUiTestPath(path: String): Boolean {
        return path.startsWith(".idea/") ||
            path.startsWith(".kotlin/") ||
            path.startsWith(".fleet/") ||
            path == ".name" ||
            path.endsWith(".iml")
    }
}
