package com.github.uiopak.lstcrc.scopes

import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScope

/**
 * Provides custom file scopes related to LSTCRC (Local Changes to Target Commit) plugin.
 * These scopes ("LSTCRC: Created Files", "LSTCRC: Modified Files", "LSTCRC: Moved Files", "LSTCRC: Changed Files")
 * allow users to filter project views and searches based on the change status of files
 * as determined by the plugin's active branch comparison.
 */
class LstCrcScopeProvider : CustomScopesProvider {

    companion object {
        // Singleton instances of the scopes, initialized lazily
        private val CREATED_FILES_SCOPE by lazy { CreatedFilesScope() }
        private val MODIFIED_FILES_SCOPE by lazy { ModifiedFilesScope() }
        private val MOVED_FILES_SCOPE by lazy { MovedFilesScope() }
        private val CHANGED_FILES_SCOPE by lazy { ChangedFilesScope() }
    }

    override fun getCustomScopes(): List<NamedScope> {
        return listOf(
            CREATED_FILES_SCOPE,
            MODIFIED_FILES_SCOPE,
            MOVED_FILES_SCOPE,
            CHANGED_FILES_SCOPE
        )
    }
}