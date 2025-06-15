package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.psi.search.scope.packageSet.NamedScopeManager

/**
 * Provides the LSTCRC scopes under a "LSTCRC Changes" group in the scope selection dropdown.
 * This replaces the need for a ScopeDescriptorProvider.
 */
class LstCrcSearchScopeProvider : SearchScopeProvider {

    override fun getDisplayName(): String {
        // This string will be used as a separator/group header in the scope selection dropdown.
        return LstCrcBundle.message("scope.provider.display.name")
    }

    override fun getSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> {
        val namedScopeManager = NamedScopeManager.getInstance(project)

        // We get the singleton instances of our scopes from LstCrcProvidedScopes.
        val createdScope = LstCrcProvidedScopes.CREATED_FILES_SCOPE
        val modifiedScope = LstCrcProvidedScopes.MODIFIED_FILES_SCOPE
        val movedScope = LstCrcProvidedScopes.MOVED_FILES_SCOPE
        val changedScope = LstCrcProvidedScopes.CHANGED_FILES_SCOPE

        // Each NamedScope is wrapped in our custom SearchScope implementation.
        return listOf(
            NamedScopeWrapper(project, createdScope, namedScopeManager),
            NamedScopeWrapper(project, modifiedScope, namedScopeManager),
            NamedScopeWrapper(project, movedScope, namedScopeManager),
            NamedScopeWrapper(project, changedScope, namedScopeManager)
        )
    }
}