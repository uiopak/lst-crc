package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.services.BranchSnapshot
import com.github.uiopak.lstcrc.services.GitService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SearchTextField
import com.intellij.ui.treeStructure.Tree
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent
import javax.swing.tree.DefaultMutableTreeNode

class BranchSelectionPanelTest : BasePlatformTestCase() {

    fun testFilterSelectsFirstMatchingBranchFromStableSnapshot() {
        val panel = createPanel(
            localBranches = listOf("bugfix/main", "feature/alpha", "feature/beta"),
            remoteBranches = listOf("origin/feature/beta")
        )

        setSearchText(panel, "feature/b")

        assertEquals("feature/beta", selectedBranchName(panel))
        assertEquals(listOf("feature/beta", "origin/feature/beta"), visibleBranchNames(panel))
    }

    fun testEnterSubmitsSelectedBranch() {
        var selectedBranch: String? = null
        val panel = createPanel(
            localBranches = listOf("feature/alpha", "feature/beta"),
            remoteBranches = listOf("origin/release/1.0")
        ) { selectedBranch = it }

        selectBranch(panel, "origin/release/1.0")
        dispatchEnter(panel)

        assertEquals("origin/release/1.0", selectedBranch)
    }

    fun testNewPanelReopensWithFullBranchSnapshotAfterPreviousFilter() {
        val firstPanel = createPanel(
            localBranches = listOf("feature/alpha", "feature/beta"),
            remoteBranches = listOf("origin/feature/beta")
        )

        setSearchText(firstPanel, "feature/beta")
        assertEquals(listOf("feature/beta", "origin/feature/beta"), visibleBranchNames(firstPanel))

        ApplicationManager.getApplication().invokeAndWait {
            Disposer.dispose(firstPanel)
        }
        flushEdt()

        val reopenedPanel = createPanel(
            localBranches = listOf("feature/alpha", "feature/beta"),
            remoteBranches = listOf("origin/feature/beta")
        )

        assertNull(selectedBranchName(reopenedPanel))
        assertEquals(listOf("feature/alpha", "feature/beta", "origin/feature/beta"), visibleBranchNames(reopenedPanel))
    }

    private fun createPanel(
        localBranches: List<String>,
        remoteBranches: List<String>,
        onBranchSelected: (String) -> Unit = {}
    ): BranchSelectionPanel {
        lateinit var panel: BranchSelectionPanel
        ApplicationManager.getApplication().invokeAndWait {
            panel = BranchSelectionPanel(
                gitService = project.service<GitService>(),
                repository = null,
                branchSnapshot = BranchSnapshot(localBranches, remoteBranches),
                onBranchSelected = onBranchSelected
            )
            Disposer.register(testRootDisposable, panel)
        }
        flushEdt()
        return panel
    }

    private fun setSearchText(panel: BranchSelectionPanel, text: String) {
        ApplicationManager.getApplication().invokeAndWait {
            findDescendant<SearchTextField>(panel).text = text
        }
        flushEdt()
    }

    private fun selectBranch(panel: BranchSelectionPanel, branchName: String) {
        ApplicationManager.getApplication().invokeAndWait {
            branchTree(panel).selectionPath = branchPath(branchTree(panel).model.root as DefaultMutableTreeNode, branchName)
        }
        flushEdt()
    }

    private fun dispatchEnter(panel: BranchSelectionPanel) {
        ApplicationManager.getApplication().invokeAndWait {
            val tree = branchTree(panel)
            val event = KeyEvent(tree, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n')
            tree.keyListeners.forEach { it.keyPressed(event) }
        }
        flushEdt()
    }

    private fun selectedBranchName(panel: BranchSelectionPanel): String? {
        return branchNameFor(branchTree(panel).selectionPath?.lastPathComponent as? DefaultMutableTreeNode)
    }

    private fun visibleBranchNames(panel: BranchSelectionPanel): List<String> {
        val root = branchTree(panel).model.root as DefaultMutableTreeNode
        return root.depthFirstEnumeration().asSequence()
            .mapNotNull { it as? DefaultMutableTreeNode }
            .mapNotNull(::branchNameFor)
            .toList()
    }

    private fun branchPath(node: DefaultMutableTreeNode, branchName: String): javax.swing.tree.TreePath? {
        if (branchNameFor(node) == branchName) {
            return javax.swing.tree.TreePath(node.path)
        }

        for (child in node.children()) {
            val path = branchPath(child as DefaultMutableTreeNode, branchName)
            if (path != null) {
                return path
            }
        }

        return null
    }

    private fun branchNameFor(node: DefaultMutableTreeNode?): String? {
        val userObject = node?.userObject ?: return null
        return runCatching {
            userObject.javaClass.getMethod("getFullBranchName").invoke(userObject) as? String
        }.getOrNull()
    }

    private fun branchTree(panel: BranchSelectionPanel): Tree = findDescendant(panel, Tree::class.java)

    private inline fun <reified T : Component> findDescendant(root: Container): T =
        findDescendant(root, T::class.java)

    private fun <T : Component> findDescendant(root: Container, componentClass: Class<T>): T {
        root.components.forEach { component ->
            if (componentClass.isInstance(component)) {
                return componentClass.cast(component)
            }
            if (component is Container) {
                runCatching { return findDescendant(component, componentClass) }
            }
        }
        error("Could not find ${componentClass.simpleName} under ${root.javaClass.simpleName}")
    }

    private fun flushEdt() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        ApplicationManager.getApplication().invokeAndWait(object : Runnable {
            override fun run() = Unit
        })
    }
}