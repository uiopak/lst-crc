package com.github.uiopak.lstcrc.scopes

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
    val CHANGED_FILES_SCOPE by lazy { ChangedFilesScope() }
}

/**
 * Provides custom file scopes related to LSTCRC (Local Changes to Target Commit) plugin.
 * These scopes ("LSTCRC: Created Files", "LSTCRC: Modified Files", "LSTCRC: Moved Files", "LSTCRC: Changed Files")
 * allow users to filter project views and searches based on the change status of files
 * as determined by the plugin's active branch comparison.
 */
class LstCrcScopeProvider : CustomScopesProvider {

    override fun getCustomScopes(): List<NamedScope> {
        return listOf(
            LstCrcProvidedScopes.CREATED_FILES_SCOPE,
            LstCrcProvidedScopes.MODIFIED_FILES_SCOPE,
            LstCrcProvidedScopes.MOVED_FILES_SCOPE,
            LstCrcProvidedScopes.CHANGED_FILES_SCOPE
        )
    }
}