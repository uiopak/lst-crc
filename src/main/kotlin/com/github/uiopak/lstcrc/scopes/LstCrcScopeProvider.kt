package com.github.uiopak.lstcrc.scopes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScope

/**
 * A singleton object holding the instances of the LSTCRC scopes.
 * This avoids initializing them inside a companion object of an extension point,
 * which is discouraged by the platform linter.
 */
object LstCrcProvidedScopes {
    // These are lazy to ensure they are created only when first accessed.
    val CREATED_FILES_SCOPE by lazy { CreatedFilesScope() }
    val MODIFIED_FILES_SCOPE by lazy { ModifiedFilesScope() }
    val MOVED_FILES_SCOPE by lazy { MovedFilesScope() }
    val DELETED_FILES_SCOPE by lazy { DeletedFilesScope() }
    val CHANGED_FILES_SCOPE by lazy { ChangedFilesScope() }

    val allScopes: List<NamedScope> = listOf(
        CREATED_FILES_SCOPE,
        MODIFIED_FILES_SCOPE,
        MOVED_FILES_SCOPE,
        DELETED_FILES_SCOPE,
        CHANGED_FILES_SCOPE
    )

    val searchableScopes: List<NamedScope> = allScopes.filterNot { it === DELETED_FILES_SCOPE }

    fun searchScopes(project: Project): List<SearchScope> =
        searchableScopes.map { scope -> GlobalSearchScopesCore.filterScope(project, scope) }
}

/**
 * Provides custom file scopes related to LSTCRC (Local Changes to Target Commit) plugin.
 * These scopes ("LSTCRC: Created Files", "LSTCRC: Modified Files", "LSTCRC: Moved Files", "LSTCRC: Changed Files")
 * allow users to filter project views and searches based on the change status of files
 * as determined by the plugin's active branch comparison.
 */
class LstCrcScopeProvider : CustomScopesProvider {

    override fun getCustomScopes(): List<NamedScope> = LstCrcProvidedScopes.allScopes
}