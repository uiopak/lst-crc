package com.github.uiopak.lstcrc.scopes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import javax.swing.Icon
import org.jetbrains.annotations.Nls
import com.intellij.psi.PsiManager // Required to get PsiFile from VirtualFile
import com.intellij.psi.PsiFile

/**
 * A SearchScope that wraps a NamedScope, delegating its core logic.
 */
class NamedScopeWrapper(
    private val project: Project, // Project context is needed for PackageSet.contains AND for PsiManager
    private val namedScope: NamedScope,
    private val namedScopesHolder: NamedScopesHolder? // Can be NamedScopeManager
) : GlobalSearchScope(project) {

    override fun getDisplayName(): @Nls String = namedScope.presentableName

    override fun getIcon(): Icon? = namedScope.icon

    override fun contains(file: VirtualFile): Boolean {
        if (project.isDisposed) return false // Guard against disposed project

        val packageSet = namedScope.value ?: return false

        // We need to convert VirtualFile to PsiFile
        val psiFile: PsiFile? = PsiManager.getInstance(project).findFile(file)

        return if (psiFile != null) {
            // If namedScopesHolder is null, try to get the default one for the project.
            // Some PackageSet implementations might not strictly need it or can work with null.
            val holderToUse = namedScopesHolder ?: NamedScopeManager.getInstance(project)
            packageSet.contains(psiFile, holderToUse)
        } else {
            // If we can't get a PsiFile (e.g., file is binary, not part of project structure recognized by PSI),
            // then it's unlikely to be contained in a typical PackageSet.
            false
        }
    }

    // --- Implement abstract methods from GlobalSearchScope ---
    override fun isSearchInModuleContent(module: com.intellij.openapi.module.Module): Boolean {
        return true
    }

    override fun isSearchInLibraries(): Boolean {
        return false
    }

    // --- Equality and HashCode are important for SearchScope instances ---
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NamedScopeWrapper) return false
        return project == other.project && namedScope.scopeId == other.namedScope.scopeId
    }

    override fun hashCode(): Int {
        var result = project.hashCode()
        result = 31 * result + namedScope.scopeId.hashCode()
        return result
    }

    override fun toString(): String {
        return "NamedScopeWrapper(name=${namedScope.presentableName}, project=${project.name})"
    }
}