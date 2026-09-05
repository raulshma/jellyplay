package com.raulshma.jellyplay.core.model.deeplink

import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget.Library
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget.Search
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the deep-link grammar: every builder must emit the exact literal the
 * emitters/reader carried before the grammar existed, parsing must reject
 * malformed inputs with null (never a partial target), and every builder must
 * round-trip through [parseLink] to the target it was built for.
 */
class DeepLinkGrammarTest {

    // --- builder literals (relocation, not format change) ---

    @Test
    fun buildersEmitExactLiterals() {
        assertEquals("jellyplay://media/abc123", DeepLinkGrammar.mediaLink("abc123"))
        assertEquals(
            "https://raulshma.github.io/jellyplay/media/abc123",
            DeepLinkGrammar.webMediaLink("abc123"),
        )
        assertEquals(
            "jellyplay://newsletter/WEEKLY",
            DeepLinkGrammar.newsletterSectionLink("WEEKLY"),
        )
        assertEquals(
            "jellyplay://newsletter/CONTINUE_WATCHING",
            DeepLinkGrammar.continueWatchingLink(),
        )
        assertEquals("jellyplay://seerr/42/tv", DeepLinkGrammar.seerrLink(42, "tv"))
        assertEquals("jellyplay://search", DeepLinkGrammar.searchLink())
        assertEquals("jellyplay://settings", DeepLinkGrammar.settingsLink())
        assertEquals("jellyplay://downloads", DeepLinkGrammar.downloadsLink())
        assertEquals("jellyplay://library", DeepLinkGrammar.libraryLink())
    }

    // --- custom-scheme parsing ---

    @Test
    fun customMediaParsesItemId() {
        assertEquals(
            DeepLinkTarget.MediaDetail("abc123"),
            DeepLinkGrammar.parseCustom("media", listOf("abc123")),
        )
    }

    @Test
    fun customMediaWithoutItemIdIsRejected() {
        assertNull(DeepLinkGrammar.parseCustom("media", emptyList()))
    }

    @Test
    fun customNewsletterParsesSection() {
        assertEquals(
            DeepLinkTarget.NewsletterSection("CONTINUE_WATCHING"),
            DeepLinkGrammar.parseCustom("newsletter", listOf("CONTINUE_WATCHING")),
        )
    }

    @Test
    fun seerrUsesPathMediaType() {
        assertEquals(
            DeepLinkTarget.SeerrDetail(42, "tv"),
            DeepLinkGrammar.parseCustom("seerr", listOf("42", "tv")),
        )
    }

    @Test
    fun seerrFallsBackToQueryWhenPathSegmentMissing() {
        assertEquals(
            DeepLinkTarget.SeerrDetail(42, "movie"),
            DeepLinkGrammar.parseCustom("seerr", listOf("42"), mapOf("type" to "movie")),
        )
    }

    @Test
    fun seerrBlankPathSegmentFallsBackToQuery() {
        assertEquals(
            DeepLinkTarget.SeerrDetail(42, "movie"),
            DeepLinkGrammar.parseCustom("seerr", listOf("42", ""), mapOf("type" to "movie")),
        )
    }

    @Test
    fun seerrBlankQueryValueFallsBackToDefault() {
        assertEquals(
            DeepLinkTarget.SeerrDetail(42, "movie"),
            DeepLinkGrammar.parseCustom("seerr", listOf("42"), mapOf("type" to "")),
        )
    }

    @Test
    fun seerrDefaultsToMovieWithoutAnyType() {
        assertEquals(
            DeepLinkTarget.SeerrDetail(42, "movie"),
            DeepLinkGrammar.parseCustom("seerr", listOf("42")),
        )
    }

    @Test
    fun seerrNonNumericIdIsRejected() {
        assertNull(DeepLinkGrammar.parseCustom("seerr", listOf("notanumber", "tv")))
    }

    @Test
    fun seerrMissingIdIsRejected() {
        assertNull(DeepLinkGrammar.parseCustom("seerr", emptyList()))
    }

    @Test
    fun argumentLessHostsParseToObjects() {
        assertEquals(Search, DeepLinkGrammar.parseCustom("search", emptyList()))
        assertEquals(Settings, DeepLinkGrammar.parseCustom("settings", emptyList()))
        assertEquals(DeepLinkTarget.Downloads, DeepLinkGrammar.parseCustom("downloads", emptyList()))
        assertEquals(Library, DeepLinkGrammar.parseCustom("library", emptyList()))
    }

    @Test
    fun unknownOrNullHostIsRejected() {
        assertNull(DeepLinkGrammar.parseCustom("unknown", listOf("abc123")))
        assertNull(DeepLinkGrammar.parseCustom(null, listOf("abc123")))
    }

    // --- web-mirror parsing ---

    @Test
    fun webMediaParsesItemId() {
        assertEquals(
            DeepLinkTarget.MediaDetail("abc123"),
            DeepLinkGrammar.parseWeb(listOf("jellyplay", "media", "abc123")),
        )
    }

