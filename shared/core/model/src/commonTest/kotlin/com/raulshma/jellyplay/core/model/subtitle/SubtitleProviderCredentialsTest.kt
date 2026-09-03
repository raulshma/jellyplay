package com.raulshma.jellyplay.core.model.subtitle

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the invariants of the subtitle-provider credentials + preferences:
 *
 *  - [SubtitleProviderCredentials.isConfigured] means "the secret material is
 *    present", independent of any enable toggle: Wyzie needs a non-blank API
 *    key; OpenSubtitles needs BOTH a non-null, non-blank username AND password
 *    (the cached JWT never counts toward configured-ness).
 *  - The sealed hierarchy serializes polymorphically with its `@SerialName`
 *    discriminators ("wyzie" / "opensubtitles") and round-trips to the same
 *    subclass — the secure store depends on that.
 *  - [SubtitleProviderPreferences.isEnabled]: JELLYFIN is ALWAYS enabled (no
 *    credentials); the two external providers follow their toggles only.
 *  - [SubtitleProviderPreferences.externalProviders] is exactly the
 *    user-toggleable pair, in sheet order.
 */
class SubtitleProviderCredentialsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── isConfigured ─────────────────────────────────────────────────────────

    @Test
    fun `wyzie is configured only with a non-blank api key`() {
        assertTrue(SubtitleProviderCredentials.Wyzie(apiKey = "key").isConfigured)
        assertFalse(SubtitleProviderCredentials.Wyzie(apiKey = "").isConfigured)
        assertFalse(SubtitleProviderCredentials.Wyzie(apiKey = "   ").isConfigured)
    }

    @Test
    fun `openSubtitles needs both username and password`() {
        assertTrue(
            SubtitleProviderCredentials.OpenSubtitles(username = "u", password = "p").isConfigured,
        )
        assertFalse(
            SubtitleProviderCredentials.OpenSubtitles(username = null, password = "p").isConfigured,
        )
        assertFalse(
            SubtitleProviderCredentials.OpenSubtitles(username = "u", password = null).isConfigured,
        )
        assertFalse(
            SubtitleProviderCredentials.OpenSubtitles(username = "", password = "p").isConfigured,
        )
        assertFalse(
            SubtitleProviderCredentials.OpenSubtitles(username = "u", password = "  ").isConfigured,
        )
    }

    @Test
    fun `a cached jwt never makes openSubtitles configured`() {
        assertFalse(
            SubtitleProviderCredentials.OpenSubtitles(
                username = null,
                password = null,
                jwt = "token",
                jwtExpiresAt = Long.MAX_VALUE,
            ).isConfigured,
        )
    }

    // ── polymorphic serialization ────────────────────────────────────────────

    @Test
    fun `credentials round-trip with their discriminator names`() {
        val wyzie: SubtitleProviderCredentials = SubtitleProviderCredentials.Wyzie(apiKey = "k")
        val wyzieJson = json.encodeToString(wyzie)
        assertTrue(wyzieJson.contains("\"wyzie\""), wyzieJson)
        assertIs<SubtitleProviderCredentials.Wyzie>(json.decodeFromString(
            SubtitleProviderCredentials.serializer(), wyzieJson,
        ))

        val openSubs: SubtitleProviderCredentials = SubtitleProviderCredentials.OpenSubtitles(
            username = "u",
            password = "p",
            jwt = "token",
            jwtExpiresAt = 42L,
        )
        val openSubsJson = json.encodeToString(openSubs)
        assertTrue(openSubsJson.contains("\"opensubtitles\""), openSubsJson)
        val decoded = json.decodeFromString(SubtitleProviderCredentials.serializer(), openSubsJson)
        assertIs<SubtitleProviderCredentials.OpenSubtitles>(decoded)
        assertEquals(42L, decoded.jwtExpiresAt)
    }

    @Test
    fun `openSubtitles jwt fields default away for a fresh login state`() {
        val encoded = json.encodeToString<SubtitleProviderCredentials>(
            SubtitleProviderCredentials.OpenSubtitles(username = "u", password = "p"),
        )
        val decoded = json.decodeFromString(SubtitleProviderCredentials.serializer(), encoded)
        val openSubs = decoded as SubtitleProviderCredentials.OpenSubtitles
        assertNull(openSubs.jwt)
        assertEquals(0L, openSubs.jwtExpiresAt)
    }

    // ── SubtitleProviderPreferences ──────────────────────────────────────────

    @Test
    fun `jellyfin is always enabled regardless of toggles`() {
        val prefs = SubtitleProviderPreferences(wyzieEnabled = false, openSubtitlesEnabled = false)
        assertTrue(prefs.isEnabled(SubtitleProviderKind.JELLYFIN))
    }

    @Test
    fun `external providers follow their toggles only`() {
        val allOff = SubtitleProviderPreferences(wyzieEnabled = false, openSubtitlesEnabled = false)
        assertFalse(allOff.isEnabled(SubtitleProviderKind.WYZIE))
        assertFalse(allOff.isEnabled(SubtitleProviderKind.OPENSUBTITLES))

        val wyzieOnly = SubtitleProviderPreferences(wyzieEnabled = true, openSubtitlesEnabled = false)
        assertTrue(wyzieOnly.isEnabled(SubtitleProviderKind.WYZIE))
        assertFalse(wyzieOnly.isEnabled(SubtitleProviderKind.OPENSUBTITLES))

        val openSubsOnly = SubtitleProviderPreferences(wyzieEnabled = false, openSubtitlesEnabled = true)
        assertFalse(openSubsOnly.isEnabled(SubtitleProviderKind.WYZIE))
        assertTrue(openSubsOnly.isEnabled(SubtitleProviderKind.OPENSUBTITLES))
    }

    @Test
    fun `external providers are exactly the toggleable pair`() {
        val prefs = SubtitleProviderPreferences()
        assertEquals(listOf(SubtitleProviderKind.WYZIE, SubtitleProviderKind.OPENSUBTITLES), prefs.externalProviders)
        assertTrue(SubtitleProviderKind.JELLYFIN !in prefs.externalProviders)
    }

    @Test
    fun `defaults keep both external providers disabled`() {
        val prefs = SubtitleProviderPreferences()
        assertFalse(prefs.wyzieEnabled)
        assertFalse(prefs.openSubtitlesEnabled)
    }
}
