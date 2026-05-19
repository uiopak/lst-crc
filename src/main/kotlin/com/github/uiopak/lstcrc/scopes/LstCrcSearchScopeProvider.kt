package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider

/**
 * Provides the LSTCRC scopes under a "LSTCRC Changes" group in the scope selection dropdown.
 * This replaces the need for a ScopeDescriptorProvider.
 */
class LstCrcSearchScopeProvider : SearchScopeProvider {

    override fun getDisplayName(): String {
        // This string is used as a separator/group header in the scope selection dropdown.
        return LstCrcBundle.message("scope.provider.display.name")
    }

    override fun getSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> =
        LstCrcProvidedScopes.searchScopes(project)
}