    @Test
    fun webNewsletterParsesSection() {
        assertEquals(
            DeepLinkTarget.NewsletterSection("CONTINUE_WATCHING"),
            DeepLinkGrammar.parseWeb(listOf("jellyplay", "newsletter", "CONTINUE_WATCHING")),
        )
    }

    @Test
    fun webArgumentLessDestinationsIgnoreTrailingSegment() {
        assertEquals(Search, DeepLinkGrammar.parseWeb(listOf("jellyplay", "search")))
        assertEquals(Search, DeepLinkGrammar.parseWeb(listOf("jellyplay", "search", "x")))
        assertEquals(Settings, DeepLinkGrammar.parseWeb(listOf("jellyplay", "settings")))
        assertEquals(DeepLinkTarget.Downloads, DeepLinkGrammar.parseWeb(listOf("jellyplay", "downloads")))
        assertEquals(Library, DeepLinkGrammar.parseWeb(listOf("jellyplay", "library")))
    }

    @Test
    fun webMediaWithoutIdSegmentYieldsEmptyId() {
        // Pinned from the router's historical behaviour: the absent trailing
        // segment becomes an empty item id, leaving rejection to the caller.
        assertEquals(
            DeepLinkTarget.MediaDetail(""),
            DeepLinkGrammar.parseWeb(listOf("jellyplay", "media")),
        )
    }

    @Test
    fun webLinksWithoutPrefixOrTypeAreRejected() {
        assertNull(DeepLinkGrammar.parseWeb(listOf("other", "media", "abc123")))
        assertNull(DeepLinkGrammar.parseWeb(listOf("jellyplay")))
        assertNull(DeepLinkGrammar.parseWeb(emptyList()))
    }

    @Test
    fun webUnknownDestinationTypeIsRejected() {
        assertNull(DeepLinkGrammar.parseWeb(listOf("jellyplay", "unknown", "xyz")))
    }

    // --- build → parse round-trips ---

    @Test
    fun everyBuilderRoundTripsToItsTarget() {
        assertEquals(DeepLinkTarget.MediaDetail("xyz"), parseLink(DeepLinkGrammar.mediaLink("xyz")))
        assertEquals(DeepLinkTarget.MediaDetail("xyz"), parseLink(DeepLinkGrammar.webMediaLink("xyz")))
        assertEquals(
            DeepLinkTarget.NewsletterSection("CONTINUE_WATCHING"),
            parseLink(DeepLinkGrammar.continueWatchingLink()),
        )
        assertEquals(
            DeepLinkTarget.SeerrDetail(7, "tv"),
            parseLink(DeepLinkGrammar.seerrLink(7, "tv")),
        )
        assertEquals(Search, parseLink(DeepLinkGrammar.searchLink()))
        assertEquals(Settings, parseLink(DeepLinkGrammar.settingsLink()))
        assertEquals(DeepLinkTarget.Downloads, parseLink(DeepLinkGrammar.downloadsLink()))
        assertEquals(Library, parseLink(DeepLinkGrammar.libraryLink()))
    }

    @Test
    fun foreignSchemeDoesNotRoundTrip() {
        assertNull(parseLink("otherapp://media/abc123"))
    }

    // --- test-local splitter: minimal stand-in for android.net.Uri on the
    // simple hierarchical URIs the builders emit (no empty path segments, no
    // percent encoding) ---

    private data class Parts(
        val scheme: String,
        val host: String,
        val pathSegments: List<String>,
        val queryParameters: Map<String, String>,
    )

    private fun split(link: String): Parts {
        val schemeEnd = link.indexOf("://")
        assertTrue(schemeEnd > 0, "not an absolute hierarchical URI: $link")
        val scheme = link.take(schemeEnd)
        val afterScheme = link.substring(schemeEnd + 3)
        val authority = afterScheme.substringBefore('/')
        val afterAuthority = afterScheme.removePrefix(authority)
        val path = afterAuthority.substringBefore('?')
        val queryPart = if ('?' in afterAuthority) afterAuthority.substringAfter('?') else ""
        val query = queryPart.split('&')
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val segments = if (path.isEmpty() || path == "/") {
            emptyList()
        } else {
            path.removePrefix("/").split('/')
        }
        return Parts(scheme, authority, segments, query)
    }

    private fun parseLink(link: String): DeepLinkTarget? {
        val parts = split(link)
        return when (parts.scheme) {
            DeepLinkGrammar.SCHEME_CUSTOM -> DeepLinkGrammar.parseCustom(
                host = parts.host,
                pathSegments = parts.pathSegments,
                queryParameters = parts.queryParameters,
            )
            DeepLinkGrammar.SCHEME_HTTPS -> {
                if (parts.host != DeepLinkGrammar.HOST_WEB) return null
                DeepLinkGrammar.parseWeb(parts.pathSegments)
            }
            else -> null
        }
    }
}
