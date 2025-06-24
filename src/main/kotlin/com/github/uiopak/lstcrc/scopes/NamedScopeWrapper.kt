@file:Suppress("EqualsOrHashCode")

package com.github.uiopak.lstcrc.scopes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import javax.swing.Icon
import org.jetbrains.annotations.Nls
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiFile

/**
 * A SearchScope that wraps a NamedScope, delegating its core logic.
 *
 * We suppress "EqualsOrHashCode" because the base class `SearchScope` has a final `hashCode()`
 * method and requires subclasses to override `calcHashCode()` instead. Our implementation correctly
 * overrides both `equals` and `calcHashCode` using the same fields, thus satisfying the contract.
 */
class NamedScopeWrapper(
    /** Project context is needed for `PackageSet.contains` and for `PsiManager`. */
    private val project: Project,
    private val namedScope: NamedScope,
    /** Can be a `NamedScopeManager` or other holder; can be null. */
    private val namedScopesHolder: NamedScopesHolder?
) : GlobalSearchScope(project) {

    override fun getDisplayName(): @Nls String = namedScope.presentableName

    override fun getIcon(): Icon? = namedScope.icon

    override fun contains(file: VirtualFile): Boolean {
        // Guard against disposed project.
        if (project.isDisposed) return false

        val packageSet = namedScope.value ?: return false
        val psiFile: PsiFile? = PsiManager.getInstance(project).findFile(file)

        return if (psiFile != null) {
            // If namedScopesHolder is null, try to get the default one for the project.
            // Some PackageSet implementations might not strictly need it or can work with null.
            val holderToUse = namedScopesHolder ?: NamedScopeManager.getInstance(project)
            packageSet.contains(psiFile, holderToUse)
        } else {
            // If we can't get a PsiFile (e.g., for a binary file or one not in project structure),
            // it's unlikely to be contained in a typical PackageSet.
            false
        }
    }

    override fun isSearchInModuleContent(module: com.intellij.openapi.module.Module): Boolean = true
    override fun isSearchInLibraries(): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NamedScopeWrapper) return false
        return project == other.project && namedScope.scopeId == other.namedScope.scopeId
    }

    override fun calcHashCode(): Int {
        var result = project.hashCode()
        result = 31 * result + namedScope.scopeId.hashCode()
        return result
    }

    override fun toString(): String {
        return "NamedScopeWrapper(name=${namedScope.presentableName}, project=${project.name})"
    }
}