package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.tabs.impl.TabLabel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.awt.Color
import javax.swing.JComponent
import javax.swing.border.Border
import javax.swing.border.MatteBorder
import com.intellij.openapi.Disposable
import com.intellij.ui.content.ContentManager
import com.intellij.util.ui.UIUtil // Required for findComponentsOfType

// Stub for UIUtil.findComponentsOfType if it's problematic to use directly in test
// or if a more controlled mock behavior is needed.
// For now, assuming it can be part of the test setup if available via test platform.

@ExtendWith(MockitoExtension::class)
class MyToolWindowFactoryTest {

    @Mock private lateinit var mockProject: Project
    @Mock private lateinit var mockToolWindow: ToolWindow
    @Mock private lateinit var mockContent: Content
    @Mock private lateinit var mockTabLabel: TabLabel // Mocking the specific TabLabel
    @Mock private lateinit var mockContentComponent: JComponent // Fallback component
    @Mock private lateinit var mockPropertiesComponent: PropertiesComponent
    @Mock private lateinit var mockDisposable: Disposable // For ToolWindow
    @Mock private lateinit var mockContentManager: ContentManager // For ToolWindow
    @Mock private lateinit var mockParentComponent: JComponent // For TabLabel's parent


    private lateinit var factory: MyToolWindowFactory
    private lateinit var settings: MyToolWindowFactory.TabColoringSettings

    // Reflection helper to access private applyTabStyling method
    private fun callApplyTabStyling(content: Content, settings: MyToolWindowFactory.TabColoringSettings) {
        val method = MyToolWindowFactory::class.java.getDeclaredMethod(
            "applyTabStyling",
            Content::class.java,
            MyToolWindowFactory.TabColoringSettings::class.java,
            Project::class.java,
            ToolWindow::class.java
        )
        method.isAccessible = true
        method.invoke(factory, content, settings, mockProject, mockToolWindow)
    }

    // Reflection helper to access private getTabColoringSettings method
    private fun callGetTabColoringSettings(propertiesComponent: PropertiesComponent): MyToolWindowFactory.TabColoringSettings {
        val method = MyToolWindowFactory::class.java.getDeclaredMethod(
            "getTabColoringSettings",
            PropertiesComponent::class.java
        )
        method.isAccessible = true
        return method.invoke(factory, propertiesComponent) as MyToolWindowFactory.TabColoringSettings
    }


    @BeforeEach
    fun setUp() {
        factory = MyToolWindowFactory()

        // Common mocking behavior
        `when`(mockToolWindow.disposable).thenReturn(mockDisposable)
        `when`(mockToolWindow.contentManager).thenReturn(mockContentManager)
        `when`(mockToolWindow.component).thenReturn(mock(JComponent::class.java)) // Main tool window component

        `when`(mockContent.component).thenReturn(mockContentComponent)
        `when`(mockContent.displayName).thenReturn("TestTab")

        // Setup for finding TabLabel:
        // This simulates UIUtil.findComponentsOfType finding our mockTabLabel.
        // This is a simplified mock; real UIUtil usage might be more complex.
        // For a more robust test, you might need to involve PowerMockito or deeper test framework utilities
        // if direct static mocking of UIUtil is hard.
        // For now, we assume `toolWindow.component` is the container where TabLabels are sought.
        // And `mockTabLabel.content` correctly points back to `mockContent`.
        `when`(mockTabLabel.content).thenReturn(mockContent)
        `when`(mockTabLabel.parent).thenReturn(mockParentComponent) // For revalidate/repaint calls
        `when`(mockContentComponent.parent).thenReturn(mockParentComponent)


        // Make TabLabel findable. This is tricky.
        // A simplified approach: assume applyTabStyling directly gets the TabLabel if possible.
        // The current applyTabStyling implementation uses UIUtil.findComponentsOfType.
        // Mocking static UIUtil.findComponentsOfType is hard without PowerMock.
        // As a workaround, for these unit tests, we might need to modify applyTabStyling
        // to accept a lambda for finding the TabLabel, or use a test-specific subclass of MyToolWindowFactory.
        // For now, let's assume the test focuses on the logic *after* TabLabel is found or not.
        // We'll control whether mockTabLabel is "found" by how we set up interactions with it.
        // The test will verify interactions on mockTabLabel IF it was found,
        // or on mockContentComponent IF TabLabel was not found.
        // The current implementation of applyTabStyling tries to find TabLabel via UIUtil.
        // To make it testable, we'd ideally inject the TabLabel or the search mechanism.
        // For now, we will assume the TabLabel is found by having `mockTabLabel.content` return `mockContent`.
        // The test will need to ensure `UIUtil.findComponentsOfType` (if actually called) would return `mockTabLabel`.
        // This part is the most challenging for a pure unit test.

        // Default settings for many tests
        settings = MyToolWindowFactory.TabColoringSettings(enabled = true, style = "BACKGROUND", color = "Red")
    }

