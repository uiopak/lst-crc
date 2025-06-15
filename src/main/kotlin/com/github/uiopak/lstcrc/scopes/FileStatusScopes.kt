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

// --- Base PackageSet for LSTCRC Scopes ---
private abstract class LstCrcPackageSet(
    // The description will be used for getText(), which appears in the "Pattern" field
    // of the Scopes dialog.
    private val description: String
) : PackageSetBase() {

    abstract fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile>

    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        if (project.isDisposed) return false
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        // The ProjectActiveDiffDataService is the single source of truth.
        // Its state is managed by other services based on tab selection and settings.
        // If activeBranchName is null, it means we have explicitly cleared the diff data,
        // and therefore no files should be in any LSTCRC scope.
        if (diffDataService.activeBranchName == null) {
            return false
        }

        // If a branch is active (including "HEAD" if the setting is on), we check its files.
        val relevantFiles = getRelevantFiles(diffDataService)
        return file in relevantFiles
    }

    /**
     * Returns the text representation of the package set.
     * This text is displayed in the "Pattern" field of the Scopes dialog.
     */
    override fun getText(): String = description

    override fun getNodePriority(): Int = 1 // Custom scopes, typically higher priority than 0 (built-ins)
}

// --- Specific PackageSet Implementations ---
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
    // Overriding getRelevantFiles for combined logic
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> {
        return (service.createdFiles + service.modifiedFiles + service.movedFiles).toSet()
    }
    override fun createCopy(): PackageSet = ChangedFilesPackageSet()
}


// --- NamedScope Definitions (using prefixed IDs) ---
// The 'name' parameter is the Scope ID and its default display name in UI lists.
class CreatedFilesScope : NamedScope(
    LstCrcBundle.message("scope.created.name"),
    AllIcons.General.Add, // Changed to a more specific icon
    CreatedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Green"
}

class ModifiedFilesScope : NamedScope(
    LstCrcBundle.message("scope.modified.name"),
    AllIcons.Actions.EditSource, // Changed to a more specific icon
    ModifiedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Blue"
}

class MovedFilesScope : NamedScope(
    LstCrcBundle.message("scope.moved.name"),
    AllIcons.Nodes.Tag, // CORRECTED: This icon represents renaming/tagging and is a good fit for "Moved".
    MovedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Gray"
}


class ChangedFilesScope : NamedScope(
    LstCrcBundle.message("scope.changed.name"),
    LstCrcIcons.TOOL_WINDOW, // Use our custom plugin icon for consistency
    ChangedFilesPackageSet()
)