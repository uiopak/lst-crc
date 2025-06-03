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

// Defines a scope for files that have been newly created in the active branch comparison.
class CreatedFilesScope : NamedScope(
    "Created Files", // Name displayed in the UI
    AllIcons.General.Information, // Icon for the scope
    object : PackageSetBase() { // Logic to determine if a file is in this scope
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            return file in diffDataService.createdFiles
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
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            return file in diffDataService.modifiedFiles
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
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            return file in diffDataService.movedFiles
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
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
            val diffDataService = project.service<ProjectActiveDiffDataService>()
            return file in diffDataService.createdFiles ||
                    file in diffDataService.modifiedFiles ||
                    file in diffDataService.movedFiles
        }

        override fun createCopy(): PackageSet = this

        override fun getText(): String = "All files changed in the current branch"

        override fun getNodePriority(): Int = 0
    }
)
