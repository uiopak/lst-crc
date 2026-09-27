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

    private fun content(closeable: Boolean, branchName: String? = null): Content =
        ContentFactory.getInstance().createContent(JPanel(), branchName ?: "tab", false).apply {
            isCloseable = closeable
            branchName?.let { putUserData(LstCrcKeys.BRANCH_NAME_KEY, it) }
        }
}
