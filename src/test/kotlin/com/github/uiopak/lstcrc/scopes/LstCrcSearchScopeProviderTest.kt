package com.github.uiopak.lstcrc.scopes

import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.PackageSet
import com.intellij.psi.search.scope.packageSet.PackageSetBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LstCrcSearchScopeProviderTest : BasePlatformTestCase() {

    fun testGetDisplayNameAndSearchScopesReturnExpectedLstCrcWrappers() {
        val provider = LstCrcSearchScopeProvider()

        assertEquals(LstCrcBundle.message("scope.provider.display.name"), provider.displayName)

        val scopes = provider.getSearchScopes(project, DataContext { null })

        assertSize(4, scopes)
        assertTrue(scopes.all { it is NamedScopeWrapper })

        val displayNames = scopes.map(SearchScope::getDisplayName)
        assertEquals(
            listOf(
                LstCrcProvidedScopes.CREATED_FILES_SCOPE.presentableName,
                LstCrcProvidedScopes.MODIFIED_FILES_SCOPE.presentableName,
                LstCrcProvidedScopes.MOVED_FILES_SCOPE.presentableName,
                LstCrcProvidedScopes.CHANGED_FILES_SCOPE.presentableName
            ),
            displayNames
        )
        assertFalse(displayNames.contains(LstCrcProvidedScopes.DELETED_FILES_SCOPE.presentableName))
    }

    fun testNamedScopeWrapperDelegatesPackageSetBaseContainsAndSearchFlags() {
        val matchingFile = myFixture.addFileToProject("scopes/Match.txt", "alpha\n").virtualFile
        val otherFile = myFixture.addFileToProject("scopes/Other.txt", "beta\n").virtualFile
        val holder = NamedScopeManager.getInstance(project)
        val packageSet = CapturingPackageSetBase("Match.txt")
        val namedScope = NamedScope(
            "Test.Scope",
            { "Synthetic Scope" },
            AllIcons.General.Add,
            packageSet
        )

        val wrapper = NamedScopeWrapper(project, namedScope, holder)

        assertTrue(wrapper.contains(matchingFile))
        assertFalse(wrapper.contains(otherFile))
        assertSame(holder, packageSet.lastHolder)
        assertSame(project, packageSet.lastProject)
        assertEquals("Synthetic Scope", wrapper.displayName)
        assertSame(AllIcons.General.Add, wrapper.icon)
        assertTrue(wrapper.isSearchInModuleContent(module))
        assertFalse(wrapper.isSearchInLibraries())
    }

    private class CapturingPackageSetBase(
        private val expectedSuffix: String
    ) : PackageSetBase() {
        var lastProject: com.intellij.openapi.project.Project? = null
        var lastHolder: NamedScopesHolder? = null

        override fun contains(file: VirtualFile, project: com.intellij.openapi.project.Project, holder: NamedScopesHolder?): Boolean {
            lastProject = project
            lastHolder = holder
            return file.path.endsWith(expectedSuffix)
        }

        override fun createCopy(): PackageSet = CapturingPackageSetBase(expectedSuffix)

        override fun getText(): String = "capturing:$expectedSuffix"

        override fun getNodePriority(): Int = 1
    }
}