package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
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
    // Using the simple class name for the logger category makes it identifiable.
    protected val logger: Logger = Logger.getInstance("#${LstCrcPackageSet::class.java.name}.${this::class.simpleName}")

    abstract fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile>

    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        if (project.isDisposed) return false
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        // When the "HEAD" tab is active, its changes are standard VCS changes, not part of a specific
        // branch comparison for this plugin's purpose. Therefore, LSTCRC scopes should be empty.
        // We identify the HEAD tab by checking if the active branch name in the service is "HEAD" or null.
        if (diffDataService.activeBranchName == "HEAD" || diffDataService.activeBranchName == null) {
            return false
        }

        val relevantFiles = getRelevantFiles(diffDataService)
        val result = file in relevantFiles
        // Example for debugging, consider using trace level:
        // logger.trace("ScopeCheck (${this::class.simpleName}): '${file.name}' in scope? $result. (Service has ${relevantFiles.size} files. Active branch: ${diffDataService.activeBranchName})")
        return result
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
    description = "Files newly added in the current LSTCRC branch comparison."
) {
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> = service.createdFiles.toSet()
    override fun createCopy(): PackageSet = CreatedFilesPackageSet()
}

private class ModifiedFilesPackageSet : LstCrcPackageSet(
    description = "Files modified in the current LSTCRC branch comparison."
) {
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> = service.modifiedFiles.toSet()
    override fun createCopy(): PackageSet = ModifiedFilesPackageSet()
}

private class MovedFilesPackageSet : LstCrcPackageSet(
    description = "Files moved or renamed in the current LSTCRC branch comparison."
) {
    override fun getRelevantFiles(service: ProjectActiveDiffDataService): Set<VirtualFile> = service.movedFiles.toSet()
    override fun createCopy(): PackageSet = MovedFilesPackageSet()
}

private class ChangedFilesPackageSet : LstCrcPackageSet(
    description = "All files created, modified, or moved in the current LSTCRC branch comparison."
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
    "LSTCRC: Created Files", // Scope ID and UI Name
    AllIcons.Actions.Diff, // Icon
    CreatedFilesPackageSet()      // PackageSet implementation
){
    override fun getDefaultColorName(): String = "Green"
}

class ModifiedFilesScope : NamedScope(
    "LSTCRC: Modified Files",
    AllIcons.Actions.Diff,
    ModifiedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Blue"
}

class MovedFilesScope : NamedScope(
    "LSTCRC: Moved Files",
    AllIcons.Actions.Diff,
    MovedFilesPackageSet()
){
    override fun getDefaultColorName(): String = "Gray"
}


class ChangedFilesScope : NamedScope(
    "LSTCRC: Changed Files",
    AllIcons.Actions.Diff,
    ChangedFilesPackageSet()
)