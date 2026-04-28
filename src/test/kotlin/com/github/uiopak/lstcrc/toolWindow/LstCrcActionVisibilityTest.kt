package com.github.uiopak.lstcrc.toolWindow

import com.github.uiopak.lstcrc.LstCrcConstants
import com.github.uiopak.lstcrc.resources.LstCrcBundle
import com.github.uiopak.lstcrc.state.TabInfo
import com.github.uiopak.lstcrc.state.ToolWindowState
import com.github.uiopak.lstcrc.services.ToolWindowStateService
import com.github.uiopak.lstcrc.utils.LstCrcKeys
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsLogCommitSelection
import sun.misc.Unsafe
import java.lang.reflect.Proxy
import javax.swing.JPanel

class LstCrcActionVisibilityTest : BasePlatformTestCase() {

    fun testShowRepoComparisonInfoActionHiddenOnHeadAndVisibleForComparisonTab() {
        val action = ShowRepoComparisonInfoAction()
        val stateService = project.service<ToolWindowStateService>()

        stateService.noStateLoaded()
        val headEvent = actionEvent(action)
        action.update(headEvent)
        assertFalse(headEvent.presentation.isEnabledAndVisible)

        stateService.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-a", alias = "Feature A")),
                selectedTabIndex = 0
            )
        )
        val tabEvent = actionEvent(action)
        action.update(tabEvent)
        assertTrue(tabEvent.presentation.isEnabledAndVisible)
    }

    fun testCreateTabFromRevisionActionVisibleOnlyForSingleRevisionSelection() {
        val action = CreateTabFromRevisionAction()

        val noSelectionEvent = actionEvent(action)
        action.update(noSelectionEvent)
        assertFalse(noSelectionEvent.presentation.isEnabledAndVisible)

        val singleSelectionEvent = actionEvent(action, listOf(TestRevisionNumber("abc1234")))
        action.update(singleSelectionEvent)
        assertTrue(singleSelectionEvent.presentation.isEnabledAndVisible)

        val multiSelectionEvent = actionEvent(
            action,
            listOf(TestRevisionNumber("abc1234"), TestRevisionNumber("def5678"))
        )
        action.update(multiSelectionEvent)
        assertFalse(multiSelectionEvent.presentation.isEnabledAndVisible)
    }

    fun testRenameTabActionVisibleForClosableBranchTabWhenContextIsNestedUnderBaseLabel() {
        val action = RenameTabAction()
        val content = createTabContent(closeable = true, branchName = "feature-a")
        val label = TestBaseLabel(content)
        val nestedComponent = JPanel()
        label.add(nestedComponent)

        val event = actionEvent(
            action,
            contextComponent = nestedComponent,
            toolWindowId = LstCrcConstants.TOOL_WINDOW_ID
        )

        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    fun testRenameTabActionHiddenWithoutRenamableTabContext() {
        val action = RenameTabAction()

        val wrongToolWindowEvent = actionEvent(
            action,
            contextComponent = JPanel(),
            toolWindowId = "OtherToolWindow"
        )
        action.update(wrongToolWindowEvent)
        assertFalse(wrongToolWindowEvent.presentation.isEnabledAndVisible)

        val nonCloseableEvent = actionEvent(
            action,
            contextComponent = TestBaseLabel(createTabContent(closeable = false, branchName = "feature-a")),
            toolWindowId = LstCrcConstants.TOOL_WINDOW_ID
        )
        action.update(nonCloseableEvent)
        assertFalse(nonCloseableEvent.presentation.isEnabledAndVisible)

        val missingBranchEvent = actionEvent(
            action,
            contextComponent = TestBaseLabel(createTabContent(closeable = true, branchName = null)),
            toolWindowId = LstCrcConstants.TOOL_WINDOW_ID
        )
        action.update(missingBranchEvent)
        assertFalse(missingBranchEvent.presentation.isEnabledAndVisible)

        val unrelatedComponentEvent = actionEvent(
            action,
            contextComponent = JPanel(),
            toolWindowId = LstCrcConstants.TOOL_WINDOW_ID
        )
        action.update(unrelatedComponentEvent)
        assertFalse(unrelatedComponentEvent.presentation.isEnabledAndVisible)
    }

    fun testOpenBranchSelectionTabActionHiddenWhenSelectionTabAlreadyExists() {
        val selectionTabName = LstCrcBundle.message("tab.name.select.branch")
        val action = OpenBranchSelectionTabAction(project, toolWindowWithContents(createNamedContent(selectionTabName)))

        val event = actionEvent(action)
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    fun testOpenBranchSelectionTabActionVisibleWhenSelectionTabIsAbsent() {
        val action = OpenBranchSelectionTabAction(project, toolWindowWithContents(createNamedContent("feature-a")))

        val event = actionEvent(action)
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    fun testSetRevisionAsRepoComparisonActionVisibleOnlyForSingleCommitSelectionWithActiveTab() {
        val action = SetRevisionAsRepoComparisonAction()
        val stateService = project.service<ToolWindowStateService>()

        stateService.noStateLoaded()
        val noTabEvent = actionEvent(action, commitSelectionSize = 1)
        action.update(noTabEvent)
        assertFalse(noTabEvent.presentation.isEnabledAndVisible)

        stateService.loadState(
            ToolWindowState(
                openTabs = listOf(TabInfo(branchName = "feature-a", alias = "Feature A")),
                selectedTabIndex = 0
            )
        )

        val noSelectionEvent = actionEvent(action)
        action.update(noSelectionEvent)
        assertFalse(noSelectionEvent.presentation.isEnabledAndVisible)

        val singleSelectionEvent = actionEvent(action, commitSelectionSize = 1)
        action.update(singleSelectionEvent)
        assertTrue(singleSelectionEvent.presentation.isEnabledAndVisible)

        val multiSelectionEvent = actionEvent(action, commitSelectionSize = 2)
        action.update(multiSelectionEvent)
        assertFalse(multiSelectionEvent.presentation.isEnabledAndVisible)
    }

    private fun actionEvent(
        action: com.intellij.openapi.actionSystem.AnAction,
        revisions: List<VcsRevisionNumber>? = null,
        contextComponent: java.awt.Component? = null,
        toolWindowId: String? = null,
        commitSelectionSize: Int? = null
    ): AnActionEvent {
        val builder = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)

        if (revisions != null) {
            builder.add(VcsDataKeys.VCS_REVISION_NUMBERS, revisions.toTypedArray())
        }

        if (contextComponent != null) {
            builder.add(PlatformDataKeys.CONTEXT_COMPONENT, contextComponent)
        }

        if (toolWindowId != null) {
            builder.add(PlatformDataKeys.TOOL_WINDOW, toolWindow(toolWindowId))
        }

        if (commitSelectionSize != null) {
            builder.add(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION, commitSelection(commitSelectionSize))
        }

        return AnActionEvent.createFromAnAction(action, null, "test", builder.build())
    }

    private fun commitSelection(size: Int): VcsLogCommitSelection {
        return Proxy.newProxyInstance(
            VcsLogCommitSelection::class.java.classLoader,
            arrayOf(VcsLogCommitSelection::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getCommits" -> List(size) { index -> "commit-$index" }
                else -> defaultValue(method.returnType)
            }
        } as VcsLogCommitSelection
    }

    private fun createTabContent(closeable: Boolean, branchName: String?): Content {
        val content = ContentFactory.getInstance().createContent(JPanel(), branchName ?: "HEAD", false)
        content.isCloseable = closeable
        if (branchName != null) {
            content.putUserData(LstCrcKeys.BRANCH_NAME_KEY, branchName)
        }
        return content
    }

    private fun createNamedContent(displayName: String): Content {
        return ContentFactory.getInstance().createContent(JPanel(), displayName, false)
    }

    private fun toolWindow(id: String): ToolWindow {
        return Proxy.newProxyInstance(
            ToolWindow::class.java.classLoader,
            arrayOf(ToolWindow::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getId" -> id
                "isDisposed" -> false
                else -> defaultValue(method.returnType)
            }
        } as ToolWindow
    }

    private fun toolWindowWithContents(vararg contents: Content): ToolWindow {
        val contentManager = Proxy.newProxyInstance(
            ContentManager::class.java.classLoader,
            arrayOf(ContentManager::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getContents" -> contents
                else -> defaultValue(method.returnType)
            }
        } as ContentManager

        return Proxy.newProxyInstance(
            ToolWindow::class.java.classLoader,
            arrayOf(ToolWindow::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getContentManager" -> contentManager
                else -> defaultValue(method.returnType)
            }
        } as ToolWindow
    }

    private fun defaultValue(returnType: Class<*>): Any? {
        return when {
            returnType == java.lang.Boolean.TYPE -> false
            returnType == java.lang.Integer.TYPE -> 0
            returnType == java.lang.Long.TYPE -> 0L
            returnType == java.lang.Float.TYPE -> 0f
            returnType == java.lang.Double.TYPE -> 0.0
            returnType == java.lang.Character.TYPE -> '\u0000'
            returnType == java.lang.Short.TYPE -> 0.toShort()
            returnType == java.lang.Byte.TYPE -> 0.toByte()
            else -> null
        }
    }

    private class TestBaseLabel(private val content: Content) : BaseLabel(allocateUi(), true) {
        override fun getContent(): Content = content

        companion object {
            private fun allocateUi(): ToolWindowContentUi {
                val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
                unsafeField.isAccessible = true
                val unsafe = unsafeField.get(null) as Unsafe
                return unsafe.allocateInstance(ToolWindowContentUi::class.java) as ToolWindowContentUi
            }
        }
    }

    private data class TestRevisionNumber(private val value: String) : VcsRevisionNumber {
        override fun asString(): String = value

        override fun compareTo(other: VcsRevisionNumber): Int = value.compareTo(other.asString())
    }
}