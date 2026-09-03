package com.raulshma.jellyplay.feature.admin.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integrity pins for the plugin-catalog trust + taxonomy constants
 * (`PluginCatalogConstants.kt`), mirroring jellyfin-web's plugin dashboard:
 *
 *  - only the official repository prefix is "trusted" — installs from it
 *    skip the security disclaimer, so a lookalike host, a scheme downgrade
 *    or a missing path boundary must NOT count as trusted;
 *  - fromServer normalizes the server taxonomy (case, spaces, "&") and
 *    defaults unknown/blank to OTHER;
 *  - the enum display names stay unique and non-blank.
 */
class PluginCatalogConstantsTest {

    // ── isTrustedRepository ──

    @Test
    fun `official repository prefix is trusted`() {
        assertTrue(isTrustedRepository(TRUSTED_REPO_URL))
        assertTrue(isTrustedRepository("https://repo.jellyfin.org/some/path"))
        assertTrue(isTrustedRepository("HTTPS://REPO.JELLYFIN.ORG/")) // case-insensitive
    }

    @Test
    fun `lookalike hosts and scheme downgrades are not trusted`() {
        // .org.evil.com shares the prefix but not the path boundary.
        assertFalse(isTrustedRepository("https://repo.jellyfin.org.evil.com/"))
        assertFalse(isTrustedRepository("http://repo.jellyfin.org/")) // scheme downgrade
        assertFalse(isTrustedRepository("https://evil.example.com/repo.jellyfin.org/"))
    }

    @Test
    fun `blank or missing repository urls are not trusted`() {
        assertFalse(isTrustedRepository(null))
        assertFalse(isTrustedRepository(""))
        assertFalse(isTrustedRepository("   "))
    }

    // ── PluginCategory.fromServer ──

    @Test
    fun `category mapping covers the server taxonomy`() {
        assertEquals(PluginCategory.ADMINISTRATION, PluginCategory.fromServer("Administration"))
        assertEquals(PluginCategory.GENERAL, PluginCategory.fromServer("general"))
        assertEquals(PluginCategory.ANIME, PluginCategory.fromServer("Anime"))
        assertEquals(PluginCategory.BOOKS, PluginCategory.fromServer("Books"))
        assertEquals(PluginCategory.LIVE_TV, PluginCategory.fromServer("Live TV"))
        assertEquals(PluginCategory.MOVIES_AND_SHOWS, PluginCategory.fromServer("Movies & Shows"))
        assertEquals(PluginCategory.MOVIES_AND_SHOWS, PluginCategory.fromServer("movies and shows"))
        assertEquals(PluginCategory.MUSIC, PluginCategory.fromServer("Music"))
        assertEquals(PluginCategory.SUBTITLES, PluginCategory.fromServer("Subtitles"))
    }

    @Test
    fun `unknown or blank categories default to other`() {
        assertEquals(PluginCategory.OTHER, PluginCategory.fromServer(" Metagenerator "))
        assertEquals(PluginCategory.OTHER, PluginCategory.fromServer("totally novel"))
        assertEquals(PluginCategory.OTHER, PluginCategory.fromServer(null))
        assertEquals(PluginCategory.OTHER, PluginCategory.fromServer(""))
    }

    // ── enum integrity ──

    @Test
    fun `display names are unique and non-blank`() {
        val categoryNames = PluginCategory.entries.map { it.displayName }
        assertEquals(categoryNames.size, categoryNames.toSet().size)
        assertTrue(categoryNames.all { it.isNotBlank() })

        val statusFilters = PluginStatusFilter.entries.map { it.displayName }
        assertEquals(statusFilters.size, statusFilters.toSet().size)
        assertTrue(statusFilters.all { it.isNotBlank() })
    }
}
