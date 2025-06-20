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

/**
 * A generic [PackageSet] implementation for LST-CRC scopes. It determines file inclusion based on a provided lambda,
 * reducing boilerplate for different change types.
 */
private class LstCrcPackageSet(
    private val description: String,
    private val filesExtractor: (ProjectActiveDiffDataService) -> Collection<VirtualFile>
) : PackageSetBase() {

    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        if (project.isDisposed) return false
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        // If activeBranchName is null, it means diff data is intentionally cleared,
        // so no files should be included in any LSTCRC scope.
        if (diffDataService.activeBranchName == null) {
            return false
        }

        val relevantFiles = filesExtractor(diffDataService).toSet()
        return file in relevantFiles
    }

    override fun createCopy(): PackageSet = LstCrcPackageSet(description, filesExtractor)
    override fun getText(): String = description
    override fun getNodePriority(): Int = 1
}

// Instantiate the generic PackageSet for each change type.
private val createdFilesPackageSet = LstCrcPackageSet(
    LstCrcBundle.message("scope.created.description")
) { it.createdFiles }

private val modifiedFilesPackageSet = LstCrcPackageSet(
    LstCrcBundle.message("scope.modified.description")
) { it.modifiedFiles }

private val movedFilesPackageSet = LstCrcPackageSet(
    LstCrcBundle.message("scope.moved.description")
) { it.movedFiles }

private val changedFilesPackageSet = LstCrcPackageSet(
    LstCrcBundle.message("scope.changed.description")
) { it.createdFiles + it.modifiedFiles + it.movedFiles }


/**
 * A `NamedScope` that includes all files newly created in the active LST-CRC comparison.
 */
class CreatedFilesScope : NamedScope(
    LstCrcBundle.message("scope.created.name"),
    AllIcons.General.Add,
    createdFilesPackageSet
){
    override fun getDefaultColorName(): String = "Green"
}

/**
 * A `NamedScope` that includes all files modified in the active LST-CRC comparison.
 */
class ModifiedFilesScope : NamedScope(
    LstCrcBundle.message("scope.modified.name"),
    AllIcons.Actions.EditSource,
    modifiedFilesPackageSet
){
    override fun getDefaultColorName(): String = "Blue"
}

/**
 * A `NamedScope` that includes all files moved or renamed in the active LST-CRC comparison.
 */
class MovedFilesScope : NamedScope(
    LstCrcBundle.message("scope.moved.name"),
    AllIcons.Nodes.Tag,
    movedFilesPackageSet
){
    override fun getDefaultColorName(): String = "Gray"
}


/**
 * A `NamedScope` that includes all files created, modified, or moved in the active LST-CRC comparison.
 */
class ChangedFilesScope : NamedScope(
    LstCrcBundle.message("scope.changed.name"),
    LstCrcIcons.TOOL_WINDOW, // Use our custom plugin icon for brand consistency.
    changedFilesPackageSet
)