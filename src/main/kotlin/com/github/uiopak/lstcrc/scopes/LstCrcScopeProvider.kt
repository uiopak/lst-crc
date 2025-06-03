package com.github.uiopak.lstcrc.scopes

import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScope

class LstCrcScopeProvider : CustomScopesProvider { // Changed interface
    override fun getCustomScopes(): List<NamedScope> { // Changed method name and return type
        return listOf( // Changed to listOf for List<NamedScope>
            CreatedFilesScope(),
            ModifiedFilesScope(),
            MovedFilesScope(),
            ChangedFilesScope()
        )
    }
}
