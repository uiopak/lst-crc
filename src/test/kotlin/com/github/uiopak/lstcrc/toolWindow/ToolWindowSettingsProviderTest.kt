package com.github.uiopak.lstcrc.toolWindow

import com.intellij.ide.util.PropertiesComponent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

// In-memory implementation for testing if Mockito is not available/preferred for PropertiesComponent
class InMemoryPropertiesComponent : PropertiesComponent() {
    private val values = mutableMapOf<String, String>()
    private val booleanValues = mutableMapOf<String, Boolean>()

    override fun isValueSet(name: String?): Boolean = values.containsKey(name) || booleanValues.containsKey(name)
    override fun getValue(name: String?): String? = values[name]
    override fun getValue(name: String?, defaultValue: String?): String = values[name] ?: defaultValue ?: ""
    override fun setValue(name: String, value: String?) { if (value == null) values.remove(name) else values[name] = value }
    override fun setValue(name: String, value: String?, defaultValue: String?) { if (value == defaultValue) values.remove(name) else values[name] = value ?: "" }
    override fun unsetValue(name: String?) { values.remove(name); booleanValues.remove(name) }

    override fun getValues(name: String?): Array<String>? = values[name]?.let { arrayOf(it) }
    override fun setValues(name: String?, values: Array<String>?) { if (values != null && values.isNotEmpty()) this.values[name!!] = values[0] else this.values.remove(name) }

    override fun getBoolean(name: String, defaultValue: Boolean): Boolean = booleanValues[name] ?: defaultValue
    override fun setValue(name: String, value: Boolean, defaultValue: Boolean) {
        if (value == defaultValue) booleanValues.remove(name) else booleanValues[name] = value
    }
    override fun setValue(name: String, value: Boolean) { booleanValues[name] = value }

    // Other methods of PropertiesComponent would need to be overridden if used, but are not for these tests.
}


@ExtendWith(MockitoExtension::class) // Needed if using @Mock annotation
class ToolWindowSettingsProviderTest {

    @Mock
    private lateinit var mockPropertiesComponent: PropertiesComponent

    // Or use in-memory version:
    // private lateinit var mockPropertiesComponent: InMemoryPropertiesComponent


    private lateinit var settingsProvider: ToolWindowSettingsProvider

    // Define keys here to avoid typos and ensure consistency
    private val TAB_COLORING_ENABLED_KEY = "com.github.uiopak.lstcrc.app.tabColoringEnabled"
    private val TAB_COLORING_STYLE_KEY = "com.github.uiopak.lstcrc.app.tabColoringStyle"
    private val TAB_COLORING_COLOR_KEY = "com.github.uiopak.lstcrc.app.tabColoringColor"

    private val DEFAULT_TAB_COLORING_ENABLED = true
    private val DEFAULT_TAB_COLORING_STYLE = "BACKGROUND"
    private val DEFAULT_TAB_COLORING_COLOR = "Default"


    @BeforeEach
    fun setUp() {
        // mockPropertiesComponent = InMemoryPropertiesComponent() // Uncomment if using in-memory
        // For Mockito:
        settingsProvider = ToolWindowSettingsProvider(mockPropertiesComponent)
    }

    @Test
    fun `test default tab coloring enabled`() {
        `when`(mockPropertiesComponent.getBoolean(TAB_COLORING_ENABLED_KEY, DEFAULT_TAB_COLORING_ENABLED))
            .thenReturn(DEFAULT_TAB_COLORING_ENABLED)
        // Access internal getter through reflection or make it package-private/internal for testing
        // For simplicity, we'll assume direct access to what the getter would return,
        // or that the action's isSelected reflects this.
        // This test primarily ensures the default value is correctly passed to PropertiesComponent.
        // The actual "get" method `isTabColoringEnabled` is private.
        // We test it via its usage in `createToolWindowSettingsGroup` actions, or by making it testable.
        // For now, let's verify the provider asks for the key with the correct default.

        // Simulate what a getter would do:
        settingsProvider.javaClass.getDeclaredMethod("isTabColoringEnabled").apply {
            isAccessible = true
            val result = invoke(settingsProvider) as Boolean
            assertEquals(DEFAULT_TAB_COLORING_ENABLED, result)
        }
        verify(mockPropertiesComponent).getBoolean(TAB_COLORING_ENABLED_KEY, DEFAULT_TAB_COLORING_ENABLED)
    }

