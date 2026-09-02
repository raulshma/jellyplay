package com.raulshma.jellyplay.widget

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.deeplink.DeepLinkHandler

/**
 * Pins the widget deep-link intents: the built URIs reuse the shared scheme
 * constants (so [DeepLinkHandler] keeps parsing them), and both launch
 * intents target [MainActivity] with the documented flag stack —
 * NEW_TASK|CLEAR_TOP for plain opens, plus SINGLE_TOP and ACTION_VIEW for
 * URI opens so a widget tap reuses an existing shell instance and still
 * routes through the deep-link parser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetDeepLinksTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `media deep link reuses the shared custom scheme`() {
        assertEquals(
            "${DeepLinkHandler.SCHEME_CUSTOM}://media/item-1",
            WidgetDeepLinks.buildMediaDeepLink("item-1"),
        )
    }

    @Test
    fun `seerr deep link embeds tmdb id and media type`() {
        assertEquals(
            "jellyplay://seerr/42/tv",
            WidgetDeepLinks.buildSeerrDeepLink(42, "tv"),
        )
    }

    @Test
    fun `widget links round-trip through the deep-link handler`() {
        // The whole point of sharing the constants: what the widget builds,
        // the handler must parse to the matching route.
        val handler = DeepLinkHandler()

        val mediaRoute = handler.parse(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
            WidgetDeepLinks.buildMediaDeepLink("item-1"),
        )))
        assertEquals(
            com.raulshma.jellyplay.core.ui.navigation.Route.MediaDetail("item-1"),
            mediaRoute,
        )

        val seerrRoute = handler.parse(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
            WidgetDeepLinks.buildSeerrDeepLink(42, "tv"),
        )))
        assertEquals(
            com.raulshma.jellyplay.core.ui.navigation.Route.SeerrDetail(42, "tv"),
            seerrRoute,
        )
    }

    @Test
    fun `openAppIntent targets MainActivity with relaunch flags`() {
        val intent = WidgetDeepLinks.openAppIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags)
    }

    @Test
    fun `openUriIntent carries the view action, data and reuse flags`() {
        val uri = WidgetDeepLinks.buildMediaDeepLink("item-1")
        val intent = WidgetDeepLinks.openUriIntent(context, uri)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(android.net.Uri.parse(uri), intent.data)
        assertTrue(intent.hasCategory(Intent.CATEGORY_DEFAULT))
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
            intent.flags,
        )
        assertNotNull(intent.data)
    }
}
