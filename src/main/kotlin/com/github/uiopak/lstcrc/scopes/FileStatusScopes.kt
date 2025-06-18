package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.resources.LstCrcIcons
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase

private abstract class LstCrcPackageSet(
    /** The description passed here is used for [getText], which appears in the "Pattern" field of the Scopes dialog. */
    private val description: String
) : PackageSetBase() {

    abstract fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile>

    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        if (project.isDisposed) return false
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        // The ProjectActiveDiffDataService is the single source of truth for scope data.
        // If activeBranchName is null, it means diff data is intentionally cleared,
        // so no files should be included in any LSTCRC scope.
        if (diffDataService.activeBranchName == null) {
            return false
        }

        val relevantFiles = getRelevantFiles(diffDataService)
        return file in relevantFiles
    }

    override fun getText(): String = description

    // Custom scopes should have a higher priority than built-in scopes (0).
    override fun getNodePriority(): Int = 1
}

private class CreatedFilesPackageSet : LstCrcPackageSet(
    description = LstCrcBundle.message("scope.created.description")
) {
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> = service.createdFiles.toSet()
    override fun createCopy(): PackageSet = CreatedFilesPackageSet()
}

private class ModifiedFilesPackageSet : LstCrcPackageSet(
    description = LstCrcBundle.message("scope.modified.description")
) {
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> = service.modifiedFiles.toSet()
    override fun createCopy(): PackageSet = ModifiedFilesPackageSet()
}

private class MovedFilesPackageSet : LstCrcPackageSet(
    description = LstCrcBundle.message("scope.moved.description")
) {
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> = service.movedFiles.toSet()
    override fun createCopy(): PackageSet = MovedFilesPackageSet()
}

private class ChangedFilesPackageSet : LstCrcPackageSet(
    description = LstCrcBundle.message("scope.changed.description")
) {
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> {
        return (service.createdFiles + service.modifiedFiles + service.movedFiles).toSet()
    }
    override fun createCopy(): PackageSet = ChangedFilesPackageSet()
}

class CreatedFilesScope : NamedScope(
    LstCrcBundle.message("scope.created.name"),
    AllIcons.General.Add,
    CreatedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Green"
}

class ModifiedFilesScope : NamedScope(
    LstCrcBundle.message("scope.modified.name"),
    AllIcons.Actions.EditSource,
    ModifiedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Blue"
}

class MovedFilesScope : NamedScope(
    LstCrcBundle.message("scope.moved.name"),
    AllIcons.Nodes.Tag,
    MovedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Gray"
}


class ChangedFilesScope : NamedScope(
    LstCrcBundle.message("scope.changed.name"),
    LstCrcIcons.TOOL_WINDOW, // Use our custom plugin icon for the main scope for brand consistency.
    ChangedFilesPackageSet()
)