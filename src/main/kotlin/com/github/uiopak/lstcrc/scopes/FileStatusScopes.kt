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
 * Describes one LST-CRC scope: its id, UI texts, icon, default file colour and the set of paths
 * (from [ProjectActiveDiffDataService]) it contains.
 */
class ScopeDescriptor(
    val scopeId: String,
    val nameKey: String,
    val descriptionKey: String,
    val icon: Icon,
    val colorName: String,
    val paths: (ProjectActiveDiffDataService) -> Set<String>
)

/**
 * Matches files by path against the active comparison. Paths are used (not [VirtualFile] identity)
 * because deleted and revision-backed files are different instances in the tree and in editors.
 */
private class LstCrcPackageSet(private val descriptor: ScopeDescriptor) : PackageSetBase() {

    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        if (project.isDisposed) return false
        val diffDataService = project.service<ProjectActiveDiffDataService>()

        // A null branch means the diff data is intentionally cleared.
        val branchName = diffDataService.activeBranchName ?: return false

        // HEAD-tab changes only count when "Include HEAD tab changes in file scopes" is enabled.
        if (branchName == "HEAD" && !ToolWindowSettingsProvider.isIncludeHeadInScopes()) {
            return false
        }

        return file.path in descriptor.paths(diffDataService)
    }

    override fun createCopy(): PackageSet = LstCrcPackageSet(descriptor)

    // Resolved lazily so the bundle is only read when the UI needs the text.
    override fun getText(): String = LstCrcBundle.message(descriptor.descriptionKey)

    override fun getNodePriority(): Int = 1
}

open class ColoredLstCrcScope(private val descriptor: ScopeDescriptor) : NamedScope(
    descriptor.scopeId,
    { LstCrcBundle.message(descriptor.nameKey) },
    descriptor.icon,
    LstCrcPackageSet(descriptor)
) {
    override fun getDefaultColorName(): String = descriptor.colorName
}

private object ScopeDescriptors {
    val CREATED = ScopeDescriptor(
        "LSTCRC.Created", "scope.created.name", "scope.created.description",
        AllIcons.General.Add, "Green"
    ) { it.createdFilePaths }

    val MODIFIED = ScopeDescriptor(
        "LSTCRC.Modified", "scope.modified.name", "scope.modified.description",
        AllIcons.Actions.EditSource, "Blue"
    ) { it.modifiedFilePaths }

    val MOVED = ScopeDescriptor(
        "LSTCRC.Moved", "scope.moved.name", "scope.moved.description",
        AllIcons.Nodes.Tag, "Gray"
    ) { it.movedFilePaths }

    val DELETED = ScopeDescriptor(
        DELETED_SCOPE_ID, "scope.deleted.name", "scope.deleted.description",
        AllIcons.Actions.Cancel, "Rose"
    ) { it.deletedFilePaths }

    val CHANGED = ScopeDescriptor(
        "LSTCRC.Changed", "scope.changed.name", "scope.changed.description",
        AllIcons.Actions.ListChanges, "Orange"
    ) { it.changedFilePaths }
}

/** Id of the deleted-files scope; also used to look up its configured colour for deleted rows in the tree. */
const val DELETED_SCOPE_ID = "LSTCRC.Deleted"

// The concrete classes are kept (rather than instances of ColoredLstCrcScope) because UI tests load them by name.

/** Files newly created in the active LST-CRC comparison. */
class CreatedFilesScope : ColoredLstCrcScope(ScopeDescriptors.CREATED)

/** Files modified in the active LST-CRC comparison. */
class ModifiedFilesScope : ColoredLstCrcScope(ScopeDescriptors.MODIFIED)

/** Files moved or renamed in the active LST-CRC comparison. */
class MovedFilesScope : ColoredLstCrcScope(ScopeDescriptors.MOVED)

/** Files deleted in the active LST-CRC comparison. */
class DeletedFilesScope : ColoredLstCrcScope(ScopeDescriptors.DELETED)

/** Files created, modified or moved in the active LST-CRC comparison. */
class ChangedFilesScope : ColoredLstCrcScope(ScopeDescriptors.CHANGED)
