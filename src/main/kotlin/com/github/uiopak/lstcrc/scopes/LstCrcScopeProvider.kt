package com.github.uiopak.lstcrc.scopes

import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScope

/**
 * Provides custom file scopes related to LSTCRC (Local Changes to Remote Commit) plugin.
 * These scopes ("Created Files", "Modified Files", "Moved Files", "Changed Files")
 * allow users to filter project views and searches based on the change status of files
 * as determined by the plugin's active branch comparison.
 */
class LstCrcScopeProvider : CustomScopesProvider {
    override fun getCustomScopes(): List<NamedScope> {
        return listOf(
            CreatedFilesScope(),
            ModifiedFilesScope(),
            MovedFilesScope(),
            ChangedFilesScope()
        )
    }
}
