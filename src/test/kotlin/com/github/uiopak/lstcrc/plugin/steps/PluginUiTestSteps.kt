package com.github.uiopak.lstcrc.plugin.steps

import com.github.uiopak.lstcrc.plugin.pages.idea
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.component
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Helper class for UI test interactions in PluginUiTest
 */
class PluginUiTestSteps(private val remoteRobot: RemoteRobot) {
    companion object {
        // Flag to track whether we've already handled the "Don't ask again" dialog
        private var dialogHandled = false
    }

    /**
     * Creates a new file with the given name and content
     */
    fun createNewFile(fileName: String, content: String) = with(remoteRobot) {
        step("Create new file: $fileName") {
            writeProjectFile(fileName, content)
            handleAddFileToGitDialogIfPresent()
        }
    }

    /**
     * Modifies an existing file by clicking on it and adding content
     */
    fun modifyFile(fileName: String, content: String) = with(remoteRobot) {
        step("Modify file: $fileName") {
            writeProjectFile(fileName, content)
        }
    }

    /**
     * Replaces content in an existing file
     */
    fun replaceFileContent(fileName: String, content: String) = with(remoteRobot) {
        step("Replace content in file: $fileName") {
            step("Left click on $fileName") {
                component("//div[@accessiblename='$fileName' and @class='SimpleColoredComponent']")
                    .click()
            }
            keyboard {
                step("Press 'Ctrl+A'") { hotKey(17, 65) }

                // Split content by newlines and enter each line separately
                val lines = content.split("\n")
                for (i in lines.indices) {
                    enterText(lines[i])
                    if (i < lines.size - 1) {
                        // Press Enter between lines, but not after the last line
                        enter()
                    }
                }
            }
        }
    }

    /**
     * Performs a Git commit with the given commit message
     */
    fun commitChanges(commitMessage: String) = with(remoteRobot) {
        step("Commit changes with message: $commitMessage") {
            runGitCommand("add", "-A")
            runGitCommand("commit", "-m", commitMessage, "--no-gpg-sign")
            refreshProjectAfterGitCommand()

            waitForNoLocalChanges()
        }
    }

    /**
     * Creates a new Git branch
     */
    fun createBranch(branchName: String) = with(remoteRobot) {
        step("Create branch: $branchName") {
            runGitCommand("checkout", "-B", branchName)
            refreshProjectAfterGitCommand()

            waitForBranch(branchName)
        }
    }

    /**
     * Switches to a Git branch
     */
    fun checkoutBranch(branchName: String) = with(remoteRobot) {
        step("Checkout branch: $branchName") {
            runGitCommand("checkout", branchName)
            refreshProjectAfterGitCommand()

            waitForBranch(branchName)
        }
    }

    fun defaultBranchName(): String = with(remoteRobot) {
        step("Resolve default branch name") {
            val branchName = currentBranchName()
            check(branchName.isNotBlank()) { "Could not resolve current Git branch name" }
            branchName
        }
    }

    fun gitRevision(reference: String): String = with(remoteRobot) {
        step("Resolve git revision for $reference") {
            runGitCommand("rev-parse", reference)
        }
    }

    fun initializeGitRepository() = with(remoteRobot) {
        step("Initialize Git repository") {
            runJs(
                """
                const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
                function deleteRecursively(file) {
                    if (file.isDirectory()) {
                        const children = file.listFiles();
                        if (children) {
                            for (let i = 0; i < children.length; i++) {
                                deleteRecursively(children[i]);
                            }
                        }
                    }

                    if (!file.delete() && file.exists()) {
                        throw new java.io.IOException("Could not delete " + file.getAbsolutePath());
                    }
                }

                const basePathFile = new java.io.File(project.getBasePath());
                const projectFileName = project.getName() + ".iml";
                const children = basePathFile.listFiles();
                if (children) {
                    for (let i = 0; i < children.length; i++) {
                        const child = children[i];
                        const childName = child.getName();
                        if (childName !== ".idea" && childName !== projectFileName) {
                            deleteRecursively(child);
                        }
                    }
                }
                """.trimIndent(),
                true
            )
            runGitCommand("init")
            refreshProjectAfterGitCommand()
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
            runProjectWriteOperation(
                """
                const file = baseDir.findChild(${toJsStringLiteral(fileName)});
                if (file) {
                    file.delete(null);
                }
                """.trimIndent()
            )
        }
    }

