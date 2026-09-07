package com.raulshma.jellyplay.core.model.seerr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the ONE Radarr/Sonarr base-URL grammar ([arrBaseUrl]) — the function
 * both `getFullUrl()` helpers delegate to and that the `*ServiceDetail`
 * fields can be run through. These tests pin TODAY's behaviour, including the
 * oddities: a blank externalUrl falls through (never blocks), and an
 * unset (0 or null) port is rendered verbatim as `:0`.
 */
class ArrBaseUrlTest {

    private fun url(
        externalUrl: String? = null,
        useSsl: Boolean = false,
        hostname: String? = "host",
        port: Int? = 7878,
        baseUrl: String? = null,
    ): String? = arrBaseUrl(externalUrl, useSsl, hostname, port, baseUrl)

    @Test
    fun `externalUrl wins verbatim with trailing slashes trimmed`() {
        assertEquals("https://radarr.example.com", url(externalUrl = "https://radarr.example.com/"))
        assertEquals("https://x.example", url(externalUrl = "https://x.example///"))
        // externalUrl precedence holds even alongside fully populated fields.
        assertEquals(
            "http://ext.example",
            url(externalUrl = "http://ext.example", useSsl = true, hostname = "h", port = 1, baseUrl = "/b"),
        )
    }

    @Test
    fun `blank externalUrl falls through to the assembled URL`() {
        assertEquals("http://host:7878", url(externalUrl = "   "))
        assertEquals("http://host:7878", url(externalUrl = ""))
    }

    @Test
    fun `null or blank hostname yields null`() {
        assertNull(url(hostname = null))
        assertNull(url(hostname = ""))
        assertNull(url(hostname = "   "))
        // Precedence order pinned: the externalUrl branch runs first, so it
        // wins even when the hostname is blank.
        assertEquals("https://ext.example", url(externalUrl = "https://ext.example", hostname = ""))
    }

    @Test
    fun `useSsl picks https vs http`() {
        assertEquals("https://host:7878", url(useSsl = true))
        assertEquals("http://host:7878", url(useSsl = false))
    }

    @Test
    fun `baseUrl becomes a single leading path segment`() {
        assertEquals("https://host:7878/radarr", url(useSsl = true, baseUrl = "/radarr"))
        assertEquals("http://host:7878/sonarr", url(baseUrl = "sonarr"))
        // Surrounding slashes collapse into one segment.
        assertEquals("http://host:7878/some/base", url(baseUrl = "/some/base/"))
        // Slash-only (or empty) baseUrl contributes no segment.
        assertEquals("http://host:7878", url(baseUrl = "///"))
        assertEquals("http://host:7878", url(baseUrl = ""))
        // Null baseUrl behaves the same as empty.
        assertEquals("http://host:7878", url(baseUrl = null))
    }

    @Test
    fun `port is rendered verbatim, including the unset 0 oddity`() {
        assertEquals("http://host:0", url(port = 0))
        assertEquals("http://host:8989", url(port = 8989))
        // A null port behaves as the same unset 0.
        assertEquals("http://host:0", url(port = null))
    }

    @Test
    fun `getFullUrl delegates to the shared grammar`() {
        val radarr = SeerrRadarrSettings(
            id = 1, name = "R", hostname = "h", port = 0, apiKey = "k",
            useSsl = true, baseUrl = "/b",
        )
        assertEquals("https://h:0/b", radarr.getFullUrl())

        val sonarr = SeerrSonarrSettings(
            id = 2, name = "S", hostname = "h", port = 8989, apiKey = "k",
            useSsl = false, externalUrl = "https://tv.example/",
        )
        assertEquals("https://tv.example", sonarr.getFullUrl())

        // Both map the grammar's null (blank hostname) to "".
        assertEquals(
            "",
            SeerrRadarrSettings(id = 3, name = "R", hostname = " ", port = 1, apiKey = "k").getFullUrl(),
        )
        assertEquals(
            "",
            SeerrSonarrSettings(id = 4, name = "S", hostname = "", port = 1, apiKey = "k").getFullUrl(),
        )
    }
}
