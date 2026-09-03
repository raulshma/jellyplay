package com.raulshma.jellyplay.deeplink

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the deep-link router: every supported `jellyplay://` host and its
 * `https://raulshma.github.io/jellyplay/...` equivalent must resolve to the
 * matching [Route], rejected inputs (unknown hosts, malformed ids, foreign
 * schemes/hosts) must yield null rather than a partial route, and every
 * `createXLink` builder must round-trip through [DeepLinkHandler.parse] to
 * the route it was built for — the widgets/notifications/shortcuts jump
 * path depends on that symmetry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeepLinkHandlerTest {

    private val handler = DeepLinkHandler()

    private fun parse(uri: String): Route? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        return handler.parse(intent)
    }

    // --- custom scheme: media / newsletter ---

    @Test
    fun `custom media link resolves to MediaDetail with item id`() {
        assertEquals(
            Route.MediaDetail("abc123"),
            parse("jellyplay://media/abc123"),
        )
    }

    @Test
    fun `custom media link without item id is rejected`() {
        assertNull(parse("jellyplay://media"))
    }

    @Test
    fun `custom newsletter link resolves to NewsletterSectionList`() {
        assertEquals(
            Route.NewsletterSectionList("CONTINUE_WATCHING"),
            parse("jellyplay://newsletter/CONTINUE_WATCHING"),
        )
    }

    // --- custom scheme: seerr ---

    @Test
    fun `custom seerr link uses path media type`() {
        assertEquals(
            Route.SeerrDetail(tmdbId = 42, mediaType = "tv"),
            parse("jellyplay://seerr/42/tv"),
        )
    }

    @Test
    fun `seerr type falls back to query parameter when path segment blank`() {
        assertEquals(
            Route.SeerrDetail(tmdbId = 42, mediaType = "movie"),
            parse("jellyplay://seerr/42?type=movie"),
        )
    }

    @Test
    fun `seerr defaults to movie when neither path nor query carry a type`() {
        assertEquals(
            Route.SeerrDetail(tmdbId = 42, mediaType = "movie"),
            parse("jellyplay://seerr/42"),
        )
    }

    @Test
    fun `seerr link with non-numeric tmdb id is rejected`() {
        assertNull(parse("jellyplay://seerr/notanumber/tv"))
    }

    // --- custom scheme: argument-less top-level destinations ---

    @Test
    fun `argument-less hosts resolve to their route objects`() {
        assertEquals(Route.Search, parse("jellyplay://search"))
        assertEquals(Route.Settings, parse("jellyplay://settings"))
        assertEquals(Route.Downloads, parse("jellyplay://downloads"))
        assertEquals(Route.Library, parse("jellyplay://library"))
    }

    // --- rejections ---

    @Test
    fun `unknown host is rejected`() {
        assertNull(parse("jellyplay://unknown"))
    }

    @Test
    fun `missing host is rejected`() {
        assertNull(parse("jellyplay://"))
    }

    @Test
    fun `foreign scheme is rejected`() {
        assertNull(parse("otherapp://media/abc123"))
    }

    @Test
    fun `intent without data is rejected`() {
        assertNull(handler.parse(Intent(Intent.ACTION_VIEW)))
    }

    // --- https scheme ---

    @Test
    fun `web media link resolves to MediaDetail`() {
        assertEquals(
            Route.MediaDetail("abc123"),
            parse("https://raulshma.github.io/jellyplay/media/abc123"),
        )
    }

    @Test
    fun `web newsletter link resolves to NewsletterSectionList`() {
        assertEquals(
            Route.NewsletterSectionList("CONTINUE_WATCHING"),
            parse("https://raulshma.github.io/jellyplay/newsletter/CONTINUE_WATCHING"),
        )
    }

    @Test
    fun `web argument-less destinations ignore the trailing id segment`() {
        assertEquals(Route.Search, parse("https://raulshma.github.io/jellyplay/search"))
        assertEquals(Route.Settings, parse("https://raulshma.github.io/jellyplay/settings"))
        assertEquals(Route.Downloads, parse("https://raulshma.github.io/jellyplay/downloads"))
        assertEquals(Route.Library, parse("https://raulshma.github.io/jellyplay/library"))
    }

    @Test
    fun `web media link without id segment resolves to empty-id MediaDetail`() {
        // parseHttpsScheme only requires prefix + type for item-bearing
        // routes; the absent trailing segment becomes an empty item id —
        // pinned so the navigation layer's own empty-id guard stays the
        // single rejection point.
        assertEquals(
            Route.MediaDetail(""),
            parse("https://raulshma.github.io/jellyplay/media"),
        )
    }

    @Test
    fun `web link with foreign host is rejected`() {
        assertNull(parse("https://evil.example.com/jellyplay/media/abc123"))
    }

    @Test
    fun `web link missing the jellyplay path prefix is rejected`() {
        assertNull(parse("https://raulshma.github.io/other/media/abc123"))
    }

    @Test
    fun `web link with only the prefix is rejected`() {
        assertNull(parse("https://raulshma.github.io/jellyplay"))
    }

    @Test
    fun `web link with unknown destination type is rejected`() {
        assertNull(parse("https://raulshma.github.io/jellyplay/unknown/xyz"))
    }

    // --- builder round-trips ---

    @Test
    fun `every builder round-trips to its expected route`() {
        assertEquals(
            Route.MediaDetail("xyz"),
            parse(DeepLinkHandler.createMediaLink("xyz")),
        )
        assertEquals(
            Route.MediaDetail("xyz"),
            parse(DeepLinkHandler.createWebMediaLink("xyz")),
        )
        assertEquals(
            Route.NewsletterSectionList("CONTINUE_WATCHING"),
            parse(DeepLinkHandler.createContinueWatchingLink()),
        )
        assertEquals(
            Route.SeerrDetail(7, "tv"),
            parse(DeepLinkHandler.createSeerrLink(7, "tv")),
        )
        assertEquals(Route.Search, parse(DeepLinkHandler.createSearchLink()))
        assertEquals(Route.Settings, parse(DeepLinkHandler.createSettingsLink()))
        assertEquals(Route.Downloads, parse(DeepLinkHandler.createDownloadsLink()))
        assertEquals(Route.Library, parse(DeepLinkHandler.createLibraryLink()))
    }
}
