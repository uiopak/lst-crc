package com.github.uiopak.lstcrc.scopes

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeDescriptorProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
// No need for NamedScopeFilter if using NamedScopeWrapper
import com.intellij.psi.search.scope.packageSet.NamedScopeManager

class LstCrcScopeDescriptorProvider : ScopeDescriptorProvider {

    override fun getScopeDescriptors(project: Project, dataContext: DataContext): Array<ScopeDescriptor> {
        val customScopesProvider = LstCrcScopeProvider()
        val namedScopes = customScopesProvider.customScopes
        val namedScopeManager = NamedScopeManager.getInstance(project) // For the NamedScopeWrapper context

        return namedScopes
            .filter { it.value != null } // Ensure there's a PackageSet
            .map { namedScope ->
                val searchScope = NamedScopeWrapper(project, namedScope, namedScopeManager)
                ScopeDescriptor(searchScope)
            }.toTypedArray()
    }
}