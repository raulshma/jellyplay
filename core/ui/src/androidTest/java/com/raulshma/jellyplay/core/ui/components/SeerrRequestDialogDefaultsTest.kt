package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceProfile
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceRootFolder
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceServerDefaults
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the request sheet pre-selects the media-type default server's
 * activeDirectory (root folder) and activeProfileId (quality profile) once
 * service details arrive, mirroring Overseerr/Jellyseerr's request modal.
 */
@RunWith(AndroidJUnit4::class)
class SeerrRequestDialogDefaultsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rootFolderPreselectsDefaultServerActiveDirectory() {
        // Default points at the SECOND root folder / profile so a pass cannot
        // be confused with the index-0 fallback.
        val defaultServer = SeerrRadarrServiceDetail(
            id = 1,
            name = "Radarr Main",
            isDefault = true,
            activeDirectory = "/media/4k-movies",
            activeProfileId = 2,
            profiles = listOf(
                SeerrServiceProfile(id = 1, name = "Any"),
                SeerrServiceProfile(id = 2, name = "HD-1080p"),
            ),
            rootFolders = listOf(
                SeerrServiceRootFolder(id = 1, path = "/media/movies"),
                SeerrServiceRootFolder(id = 2, path = "/media/4k-movies"),
            ),
            server = SeerrServiceServerDefaults(
                id = 1,
                name = "Radarr Main",
                isDefault = true,
                activeDirectory = "/media/4k-movies",
                activeProfileId = 2,
            ),
        )
        var servers by mutableStateOf(emptyList<SeerrRadarrServiceDetail>())

        composeTestRule.setContent {
            MaterialTheme {
                SeerrRequestPanel(
                    item = SeerrSearchItem(id = 42, mediaType = "movie", title = "Test Movie"),
                    radarrServers = servers,
                    onDismiss = {},
                )
            }
        }