    /**
     * Renames/Moves a file
     */
    fun renameFile(oldName: String, newName: String) = with(remoteRobot) {
        step("Rename file from $oldName to $newName") {
            runProjectWriteOperation(
                """
                const file = baseDir.findChild(${toJsStringLiteral(oldName)});
                const existingTarget = baseDir.findChild(${toJsStringLiteral(newName)});
                if (existingTarget) {
                    existingTarget.delete(null);
                }
                if (file) {
                    file.rename(null, ${toJsStringLiteral(newName)});
                }
                """.trimIndent()
            )
        }
    }

    private fun currentBranchName(): String = with(remoteRobot) {
        runGitCommand("rev-parse", "--abbrev-ref", "HEAD")
    }

    private fun runGitCommand(vararg args: String): String = with(remoteRobot) {
        val commandArguments = listOf("git", *args).joinToString(", ") { "\"$it\"" }
        fun executeGitCommand(): String = callJs<String>(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            let output = "";
            let exitCode = -1;
            if (project) {
                const lockFile = new java.io.File(project.getBasePath() + "/.git/index.lock");
                if (lockFile.exists()) {
                    lockFile.delete();
                }

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
        runProjectWriteOperation(
            """
            let file = baseDir.findChild(${toJsStringLiteral(fileName)});
            if (!file) {
                file = baseDir.createChildData(null, ${toJsStringLiteral(fileName)});
            }
            com.intellij.openapi.vfs.VfsUtil.saveText(file, ${toJsStringLiteral(content)});
            """.trimIndent()
        )
    }

    private fun handleAddFileToGitDialogIfPresent() = with(remoteRobot) {
        runCatching {
            waitFor(Duration.ofSeconds(5), interval = Duration.ofMillis(250)) {
                val addButtons = findAll<ComponentFixture>(byXpath("//div[@accessiblename='Add' and @class='JButton']"))
                if (addButtons.isEmpty()) {
                    return@waitFor false
                }

                if (!dialogHandled) {
                    runCatching {
                        find<ComponentFixture>(byXpath("//div[@accessiblename='Don\'t ask again' and @class='JCheckBox']")).click()
                    }
                    dialogHandled = true
                }

                addButtons.first().click()
                true
            }
        }
    }

    private fun runProjectWriteOperation(operationScript: String) = with(remoteRobot) {
        runJs(
            """
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (project) {
                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, new java.lang.Runnable({
                    run: function() {
                        const baseDir = project.getBaseDir();
                        $operationScript
                        com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                    }
                }));
            }
            """,
            true
        )
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
                        const repositoryManager = git4idea.repo.GitRepositoryManager.getInstance(project);
                        const repositories = repositoryManager.getRepositories();
                        for (let i = 0; i < repositories.size(); i++) {
                            repositories.get(i).update();
                        }
                        com.intellij.openapi.vcs.changes.ChangeListManager.getInstance(project).scheduleUpdate();
                        com.intellij.openapi.vcs.changes.VcsDirtyScopeManager.getInstance(project).markEverythingDirty();
                    }
                }));
            }
            """,
            true
        )
    }

    private fun waitForBranch(branchName: String) = with(remoteRobot) {
        waitFor(Duration.ofSeconds(30), interval = Duration.ofSeconds(1)) {
            currentBranchName() == branchName
        }
    }

    private fun waitForNoLocalChanges() = with(remoteRobot) {
        waitFor(Duration.ofSeconds(30), interval = Duration.ofSeconds(1)) {
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
}
