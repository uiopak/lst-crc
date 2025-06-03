package com.github.uiopak.lstcrc.scopes

import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopeProvider

class LstCrcScopeProvider : NamedScopeProvider {
    override fun getScopes(): Array<NamedScope> {
        return arrayOf(
            CreatedFilesScope(),
            ModifiedFilesScope(),
            MovedFilesScope(),
            ChangedFilesScope()
        )
    }
}