    // Test color conversion implicitly via applyTabStyling for now.
    // A dedicated test would be good if color conversion was a public static util.
    // MyToolWindowFactory.getTabColoringSettings already tests PropertiesComponent interaction.

    @Test
    fun `applyTabStyling when disabled should reset styles on TabLabel`() {
        settings = MyToolWindowFactory.TabColoringSettings(enabled = false, style = "BACKGROUND", color = "Red")

        // Simulate TabLabel is found
        `when`(mockToolWindow.component).thenReturn(mockTabLabel) // Simplistic way to ensure it's "found"
                                                                // by making it the toolwindow component itself for the test
        // More accurately, need to mock the behavior of UIUtil.findComponentsOfType
        // This is a placeholder for proper static mocking or refactoring for testability.
        // For now, assume `applyTabStyling` will use `mockTabLabel` if `mockTabLabel.content == mockContent`.

        callApplyTabStyling(mockContent, settings)

        verify(mockTabLabel).background = null
        verify(mockTabLabel).isOpaque = false
        verify(mockTabLabel).border = null
        verify(mockTabLabel.parent)?.revalidate()
        verify(mockTabLabel.parent)?.repaint()
    }

    @Test
    fun `applyTabStyling when disabled should reset styles on contentComponent if TabLabel not found`() {
        settings = MyToolWindowFactory.TabColoringSettings(enabled = false, style = "BACKGROUND", color = "Red")

        // Simulate TabLabel is NOT found (e.g., mockTabLabel.content returns something else or UIUtil returns empty)
        `when`(mockTabLabel.content).thenReturn(null) // Ensure our mockTabLabel is not matched
        // Or ensure UIUtil.findComponentsOfType called within applyTabStyling returns an empty list.
        // This requires deeper mocking of UIUtil or refactoring as mentioned.
        // For this test, we'll rely on the fallback by ensuring no TabLabel is associated with mockContent.

        callApplyTabStyling(mockContent, settings)

        verify(mockContentComponent).background = UIManager.getColor("Panel.background")
        verify(mockContentComponent).border = UIManager.getBorder("Component.border")
        // verify(mockContentComponent).isOpaque = false // or true, depending on default expectation
        verify(mockContentComponent.parent)?.revalidate()
        verify(mockContentComponent.parent)?.repaint()
        verify(mockTabLabel, never()).setBackground(any()) // Ensure TabLabel was not styled
    }


    @Test
    fun `applyTabStyling with BACKGROUND style on TabLabel`() {
        settings = MyToolWindowFactory.TabColoringSettings(enabled = true, style = "BACKGROUND", color = "Red")
        // Simulate TabLabel is found
         `when`(mockToolWindow.component).thenReturn(mockTabLabel)


        callApplyTabStyling(mockContent, settings)

        verify(mockTabLabel).background = Color.RED
        verify(mockTabLabel).isOpaque = true
        verify(mockTabLabel.parent)?.revalidate()
        verify(mockTabLabel.parent)?.repaint()
    }

    @Test
    fun `applyTabStyling with BORDER_TOP style on TabLabel`() {
        settings = MyToolWindowFactory.TabColoringSettings(enabled = true, style = "BORDER_TOP", color = "Blue")
        // Simulate TabLabel is found
        `when`(mockToolWindow.component).thenReturn(mockTabLabel)

        callApplyTabStyling(mockContent, settings)

        verify(mockTabLabel).border = any(MatteBorder::class.java)
        // Optionally, capture the border and assert its properties if MatteBorder details are critical
        // val borderCaptor = ArgumentCaptor.forClass(Border::class.java)
        // verify(mockTabLabel).border = borderCaptor.capture()
        // assertTrue(borderCaptor.value is MatteBorder)
        // assertEquals(Color.BLUE, (borderCaptor.value as MatteBorder).matteColor)
        // assertEquals(2, (borderCaptor.value as MatteBorder).getBorderInsets(mockTabLabel).top)
        verify(mockTabLabel.parent)?.revalidate()
        verify(mockTabLabel.parent)?.repaint()
    }

