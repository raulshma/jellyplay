package com.raulshma.jellyplay.core.model.seerr

import org.junit.Assert.assertEquals
import org.junit.Test

class SeerrSettingsTest {

    @Test
    fun `SeerrRadarrSettings getFullUrl returns externalUrl if present`() {
        val settings = SeerrRadarrSettings(
            id = 1,
            name = "Radarr",
            hostname = "192.168.1.10",
            port = 7878,
            apiKey = "key",
            externalUrl = "https://radarr.example.com/"
        )
        assertEquals("https://radarr.example.com", settings.getFullUrl())
    }

    @Test
    fun `SeerrRadarrSettings getFullUrl builds URL correctly with SSL`() {
        val settings = SeerrRadarrSettings(
            id = 1,
            name = "Radarr",
            hostname = "192.168.1.10",
            port = 7878,
            apiKey = "key",
            useSsl = true,
            baseUrl = "/radarr"
        )
        assertEquals("https://192.168.1.10:7878/radarr", settings.getFullUrl())
    }

    @Test
    fun `SeerrRadarrSettings getFullUrl builds URL correctly without SSL`() {
        val settings = SeerrRadarrSettings(
            id = 1,
            name = "Radarr",
            hostname = "192.168.1.10",
            port = 7878,
            apiKey = "key",
            useSsl = false
        )
        assertEquals("http://192.168.1.10:7878", settings.getFullUrl())
    }

    @Test
    fun `SeerrSonarrSettings getFullUrl builds URL correctly`() {
        val settings = SeerrSonarrSettings(
            id = 1,
            name = "Sonarr",
            hostname = "192.168.1.10",
            port = 8989,
            apiKey = "key",
            useSsl = false,
            baseUrl = ""
        )
        assertEquals("http://192.168.1.10:8989", settings.getFullUrl())
    }
}
