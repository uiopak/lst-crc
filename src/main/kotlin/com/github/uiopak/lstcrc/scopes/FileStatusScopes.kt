package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase

/**
 * A generic [PackageSet] implementation for LST-CRC scopes. It determines file inclusion based on a provided lambda,
 * reducing boilerplate for different change types.
 */
private class LstCrcPackageSet(
    private val descriptionKey: String, // Store the key, not the message
    private val filesExtractor: (ProjectActiveDiffDataService) -> Set<VirtualFile>,
    private val pathsExtractor: ((ProjectActiveDiffDataService) -> Set<String>)? = null // For deleted files that need path-based matching
) : PackageSetBase() {

    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        if (project.isDisposed) return false
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        // If activeBranchName is null, it means diff data is intentionally cleared,
        // so no files should be included in any LSTCRC scope.
        if (diffDataService.activeBranchName == null) {
            return false
        }

        // For deleted files scope or VcsVirtualFile, use path-based matching
        // since file instances differ between tree nodes and opened editors
        return if (pathsExtractor != null || file is com.intellij.openapi.vcs.vfs.VcsVirtualFile) {
            val paths = pathsExtractor?.invoke(diffDataService)
                ?: filesExtractor(diffDataService).mapTo(HashSet()) { it.path }
            file.path in paths
        } else {
            file in filesExtractor(diffDataService)
        }
    }

    override fun createCopy(): PackageSet = LstCrcPackageSet(descriptionKey, filesExtractor, pathsExtractor)
    // Defer the call to LstCrcBundle until getText() is actually called by the UI
    override fun getText(): String = LstCrcBundle.message(descriptionKey)
    override fun getNodePriority(): Int = 1
}

// Instantiate the generic PackageSet for each change type, using pre-computed sets from the service.
private val createdFilesPackageSet = LstCrcPackageSet(
    "scope.created.description",
    filesExtractor = { it.createdFilesSet }
)

private val modifiedFilesPackageSet = LstCrcPackageSet(
    "scope.modified.description",
    filesExtractor = { it.modifiedFilesSet }
)

private val movedFilesPackageSet = LstCrcPackageSet(
    "scope.moved.description",
    filesExtractor = { it.movedFilesSet }
)

private val deletedFilesPackageSet = LstCrcPackageSet(
    "scope.deleted.description",
    filesExtractor = { it.createdFilesSet }, // unused for path matching, but required by signature
    pathsExtractor = { it.deletedFilePaths }
)

private val changedFilesPackageSet = LstCrcPackageSet(
    "scope.changed.description",
    filesExtractor = { it.changedFilesSet }
)


/**
 * A `NamedScope` that includes all files newly created in the active LST-CRC comparison.
 */
class CreatedFilesScope : NamedScope(
    "LSTCRC.Created", // scopeId (stable, non-localized)
    { LstCrcBundle.message("scope.created.name") }, // presentableNameSupplier
    AllIcons.General.Add,
    createdFilesPackageSet
){
    override fun getDefaultColorName(): String = "Green"
}

/**
 * A `NamedScope` that includes all files modified in the active LST-CRC comparison.
 */
class ModifiedFilesScope : NamedScope(
    "LSTCRC.Modified", // scopeId
    { LstCrcBundle.message("scope.modified.name") }, // presentableNameSupplier
    AllIcons.Actions.EditSource,
    modifiedFilesPackageSet
){
    override fun getDefaultColorName(): String = "Blue"
}

/**
 * A `NamedScope` that includes all files moved or renamed in the active LST-CRC comparison.
 */
class MovedFilesScope : NamedScope(
    "LSTCRC.Moved", // scopeId
    { LstCrcBundle.message("scope.moved.name") }, // presentableNameSupplier
    AllIcons.Nodes.Tag,
    movedFilesPackageSet
){
    override fun getDefaultColorName(): String = "Gray"
}

/**
 * A `NamedScope` that includes all files deleted in the active LST-CRC comparison.
 */
class DeletedFilesScope : NamedScope(
    "LSTCRC.Deleted", // scopeId
    { LstCrcBundle.message("scope.deleted.name") }, // presentableNameSupplier
    AllIcons.Actions.Cancel,
    deletedFilesPackageSet
){
    override fun getDefaultColorName(): String = "Rose"
}


/**
 * A `NamedScope` that includes all files created, modified, or moved in the active LST-CRC comparison.
 */
class ChangedFilesScope : NamedScope(
    "LSTCRC.Changed", // scopeId
    { LstCrcBundle.message("scope.changed.name") }, // presentableNameSupplier
    AllIcons.Actions.ListChanges, // Use a standard IDE icon for consistency.
    changedFilesPackageSet
)