    @Test
    fun `applyTabStyling with Default color should reset TabLabel`() {
        settings = MyToolWindowFactory.TabColoringSettings(enabled = true, style = "BACKGROUND", color = "Default")
        // Simulate TabLabel is found
        `when`(mockToolWindow.component).thenReturn(mockTabLabel)

        callApplyTabStyling(mockContent, settings)

        verify(mockTabLabel).background = null
        verify(mockTabLabel).isOpaque = false // Important for default look
         verify(mockTabLabel.parent)?.revalidate()
        verify(mockTabLabel.parent)?.repaint()
    }

    @Test
    fun `applyTabStyling with hex color`() {
        settings = MyToolWindowFactory.TabColoringSettings(enabled = true, style = "BACKGROUND", color = "#FF00FF")
        // Simulate TabLabel is found
        `when`(mockToolWindow.component).thenReturn(mockTabLabel)

        callApplyTabStyling(mockContent, settings)
        verify(mockTabLabel).background = Color.decode("#FF00FF")
        verify(mockTabLabel).isOpaque = true
    }

    @Test
    fun `applyTabStyling with invalid color string falls back to Default (null color)`() {
        settings = MyToolWindowFactory.TabColoringSettings(enabled = true, style = "BACKGROUND", color = "InvalidColorString")
        // Simulate TabLabel is found
        `when`(mockToolWindow.component).thenReturn(mockTabLabel)

        callApplyTabStyling(mockContent, settings)
        // Should reset to default because color is invalid
        verify(mockTabLabel).background = null
        verify(mockTabLabel).isOpaque = false
    }

    // --- Tests for getTabColoringSettings ---
    @Test
    fun `getTabColoringSettings reads from PropertiesComponent`() {
        `when`(mockPropertiesComponent.getBoolean("com.github.uiopak.lstcrc.app.tabColoringEnabled", true)).thenReturn(false)
        `when`(mockPropertiesComponent.getValue("com.github.uiopak.lstcrc.app.tabColoringStyle", "BACKGROUND")).thenReturn("BORDER_BOTTOM")
        `when`(mockPropertiesComponent.getValue("com.github.uiopak.lstcrc.app.tabColoringColor", "Default")).thenReturn("Green")

        val retrievedSettings = callGetTabColoringSettings(mockPropertiesComponent)

        assertEquals(false, retrievedSettings.enabled)
        assertEquals("BORDER_BOTTOM", retrievedSettings.style)
        assertEquals("Green", retrievedSettings.color)

        verify(mockPropertiesComponent).getBoolean("com.github.uiopak.lstcrc.app.tabColoringEnabled", true)
        verify(mockPropertiesComponent).getValue("com.github.uiopak.lstcrc.app.tabColoringStyle", "BACKGROUND")
        verify(mockPropertiesComponent).getValue("com.github.uiopak.lstcrc.app.tabColoringColor", "Default")
    }
}

// Challenges for these tests:
// 1. Mocking UIUtil.findComponentsOfType: This static method is hard to mock without PowerMockito or similar.
//    The current tests somewhat bypass this by either assuming the TabLabel is found (by making mockToolWindow.component return it)
//    or by ensuring mockTabLabel.content doesn't match, thus forcing a fallback.
//    A refactor of MyToolWindowFactory to make TabLabel retrieval injectable would improve testability.
//    For example, applyTabStyling could take a `(Content) -> TabLabel?` lambda as a parameter.
// 2. UIManager calls: `UIManager.getColor` and `UIManager.getBorder` are static calls.
//    These will return actual L&F values, which is usually fine, but for strict isolation,
//    they could also be wrapped or mocked. For now, this is acceptable.
//
// The provided tests focus on the conditional logic within applyTabStyling and assume the component
// to be styled (TabLabel or content.component) is correctly identified and passed to the styling logic.
// The line `when(mockToolWindow.component).thenReturn(mockTabLabel)` is a simplification
// to make `UIUtil.findComponentsOfType(mockToolWindow.component, ...)` potentially find the tab label.
// A more accurate mock of the component hierarchy might be needed for full robustness if `UIUtil` is deeply involved.
// Given the complexity, this is a good starting point for unit testing the core styling logic.