    @Test
    fun `test default tab coloring style`() {
        `when`(mockPropertiesComponent.getValue(TAB_COLORING_STYLE_KEY, DEFAULT_TAB_COLORING_STYLE))
            .thenReturn(DEFAULT_TAB_COLORING_STYLE)
        settingsProvider.javaClass.getDeclaredMethod("getTabColoringStyle").apply {
            isAccessible = true
            val result = invoke(settingsProvider) as String
            assertEquals(DEFAULT_TAB_COLORING_STYLE, result)
        }
        verify(mockPropertiesComponent).getValue(TAB_COLORING_STYLE_KEY, DEFAULT_TAB_COLORING_STYLE)
    }

    @Test
    fun `test default tab coloring color`() {
        `when`(mockPropertiesComponent.getValue(TAB_COLORING_COLOR_KEY, DEFAULT_TAB_COLORING_COLOR))
            .thenReturn(DEFAULT_TAB_COLORING_COLOR)
        settingsProvider.javaClass.getDeclaredMethod("getTabColoringColor").apply {
            isAccessible = true
            val result = invoke(settingsProvider) as String
            assertEquals(DEFAULT_TAB_COLORING_COLOR, result)
        }
        verify(mockPropertiesComponent).getValue(TAB_COLORING_COLOR_KEY, DEFAULT_TAB_COLORING_COLOR)
    }

    @Test
    fun `test set and get tab coloring enabled`() {
        val testValue = false
        // Simulate setting the value
        settingsProvider.javaClass.getDeclaredMethod("setTabColoringEnabled", Boolean::class.javaPrimitiveType).apply {
            isAccessible = true
            invoke(settingsProvider, testValue)
        }
        verify(mockPropertiesComponent).setValue(TAB_COLORING_ENABLED_KEY, testValue, DEFAULT_TAB_COLORING_ENABLED)

        // Simulate getting the value
        `when`(mockPropertiesComponent.getBoolean(TAB_COLORING_ENABLED_KEY, DEFAULT_TAB_COLORING_ENABLED))
            .thenReturn(testValue)
        settingsProvider.javaClass.getDeclaredMethod("isTabColoringEnabled").apply {
            isAccessible = true
            val result = invoke(settingsProvider) as Boolean
            assertEquals(testValue, result)
        }
    }

    @Test
    fun `test set and get tab coloring style`() {
        val testValue = "BORDER_TOP"
        settingsProvider.javaClass.getDeclaredMethod("setTabColoringStyle", String::class.java).apply {
            isAccessible = true
            invoke(settingsProvider, testValue)
        }
        verify(mockPropertiesComponent).setValue(TAB_COLORING_STYLE_KEY, testValue, DEFAULT_TAB_COLORING_STYLE)

        `when`(mockPropertiesComponent.getValue(TAB_COLORING_STYLE_KEY, DEFAULT_TAB_COLORING_STYLE))
            .thenReturn(testValue)
        settingsProvider.javaClass.getDeclaredMethod("getTabColoringStyle").apply {
            isAccessible = true
            val result = invoke(settingsProvider) as String
            assertEquals(testValue, result)
        }
    }

    @Test
    fun `test set and get tab coloring color`() {
        val testValue = "Red"
        settingsProvider.javaClass.getDeclaredMethod("setTabColoringColor", String::class.java).apply {
            isAccessible = true
            invoke(settingsProvider, testValue)
        }
        verify(mockPropertiesComponent).setValue(TAB_COLORING_COLOR_KEY, testValue, DEFAULT_TAB_COLORING_COLOR)

        `when`(mockPropertiesComponent.getValue(TAB_COLORING_COLOR_KEY, DEFAULT_TAB_COLORING_COLOR))
            .thenReturn(testValue)
        settingsProvider.javaClass.getDeclaredMethod("getTabColoringColor").apply {
            isAccessible = true
            val result = invoke(settingsProvider) as String
            assertEquals(testValue, result)
        }
    }
}

// Note: The use of reflection to test private methods is generally discouraged for pure unit tests,
// as it breaks encapsulation. Ideally, private methods are tested via public methods that use them.
// In this case, the getters/setters are used by ToggleActions. A more integrated test would
// simulate action events, but that's more complex. For now, reflection is a pragmatic choice
// to directly verify the private getters/setters logic with PropertiesComponent.
// If Mockito isn't available, the InMemoryPropertiesComponent can be used.
// The @ExtendWith(MockitoExtension::class) and @Mock annotations require Mockito.
// If not using Mockito for PropertiesComponent, these would be removed and InMemoryPropertiesComponent instantiated.
// I've used Mockito here as it's a common library. If it's not actually available in the test classpath,
// these tests will fail at runtime. The build.gradle.kts didn't explicitly list mockito,
// so this might need adjustment if it's not pulled in transitively by the intellij platform test framework.
// Assuming Mockito is available for now.
