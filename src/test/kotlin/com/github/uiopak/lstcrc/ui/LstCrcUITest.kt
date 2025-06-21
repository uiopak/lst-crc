package com.github.uiopak.lstcrc.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration

/**
 * UI tests for the lst-crc plugin.
 * These tests run the IDE and test the plugin in a real environment.
 *
 * To run these tests, use the 'runIdeForUiTests' Gradle task first to start an IDE instance with the Robot Server:
 * ./gradlew runIdeForUiTests
 *
 * Then run this test class.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LstCrcUITest {
    // Get timeout from system property or use default (120 seconds)
    private val timeout = System.getProperty("ui.test.timeout")?.toLongOrNull() ?: 120L

    // Get robot server URL from system property or use default
    private val robotServerUrl = System.getProperty("robot.server.url") ?: "http://127.0.0.1:8082"

    private lateinit var remoteRobot: RemoteRobot
    private lateinit var ideFrame: CommonContainerFixture

    @BeforeAll
    fun setUpAll() {
        remoteRobot = RemoteRobot(robotServerUrl)

        // Wait for the Robot Server to be responsive by making a simple call.
        // This is more reliable than a simple HTTP check.
        waitFor(Duration.ofMinutes(3), interval = Duration.ofSeconds(5)) {
            try {
                remoteRobot.callJs<Boolean>("true")
            } catch (e: Exception) {
                println("[DEBUG_LOG] Waiting for Robot Server to be available... ${e.message}")
                false
            }
        }
        println("[DEBUG_LOG] Robot Server is available, proceeding with tests.")

        // Wait for the IDE to fully initialize and find the main frame
        ideFrame = remoteRobot.find(byXpath("//div[@class='IdeFrameImpl']"), Duration.ofSeconds(timeout))
        println("[DEBUG_LOG] IDE Frame found.")
    }

    private fun openToolWindow() {
        // Open the tool window using the 'ActivateGitChangesViewToolWindow' action.
        // This is more reliable than clicking UI elements.
        val result = ideFrame.runJs("""
            const actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();
            const gitChangesViewAction = actionManager.getAction("ActivateGitChangesViewToolWindow");

            if (gitChangesViewAction) {
                const dataContext = com.intellij.openapi.actionSystem.DataManager.getInstance().getDataContext();
                const event = new com.intellij.openapi.actionSystem.AnActionEvent(
                    null, dataContext, "test", gitChangesViewAction.getTemplatePresentation().clone(),
                    actionManager, 0
                );
                gitChangesViewAction.actionPerformed(event);
                return true;
            } else {
                return false;
            }
        """, true) as Boolean
        Assertions.assertTrue(result, "Action 'ActivateGitChangesViewToolWindow' could not be performed.")

        // Wait for the tool window to appear and be visible
        waitFor(Duration.ofSeconds(timeout / 2)) {
            try {
                val component = ideFrame.find<ComponentFixture>(byXpath("//div[@accessiblename='GitChangesView' and @class='ToolWindowImpl']"))
                component.isShowing
            } catch (e: Exception) {
                println("[DEBUG_LOG] Waiting for tool window to appear: ${e.message}")
                false
            }
        }
    }

    /**
     * Test that the GitChangesView tool window is available and can be opened.
     */
    @Test
    fun testToolWindowAvailable() {
        openToolWindow()
        // Verify that the tool window is visible
        val toolWindows = ideFrame.findAll<ComponentFixture>(byXpath("//div[@accessiblename='GitChangesView' and @class='ToolWindowImpl']"))
        Assertions.assertTrue(toolWindows.isNotEmpty(), "GitChangesView tool window should be visible")
    }

    /**
     * Test that the status bar widget is available.
     */
    @Test
    fun testStatusBarWidgetAvailable() {
        // Check if the status bar widget is present by querying the StatusBar manager.
        // The widget ID is "LstCrcStatusWidget", as defined in LstCrcStatusWidget.kt.
        val widgetExists = ideFrame.runJs("""
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) return false;

            const statusBar = com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project);
            if (statusBar) {
                // The ID here is the widget's ID, not the factory's ID.
                const widget = statusBar.getWidget("LstCrcStatusWidget");
                return widget != null;
            }
            return false;
        """, true) as Boolean

        Assertions.assertTrue(widgetExists, "LST-CRC status bar widget should be available")
    }

    /**
     * Test that the custom scopes are registered and available.
     */
    @Test
    fun testCustomScopesAvailable() {
        // Check if our custom scopes provider is registered and provides the correct scopes.
        val scopesExist = ideFrame.runJs("""
            const project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0];
            if (!project) return false;

            // Find our specific SearchScopeProvider from all registered providers.
            const providers = com.intellij.psi.search.SearchScopeProvider.EP_NAME.getExtensionList();
            const ourProvider = providers.find(p => p.getClass().getName() === 'com.github.uiopak.lstcrc.scopes.LstCrcSearchScopeProvider');

            if (!ourProvider) return false;

            // Call getSearchScopes to see if it returns the expected number of scopes.
            const dataContext = com.intellij.openapi.actionSystem.DataManager.getInstance().getDataContext();
            const scopes = ourProvider.getSearchScopes(project, dataContext);

            // We expect 4 scopes: Created, Modified, Moved, and Changed.
            if (scopes.size() !== 4) return false;

            // For extra certainty, check if one of the scopes has the expected name from the bundle.
            // Note: Accessing resource bundles can be tricky in this context, so we'll check for a key part of the name.
            let foundChangedScope = false;
            for (let i = 0; i < scopes.size(); i++) {
                const scope = scopes.get(i);
                if (scope.getDisplayName().includes("LSTCRC: Changed Files")) {
                    foundChangedScope = true;
                    break;
                }
            }
            return foundChangedScope;
        """, true) as Boolean

        Assertions.assertTrue(scopesExist, "LST-CRC custom scopes should be available and correctly configured")
    }

    /**
     * Test that the GitChangesView tool window has a 'HEAD' tab and it contains a ChangesTree.
     */
    @Test
    fun testToolWindowDisplaysChangesAndContent() {
        openToolWindow()

        // Find the tool window fixture.
        val toolWindow = ideFrame.find<CommonContainerFixture>(byXpath("//div[@accessiblename='GitChangesView' and @class='ToolWindowImpl']"))

        // Verify that the 'HEAD' tab is present.
        // We look for a component with the text 'HEAD' which acts as the tab label.
        waitFor(Duration.ofSeconds(10)) {
            toolWindow.findAll<ComponentFixture>(byXpath("//div[@text='HEAD']")).isNotEmpty()
        }
        val headTab = toolWindow.find<ComponentFixture>(byXpath("//div[@text='HEAD']"))
        Assertions.assertTrue(headTab.isShowing, "The 'HEAD' tab should be visible in the tool window.")

        // Now, verify that the content of the tool window (the LstCrcChangesBrowser) contains a ChangesTree.
        // This confirms that our custom UI component has been loaded correctly.
        val changesTree = toolWindow.find<ComponentFixture>(byXpath("//div[@class='ChangesTree']"))
        Assertions.assertTrue(changesTree.isShowing, "The ChangesTree component should be visible inside the tool window.")
    }
}