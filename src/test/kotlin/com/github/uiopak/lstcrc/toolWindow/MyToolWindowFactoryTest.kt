package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.testsupport.LstCrcTestCase
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel

class MyToolWindowFactoryTest : LstCrcTestCase() {

    fun testOnlyTheHeadTabSelectsHead() {
        val findTabIndex = { branchName: String -> if (branchName == "feature") 0 else -1 }

        assertEquals(-1, selectedTabIndex(content(closeable = false), findTabIndex))
        assertEquals(0, selectedTabIndex(content(closeable = true, branchName = "feature"), findTabIndex))
        // A comparison tab its creator has not registered yet keeps the current comparison.
        assertNull(selectedTabIndex(content(closeable = true, branchName = "new-branch"), findTabIndex))
        // The "Select Branch" tab has no branch: selecting it must not switch the comparison to HEAD.
        assertNull(selectedTabIndex(content(closeable = true), findTabIndex))
    }

    // The state lists only comparison tabs, in tool window order: HEAD and "Select Branch" tabs are not counted.
    fun testStateIndexOfATabCountsTheComparisonTabsBeforeIt() {
        val head = content(closeable = false)
        val featureA = content(closeable = true, branchName = "feature-a")
        val selectBranch = content(closeable = true)
        val newTab = content(closeable = true, branchName = "feature-new")
        val featureC = content(closeable = true, branchName = "feature-c")

        assertEquals(1, comparisonTabsBefore(listOf(head, featureA, selectBranch, newTab, featureC), newTab))
        assertEquals(0, comparisonTabsBefore(listOf(head, newTab, featureA), newTab))
        assertEquals(2, comparisonTabsBefore(listOf(head, featureA, featureC, newTab), newTab))
    }

    private fun content(closeable: Boolean, branchName: String? = null): Content =
        ContentFactory.getInstance().createContent(JPanel(), branchName ?: "tab", false).apply {
            isCloseable = closeable
            branchName?.let { putUserData(LstCrcKeys.BRANCH_NAME_KEY, it) }
        }
}
