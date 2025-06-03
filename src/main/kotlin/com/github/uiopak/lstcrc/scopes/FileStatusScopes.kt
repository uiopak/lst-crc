package com.github.uiopak.lstcrc.scopes

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase

class CreatedFilesScope : NamedScope("Created Files", AllIcons.General.Information, object : PackageSetBase() {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        return file in diffDataService.createdFiles
    }

    override fun createCopy(): PackageSet {
        return this
    }

    override fun getText(): String {
        return "Files created in the current branch"
    }

    override fun getNodePriority(): Int = 0
})

class ModifiedFilesScope : NamedScope("Modified Files", AllIcons.General.Information, object : PackageSetBase() {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        return file in diffDataService.modifiedFiles
    }

    override fun createCopy(): PackageSet {
        return this
    }

    override fun getText(): String {
        return "Files modified in the current branch"
    }

    override fun getNodePriority(): Int = 0
})

class MovedFilesScope : NamedScope("Moved Files", AllIcons.General.Information, object : PackageSetBase() {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        return file in diffDataService.movedFiles
    }

    override fun createCopy(): PackageSet {
        return this
    }

    override fun getText(): String {
        return "Files moved in the current branch"
    }

    override fun getNodePriority(): Int = 0
})

class ChangedFilesScope : NamedScope("Changed Files", AllIcons.General.Information, object : PackageSetBase() {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        val diffDataService = project.service<ProjectActiveDiffDataService>()
        return file in diffDataService.createdFiles ||
               file in diffDataService.modifiedFiles ||
               file in diffDataService.movedFiles
    }

    override fun createCopy(): PackageSet {
        return this
    }

    override fun getText(): String {
        return "All files changed in the current branch"
    }

    override fun getNodePriority(): Int = 0
})