        // Simulate service details arriving after the sheet is already open.
        composeTestRule.runOnIdle { servers = listOf(defaultServer) }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("/media/movies").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("/media/4k-movies").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("/media/4k-movies").assertExists()
        composeTestRule.onNodeWithText("HD-1080p").assertExists()
    }

    @Test
    fun rootFolderPreselectsDefaultServerWhenDefaultIsNotFirst() {
        val regular = SeerrRadarrServiceDetail(
            id = 1,
            name = "Radarr",
            isDefault = false,
            activeDirectory = "/media/movies",
            activeProfileId = 1,
            profiles = listOf(SeerrServiceProfile(1, "Any")),
            rootFolders = listOf(SeerrServiceRootFolder(1, "/media/movies")),
            server = SeerrServiceServerDefaults(id = 1, name = "Radarr", isDefault = false, activeDirectory = "/media/movies", activeProfileId = 1),
        )
        val defaultServer = SeerrRadarrServiceDetail(
            id = 2,
            name = "Radarr 4K",
            isDefault = true,
            activeDirectory = "/media/4k-movies",
            activeProfileId = 2,
            profiles = listOf(SeerrServiceProfile(2, "Ultra-HD")),
            rootFolders = listOf(
                SeerrServiceRootFolder(3, "/media/4k-movies"),
                SeerrServiceRootFolder(4, "/media/4k-alt"),
            ),
            server = SeerrServiceServerDefaults(id = 2, name = "Radarr 4K", isDefault = true, activeDirectory = "/media/4k-movies", activeProfileId = 2),
        )
        var servers by mutableStateOf(emptyList<SeerrRadarrServiceDetail>())

        composeTestRule.setContent {
            MaterialTheme {
                SeerrRequestPanel(
                    item = SeerrSearchItem(id = 43, mediaType = "movie", title = "Multi Server Movie"),
                    radarrServers = servers,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.runOnIdle { servers = listOf(regular, defaultServer) }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Radarr 4K").fetchSemanticsNodes().isNotEmpty()
        }

        // Default server's root folder + profile must be pre-selected, not the
        // first server's.
        composeTestRule.onNodeWithText("/media/4k-movies").assertExists()
        composeTestRule.onNodeWithText("Ultra-HD").assertExists()
    }

    @Test
    fun tvRequestPreselectsSonarrDefaults() {
        val sonarr = SeerrSonarrServiceDetail(
            id = 1,
            name = "Sonarr Main",
            isDefault = true,
            activeDirectory = "/media/tv",
            activeProfileId = 1,
            profiles = listOf(
                SeerrServiceProfile(1, "HD-1080p"),
                SeerrServiceProfile(2, "Any"),
            ),
            rootFolders = listOf(
                SeerrServiceRootFolder(1, "/media/anime"),
                SeerrServiceRootFolder(2, "/media/tv"),
            ),
            server = SeerrServiceServerDefaults(
                id = 1,
                name = "Sonarr Main",
                isDefault = true,
                activeDirectory = "/media/tv",
                activeProfileId = 1,
            ),
        )
        var servers by mutableStateOf(emptyList<SeerrSonarrServiceDetail>())

        composeTestRule.setContent {
            MaterialTheme {
                SeerrRequestPanel(
                    item = SeerrSearchItem(id = 44, mediaType = "tv", name = "Test Show"),
                    sonarrServers = servers,
                    seasons = emptyList(),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.runOnIdle { servers = listOf(sonarr) }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("/media/tv").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("/media/anime").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("/media/tv").assertExists()
        composeTestRule.onNodeWithText("HD-1080p").assertExists()
    }

    @Test
    fun animeRequestPreselectsAnimeDefaults() {
        val sonarr = SeerrSonarrServiceDetail(
            id = 1,
            name = "Sonarr Main",
            isDefault = true,
            activeDirectory = "/media/tv",
            activeProfileId = 1,
            profiles = listOf(
                SeerrServiceProfile(1, "HD-1080p"),
                SeerrServiceProfile(2, "Anime-1080p"),
            ),
            rootFolders = listOf(
                SeerrServiceRootFolder(1, "/media/tv"),
                SeerrServiceRootFolder(2, "/media/anime"),
            ),
            server = SeerrServiceServerDefaults(
                id = 1,
                name = "Sonarr Main",
                isDefault = true,
                activeDirectory = "/media/tv",
                activeProfileId = 1,
                activeAnimeDirectory = "/media/anime",
                activeAnimeProfileId = 2,
            ),
        )
        var servers by mutableStateOf(emptyList<SeerrSonarrServiceDetail>())

        composeTestRule.setContent {
            MaterialTheme {
                SeerrRequestPanel(
                    item = SeerrSearchItem(id = 46, mediaType = "tv", name = "Anime Show"),
                    sonarrServers = servers,
                    seasons = emptyList(),
                    isAnime = true,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.runOnIdle { servers = listOf(sonarr) }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("/media/tv").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("/media/anime").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("/media/anime").assertExists()
        composeTestRule.onNodeWithText("Anime-1080p").assertExists()
    }

    @Test
    fun defaultServerSkips4kInstanceWhenRegularDefaultExists() {
        // 4K server listed first AND flagged default — Jellyseerr still picks
        // the regular (non-4K) default, and so must the sheet.
        val server4k = SeerrRadarrServiceDetail(
            id = 1,
            name = "Radarr 4K",
            isDefault = true,
            is4k = true,
            activeDirectory = "/media/4k-movies",
            activeProfileId = 1,
            profiles = listOf(SeerrServiceProfile(1, "Ultra-HD")),
            rootFolders = listOf(SeerrServiceRootFolder(1, "/media/4k-movies")),
            server = SeerrServiceServerDefaults(id = 1, name = "Radarr 4K", isDefault = true, is4k = true, activeDirectory = "/media/4k-movies", activeProfileId = 1),
        )
        val regular = SeerrRadarrServiceDetail(
            id = 2,
            name = "Radarr",
            isDefault = true,
            is4k = false,
            activeDirectory = "/media/movies",
            activeProfileId = 1,
            profiles = listOf(SeerrServiceProfile(1, "HD-1080p")),
            rootFolders = listOf(SeerrServiceRootFolder(2, "/media/movies")),
            server = SeerrServiceServerDefaults(id = 2, name = "Radarr", isDefault = true, is4k = false, activeDirectory = "/media/movies", activeProfileId = 1),
        )
        var servers by mutableStateOf(emptyList<SeerrRadarrServiceDetail>())

        composeTestRule.setContent {
            MaterialTheme {
                SeerrRequestPanel(
                    item = SeerrSearchItem(id = 47, mediaType = "movie", title = "4K Default Movie"),
                    radarrServers = servers,
                    onDismiss = {},
                )
            }
        }

        composeTestRule.runOnIdle { servers = listOf(server4k, regular) }

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Radarr").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("/media/movies").assertExists()
        composeTestRule.onNodeWithText("HD-1080p").assertExists()
    }
}
