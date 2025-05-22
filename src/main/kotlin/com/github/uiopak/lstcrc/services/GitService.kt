package com.github.uiopak.lstcrc.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    private val logger = thisLogger()

    private fun getCurrentRepository(): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories
        return when {
            repositories.isEmpty() -> {
                logger.warn("No Git repositories found in the project.")
                null
            }
            repositories.size > 1 -> {
                logger.info("Multiple Git repositories found. Using the first one: ${repositories.first().root.path}")
                repositories.first() // Or implement logic to select one
            }
            else -> repositories.first()
        }
    }

    fun getLocalBranches(): List<String> {
        val repository = getCurrentRepository() ?: return emptyList()
        return repository.branches.localBranches.map { it.name }
    }

    fun getRemoteBranches(): List<String> {
        val repository = getCurrentRepository() ?: return emptyList()
        return repository.branches.remoteBranches.map { it.name }
    }

    fun getAllBranches(): List<String> {
        val repository = getCurrentRepository() ?: return emptyList()
        return (repository.branches.localBranches + repository.branches.remoteBranches)
            .map { it.name }
            .distinct()
            .sorted() // Optional: sort for better UX
    }

    fun getCurrentBranch(): String? {
        val repository = getCurrentRepository() ?: return null
        return repository.currentBranchName
    }

    /**
     * Gets the changes between the current HEAD and the specified branch name.
     * These changes represent what is in `branchNameToCompare` that is different from HEAD.
     * - Files added in `branchNameToCompare` (not in HEAD) will be `Change.Type.NEW`.
     * - Files deleted in `branchNameToCompare` (present in HEAD) will be `Change.Type.DELETED`.
     * - Files modified will be `Change.Type.MODIFICATION`.
     */
    fun getChanges(branchNameToCompare: String): List<Pair<String, String>> {
        val repository = getCurrentRepository() ?: return emptyList()
        val projectRoot = repository.root

        logger.info("Getting changes between HEAD and branch: $branchNameToCompare for repository ${projectRoot.path}")

        val handler = GitLineHandler(project, projectRoot, GitCommand.DIFF)
        handler.setSilent(true)
        handler.addParameters("--name-status", "HEAD", branchNameToCompare)

        val result = Git.getInstance().runCommand(handler)
        if (result.success()) {
            return result.output.mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    val changeType = parts[0]
                    val filePath = parts[1]
                    changeType to filePath
                } else null
            }
        } else {
            logger.error("Git diff failed: ${result.errorOutput.joinToString()}")
            return emptyList()
        }
    }
}