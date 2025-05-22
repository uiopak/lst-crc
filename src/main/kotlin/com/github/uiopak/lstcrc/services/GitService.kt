package com.github.uiopak.lstcrc.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change

@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {

    fun getLocalBranches(): List<String> {
        // Mock implementation for demonstration
        return listOf("main", "develop", "feature/new-ui")
    }

    fun getRemoteBranches(): List<String> {
        // Mock implementation for demonstration
        return listOf("origin/main", "origin/develop", "origin/feature/new-ui")
    }

    fun getAllBranches(): List<String> {
        return getLocalBranches() + getRemoteBranches()
    }

    fun getCurrentBranch(): String? {
        // Mock implementation for demonstration
        return "main"
    }

    fun getChanges(branchName: String): List<Change> {
        // For demonstration purposes, we're just returning an empty list
        thisLogger().info("Getting changes for branch: $branchName")
        return emptyList()
    }
}
