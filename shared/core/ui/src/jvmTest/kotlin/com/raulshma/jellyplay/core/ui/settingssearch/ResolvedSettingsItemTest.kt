package com.raulshma.jellyplay.core.ui.settingssearch

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_loading
import com.raulshma.jellyplay.core.ui.generated.resources.core_search
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the plain-[String] projection of a search item ([ResolvedSettingsItem]):
 * the `id` / `route` / `icon` / `isAdvanced` accessors DELEGATE to the wrapped
 * [SettingsSearchItem] (not to the resolved strings), `isAdvanced` defaults to
 * false, and the resolved title/subtitle/category ride along as constructor
 * state with data semantics. The strings are never resolved from the
 * resources here — the same JVM-pure pattern as [SettingsSearchMatcherTest].
 */
class ResolvedSettingsItemTest {

    // One stable dummy icon instance per test instance — the delegation
    // assertions compare it by identity, so it must not be rebuilt per access.
    private val mockIcon: ImageVector = ImageVector.Builder(
        name = "test",
        defaultWidth = androidx.compose.ui.unit.Dp.Unspecified,
        defaultHeight = androidx.compose.ui.unit.Dp.Unspecified,
        viewportWidth = 1f,
        viewportHeight = 1f,
    ).build()

    private fun item(
        id: String,
        isAdvanced: Boolean = false,
    ) = SettingsSearchItem(
        id = id,
        titleRes = Res.string.core_search,
        subtitleRes = Res.string.core_loading,
        categoryRes = Res.string.core_loading,
        keywords = listOf("kw"),
        route = Route.PlaybackSettings(),
        icon = mockIcon,
        isAdvanced = isAdvanced,
    )

    private fun resolved(
        id: String,
        isAdvanced: Boolean = false,
        title: String = "Title",
    ) = ResolvedSettingsItem(
        item = item(id, isAdvanced),
        title = title,
        subtitle = "Sub",
        category = "Cat",
    )

    @Test
    fun accessors_delegateToTheWrappedItem() {
        val resolved = resolved("advanced_setting", isAdvanced = true)

        assertEquals("advanced_setting", resolved.id)
        assertEquals(Route.PlaybackSettings(), resolved.route)
        assertEquals(mockIcon, resolved.icon)
        assertTrue(resolved.isAdvanced)
    }

    @Test
    fun isAdvanced_defaultsToFalse() {
        assertFalse(resolved("plain").isAdvanced)
    }

    @Test
    fun resolvedText_isCarriedVerbatim() {
        val resolved = resolved("a", title = "Audio Passthrough")

        assertEquals("Audio Passthrough", resolved.title)
        assertEquals("Sub", resolved.subtitle)
        assertEquals("Cat", resolved.category)
    }

    @Test
    fun identity_coversBothWrappedItemAndResolvedStrings() {
        assertEquals(resolved("a"), resolved("a"))
        assertEquals(resolved("a", title = "X"), resolved("a", title = "X"))
        assertTrue(resolved("a", title = "X") != resolved("a", title = "Y"))
    }
}
