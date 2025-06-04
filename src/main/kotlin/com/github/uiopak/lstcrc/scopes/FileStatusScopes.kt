package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.openapi.diagnostic.thisLogger // Import logger

// Defines a scope for files that have been newly created in the active branch comparison.
class CreatedFilesScope : NamedScope(
    "Created Files", // Name displayed in the UI
    AllIcons.General.Information, // Icon for the scope
    object : PackageSetBase() { // Logic to determine if a file is in this scope
        private val scopeLogger = thisLogger() // Logger for this specific PackageSet

        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val result = file in diffDataService.createdFiles
            // Log only if the result is true, or for a specific file if debugging intensely, to reduce noise.
            // For now, let's log every check to see frequency.
            scopeLogger.debug("ScopeCheck: CreatedFilesScope for '${file.name}'. Result: $result. (Service has ${diffDataService.createdFiles.size} created files. Active branch: ${diffDataService.activeBranchName})")
            return result
        }

        override fun createCopy(): PackageSet = this // Returns a copy of this package set

        override fun getText(): String = "Files created in the current branch" // Description for the scope

        override fun getNodePriority(): Int = 0 // Priority for display order (lower is higher)
    }
)

// Defines a scope for files that have been modified in the active branch comparison.
class ModifiedFilesScope : NamedScope(
    "Modified Files",
    AllIcons.General.Information,
    object : PackageSetBase() {
        private val scopeLogger = thisLogger()
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val result = file in diffDataService.modifiedFiles
            scopeLogger.debug("ScopeCheck: ModifiedFilesScope for '${file.name}'. Result: $result. (Service has ${diffDataService.modifiedFiles.size} modified files. Active branch: ${diffDataService.activeBranchName})")
            return result
        }

        override fun createCopy(): PackageSet = this

        override fun getText(): String = "Files modified in the current branch"

        override fun getNodePriority(): Int = 0
    }
)

// Defines a scope for files that have been moved (renamed) in the active branch comparison.
class MovedFilesScope : NamedScope(
    "Moved Files",
    AllIcons.General.Information,
    object : PackageSetBase() {
        private val scopeLogger = thisLogger()
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val result = file in diffDataService.movedFiles
            scopeLogger.debug("ScopeCheck: MovedFilesScope for '${file.name}'. Result: $result. (Service has ${diffDataService.movedFiles.size} moved files. Active branch: ${diffDataService.activeBranchName})")
            return result
        }

        override fun createCopy(): PackageSet = this

        override fun getText(): String = "Files moved in the current branch"

        override fun getNodePriority(): Int = 0
    }
)

// Defines a scope encompassing all files that are created, modified, or moved in the active branch comparison.
class ChangedFilesScope : NamedScope(
    "Changed Files",
    AllIcons.General.Information,
    object : PackageSetBase() {
        private val scopeLogger = thisLogger()
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            val isCreated = file in diffDataService.createdFiles
            val isModified = file in diffDataService.modifiedFiles
            val isMoved = file in diffDataService.movedFiles
            val result = isCreated || isModified || isMoved
            scopeLogger.debug("ScopeCheck: ChangedFilesScope for '${file.name}'. Result: $result (C:$isCreated, M:$isModified, V:$isMoved. Active branch: ${diffDataService.activeBranchName})")
            return result
        }

        override fun createCopy(): PackageSet = this

        override fun getText(): String = "All files changed in the current branch"

        override fun getNodePriority(): Int = 0
    }
)