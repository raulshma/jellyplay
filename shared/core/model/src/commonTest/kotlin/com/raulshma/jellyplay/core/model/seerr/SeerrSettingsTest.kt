package com.raulshma.jellyplay.core.model.seerr

import kotlin.test.assertEquals
import kotlin.test.Test

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
        assertEquals(
settings.getFullUrl(),
"https://radarr.example.com",
)
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
        assertEquals(
settings.getFullUrl(),
"https://192.168.1.10:7878/radarr",
)
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
        assertEquals(
settings.getFullUrl(),
"http://192.168.1.10:7878",
)
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
        assertEquals(
settings.getFullUrl(),
"http://192.168.1.10:8989",
)
    }
}
