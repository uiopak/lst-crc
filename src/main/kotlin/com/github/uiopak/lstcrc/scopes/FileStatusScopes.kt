package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.services.ProjectActiveDiffDataService
import com.github.uiopak.lstcrc.toolWindow.ToolWindowSettingsProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import javax.swing.Icon

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
        val branchName = diffDataService.activeBranchName ?: return false

        // When comparing against the current branch (HEAD) and the user has disabled
        // "Include HEAD tab changes in file scopes", scopes should not match.
        if (branchName == "HEAD" && !ToolWindowSettingsProvider.isIncludeHeadInScopes()) {
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

private data class ScopeDescriptor(
    val scopeId: String,
    val nameKey: String,
    val descriptionKey: String,
    val icon: Icon,
    val colorName: String,
    val filesExtractor: (ProjectActiveDiffDataService) -> Set<VirtualFile>,
    val pathsExtractor: ((ProjectActiveDiffDataService) -> Set<String>)? = null,
    val includeInSearchScopes: Boolean = true
)

private fun packageSet(descriptor: ScopeDescriptor): PackageSet = LstCrcPackageSet(
    descriptor.descriptionKey,
    filesExtractor = descriptor.filesExtractor,
    pathsExtractor = descriptor.pathsExtractor
)

open class ColoredLstCrcScope(
    scopeId: String,
    nameKey: String,
    icon: Icon,
    private val colorName: String,
    descriptionKey: String,
    filesExtractor: (ProjectActiveDiffDataService) -> Set<VirtualFile>,
    pathsExtractor: ((ProjectActiveDiffDataService) -> Set<String>)? = null
) : NamedScope(
    scopeId,
    { LstCrcBundle.message(nameKey) },
    icon,
    LstCrcPackageSet(descriptionKey, filesExtractor, pathsExtractor)
) {
    override fun getDefaultColorName(): String = colorName
}

private object ScopeDescriptors {
    val CREATED = ScopeDescriptor(
        scopeId = "LSTCRC.Created",
        nameKey = "scope.created.name",
        descriptionKey = "scope.created.description",
        icon = AllIcons.General.Add,
        colorName = "Green",
        filesExtractor = { it.createdFilesSet },
        pathsExtractor = { it.createdFilePaths }
    )

    val MODIFIED = ScopeDescriptor(
        scopeId = "LSTCRC.Modified",
        nameKey = "scope.modified.name",
        descriptionKey = "scope.modified.description",
        icon = AllIcons.Actions.EditSource,
        colorName = "Blue",
        filesExtractor = { it.modifiedFilesSet },
        pathsExtractor = { it.modifiedFilePaths }
    )

    val MOVED = ScopeDescriptor(
        scopeId = "LSTCRC.Moved",
        nameKey = "scope.moved.name",
        descriptionKey = "scope.moved.description",
        icon = AllIcons.Nodes.Tag,
        colorName = "Gray",
        filesExtractor = { it.movedFilesSet },
        pathsExtractor = { it.movedFilePaths }
    )

    val DELETED = ScopeDescriptor(
        scopeId = "LSTCRC.Deleted",
        nameKey = "scope.deleted.name",
        descriptionKey = "scope.deleted.description",
        icon = AllIcons.Actions.Cancel,
        colorName = "Rose",
        filesExtractor = { emptySet() },
        pathsExtractor = { it.deletedFilePaths },
        includeInSearchScopes = false
    )

    val CHANGED = ScopeDescriptor(
        scopeId = "LSTCRC.Changed",
        nameKey = "scope.changed.name",
        descriptionKey = "scope.changed.description",
        icon = AllIcons.Actions.ListChanges,
        colorName = "Orange",
        filesExtractor = { it.changedFilesSet },
        pathsExtractor = { it.changedFilePaths }
    )
}


/**
 * A `NamedScope` that includes all files newly created in the active LST-CRC comparison.
 */
class CreatedFilesScope : ColoredLstCrcScope(
    ScopeDescriptors.CREATED.scopeId,
    ScopeDescriptors.CREATED.nameKey,
    ScopeDescriptors.CREATED.icon,
    ScopeDescriptors.CREATED.colorName,
    ScopeDescriptors.CREATED.descriptionKey,
    ScopeDescriptors.CREATED.filesExtractor,
    ScopeDescriptors.CREATED.pathsExtractor
)

/**
 * A `NamedScope` that includes all files modified in the active LST-CRC comparison.
 */
class ModifiedFilesScope : ColoredLstCrcScope(
    ScopeDescriptors.MODIFIED.scopeId,
    ScopeDescriptors.MODIFIED.nameKey,
    ScopeDescriptors.MODIFIED.icon,
    ScopeDescriptors.MODIFIED.colorName,
    ScopeDescriptors.MODIFIED.descriptionKey,
    ScopeDescriptors.MODIFIED.filesExtractor,
    ScopeDescriptors.MODIFIED.pathsExtractor
)

/**
 * A `NamedScope` that includes all files moved or renamed in the active LST-CRC comparison.
 */
class MovedFilesScope : ColoredLstCrcScope(
    ScopeDescriptors.MOVED.scopeId,
    ScopeDescriptors.MOVED.nameKey,
    ScopeDescriptors.MOVED.icon,
    ScopeDescriptors.MOVED.colorName,
    ScopeDescriptors.MOVED.descriptionKey,
    ScopeDescriptors.MOVED.filesExtractor,
    ScopeDescriptors.MOVED.pathsExtractor
)

/**
 * A `NamedScope` that includes all files deleted in the active LST-CRC comparison.
 */
class DeletedFilesScope : ColoredLstCrcScope(
    ScopeDescriptors.DELETED.scopeId,
    ScopeDescriptors.DELETED.nameKey,
    ScopeDescriptors.DELETED.icon,
    ScopeDescriptors.DELETED.colorName,
    ScopeDescriptors.DELETED.descriptionKey,
    ScopeDescriptors.DELETED.filesExtractor,
    ScopeDescriptors.DELETED.pathsExtractor
)


/**
 * A `NamedScope` that includes all files created, modified, or moved in the active LST-CRC comparison.
 */
class ChangedFilesScope : ColoredLstCrcScope(
    ScopeDescriptors.CHANGED.scopeId,
    ScopeDescriptors.CHANGED.nameKey,
    ScopeDescriptors.CHANGED.icon,
    ScopeDescriptors.CHANGED.colorName,
    ScopeDescriptors.CHANGED.descriptionKey,
    ScopeDescriptors.CHANGED.filesExtractor,
    ScopeDescriptors.CHANGED.pathsExtractor
)

internal object LstCrcScopeCollections {
    val customScopes: List<NamedScope>
        get() = listOf(
            LstCrcProvidedScopes.CREATED_FILES_SCOPE,
            LstCrcProvidedScopes.MODIFIED_FILES_SCOPE,
            LstCrcProvidedScopes.MOVED_FILES_SCOPE,
            LstCrcProvidedScopes.DELETED_FILES_SCOPE,
            LstCrcProvidedScopes.CHANGED_FILES_SCOPE
        )

    val searchableScopes: List<NamedScope>
        get() = customScopes.filter { scope ->
            when (scope.scopeId) {
                ScopeDescriptors.DELETED.scopeId -> false
                else -> true
            }
        }
}