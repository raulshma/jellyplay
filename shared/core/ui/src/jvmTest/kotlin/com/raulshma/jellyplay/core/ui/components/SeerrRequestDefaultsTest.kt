package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceProfile
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceRootFolder
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceServerDefaults
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins [SeerrRequestDefaults] — the request sheet's preselect decision table,
 * ported from the legacy :core:ui `SeerrRequestDialogDefaultsTest` androidTest
 * (instrumented, run by no CI lane; deleted once ported — the
 * `DownloadedSeasonSlicesTest` precedent). The dark test's whole point
 * survives: each fixture's expected default points at a NON-first entry (a
 * second root folder, profile or server) so a pass cannot be confused with the
 * index-0 fallback. Beyond the ported cases, this also pins the
 * re-application choreography rules the panel folds arrivals against: nested
 * /service/ defaults outrank top-level fields, anime defaults fall back to
 * regular ones (empty anime tags = absent), tags re-apply only on a
 * (server, anime) transition, and the season default yields to the first
 * manual pick.
 */
class SeerrRequestDefaultsTest {

    private fun profile(id: Int, name: String) = SeerrServiceProfile(id = id, name = name)

    private fun folder(id: Int, path: String) = SeerrServiceRootFolder(id = id, path = path)

    private fun nestedDefaults(
        id: Int = 1,
        name: String = "Server",
        isDefault: Boolean = false,
        is4k: Boolean = false,
        activeProfileId: Int? = null,
        activeAnimeProfileId: Int? = null,
        activeDirectory: String? = null,
        activeAnimeDirectory: String? = null,
        activeTags: List<Int> = emptyList(),
        activeAnimeTags: List<Int> = emptyList(),
    ) = SeerrServiceServerDefaults(
        id = id,
        name = name,
        isDefault = isDefault,
        is4k = is4k,
        activeProfileId = activeProfileId,
        activeAnimeProfileId = activeAnimeProfileId,
        activeDirectory = activeDirectory,
        activeAnimeDirectory = activeAnimeDirectory,
        activeTags = activeTags,
        activeAnimeTags = activeAnimeTags,
    )

    private fun radarr(
        id: Int,
        name: String,
        isDefault: Boolean = false,
        is4k: Boolean = false,
        activeDirectory: String? = null,
        activeProfileId: Int? = null,
        profiles: List<SeerrServiceProfile> = emptyList(),
        rootFolders: List<SeerrServiceRootFolder> = emptyList(),
        nested: SeerrServiceServerDefaults? = null,
    ) = SeerrRadarrServiceDetail(
        id = id,
        name = name,
        isDefault = isDefault,
        is4k = is4k,
        activeDirectory = activeDirectory,
        activeProfileId = activeProfileId,
        profiles = profiles,
        rootFolders = rootFolders,
        server = nested,
    )

    private fun sonarr(
        id: Int,
        name: String,
        isDefault: Boolean = false,
        is4k: Boolean = false,
        activeDirectory: String? = null,
        activeProfileId: Int? = null,
        profiles: List<SeerrServiceProfile> = emptyList(),
        rootFolders: List<SeerrServiceRootFolder> = emptyList(),
        nested: SeerrServiceServerDefaults? = null,
    ) = SeerrSonarrServiceDetail(
        id = id,
        name = name,
        isDefault = isDefault,
        is4k = is4k,
        activeDirectory = activeDirectory,
        activeProfileId = activeProfileId,
        profiles = profiles,
        rootFolders = rootFolders,
        server = nested,
    )

    @Test
    fun rootFolderAndProfilePreselectDefaultServerActiveValues_notIndexZeroFallback() {
        // The defaults point at the SECOND root folder and profile so a pass
        // cannot be confused with the index-0 fallback (the legacy dark test's
        // core fixture: service details arriving after the sheet is open).
        val server = radarr(
            id = 1,
            name = "Radarr Main",
            isDefault = true,
            activeDirectory = "/media/4k-movies",
            activeProfileId = 2,
            profiles = listOf(profile(1, "Any"), profile(2, "HD-1080p")),
            rootFolders = listOf(folder(1, "/media/movies"), folder(2, "/media/4k-movies")),
            nested = nestedDefaults(
                id = 1,
                name = "Radarr Main",
                isDefault = true,
                activeDirectory = "/media/4k-movies",
                activeProfileId = 2,
            ),
        )

        val folderIndex = SeerrRequestDefaults.defaultRootFolderIndex(server, server.rootFolders)
        val profileIndex = SeerrRequestDefaults.defaultProfileIndex(server, server.profiles)

        assertEquals("/media/4k-movies", server.rootFolders[folderIndex].path)
        assertEquals(1, folderIndex)
        assertEquals("HD-1080p", server.profiles[profileIndex].name)
        assertEquals(1, profileIndex)
    }

    @Test
    fun defaultServerWinsWhenNotFirstListed_itsDefaultsArePreselected() {
        val regular = radarr(
            id = 1,
            name = "Radarr",
            activeDirectory = "/media/movies",
            activeProfileId = 1,
            profiles = listOf(profile(1, "Any")),
            rootFolders = listOf(folder(1, "/media/movies")),
            nested = nestedDefaults(id = 1, name = "Radarr", activeDirectory = "/media/movies", activeProfileId = 1),
        )
        val defaultServer = radarr(
            id = 2,
            name = "Radarr 4K",
            isDefault = true,
            activeDirectory = "/media/4k-movies",
            activeProfileId = 2,
            profiles = listOf(profile(2, "Ultra-HD")),
            rootFolders = listOf(folder(3, "/media/4k-movies"), folder(4, "/media/4k-alt")),
            nested = nestedDefaults(id = 2, name = "Radarr 4K", isDefault = true, activeDirectory = "/media/4k-movies", activeProfileId = 2),
        )
        val servers = listOf(regular, defaultServer)

        val selectedIndex = SeerrRequestDefaults.defaultRadarrServerIndex(servers)

        // The default server's root folder + profile must be pre-selected,
        // not the first server's.
        assertEquals(1, selectedIndex)
        val selected = servers[selectedIndex]
        assertEquals(
            "/media/4k-movies",
            selected.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(selected, selected.rootFolders)].path,
        )
        assertEquals(
            "Ultra-HD",
            selected.profiles[SeerrRequestDefaults.defaultProfileIndex(selected, selected.profiles)].name,
        )
    }

    @Test
    fun tvRequestPreselectsSonarrDefaults() {
        val server = sonarr(
            id = 1,
            name = "Sonarr Main",
            isDefault = true,
            activeDirectory = "/media/tv",
            activeProfileId = 1,
            profiles = listOf(profile(1, "HD-1080p"), profile(2, "Any")),
            rootFolders = listOf(folder(1, "/media/anime"), folder(2, "/media/tv")),
            nested = nestedDefaults(
                id = 1,
                name = "Sonarr Main",
                isDefault = true,
                activeDirectory = "/media/tv",
                activeProfileId = 1,
            ),
        )

        // Regular series: the regular defaults — the root folder is the
        // SECOND entry, again not the index-0 fallback.
        val folderIndex = SeerrRequestDefaults.defaultRootFolderIndex(server, server.rootFolders, isAnime = false)
        val profileIndex = SeerrRequestDefaults.defaultProfileIndex(server, server.profiles, isAnime = false)

        assertEquals("/media/tv", server.rootFolders[folderIndex].path)
        assertEquals(1, folderIndex)
        assertEquals("HD-1080p", server.profiles[profileIndex].name)
    }

    @Test
    fun animeRequestPreselectsAnimeDefaults_regularSeriesKeepsRegularDefaults() {
        val server = sonarr(
            id = 1,
            name = "Sonarr Main",
            isDefault = true,
            activeDirectory = "/media/tv",
            activeProfileId = 1,
            profiles = listOf(profile(1, "HD-1080p"), profile(2, "Anime-1080p")),
            rootFolders = listOf(folder(1, "/media/tv"), folder(2, "/media/anime")),
            nested = nestedDefaults(
                id = 1,
                name = "Sonarr Main",
                isDefault = true,
                activeDirectory = "/media/tv",
                activeProfileId = 1,
                activeAnimeDirectory = "/media/anime",
                activeAnimeProfileId = 2,
            ),
        )

        // Anime: the anime-scoped defaults win (both land on the second entry).
        assertEquals(
            "/media/anime",
            server.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(server, server.rootFolders, isAnime = true)].path,
        )
        assertEquals(
            "Anime-1080p",
            server.profiles[SeerrRequestDefaults.defaultProfileIndex(server, server.profiles, isAnime = true)].name,
        )
        // The same server for a regular series lands on the regular defaults —
        // the anime-vs-regular fork is the whole rule.
        assertEquals(
            "/media/tv",
            server.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(server, server.rootFolders, isAnime = false)].path,
        )
        assertEquals(
            "HD-1080p",
            server.profiles[SeerrRequestDefaults.defaultProfileIndex(server, server.profiles, isAnime = false)].name,
        )
    }

    @Test
    fun defaultServerSkips4kInstanceWhenRegularDefaultExists() {
        // 4K server listed first AND flagged default — Jellyseerr still picks
        // the regular (non-4K) default, and so must the sheet.
        val server4k = radarr(
            id = 1,
            name = "Radarr 4K",
            isDefault = true,
            is4k = true,
            activeDirectory = "/media/4k-movies",
            activeProfileId = 1,
            profiles = listOf(profile(1, "Ultra-HD")),
            rootFolders = listOf(folder(1, "/media/4k-movies")),
            nested = nestedDefaults(id = 1, name = "Radarr 4K", isDefault = true, is4k = true, activeDirectory = "/media/4k-movies", activeProfileId = 1),
        )
        val regular = radarr(
            id = 2,
            name = "Radarr",
            isDefault = true,
            is4k = false,
            activeDirectory = "/media/movies",
            activeProfileId = 1,
            profiles = listOf(profile(1, "HD-1080p")),
            rootFolders = listOf(folder(2, "/media/movies")),
            nested = nestedDefaults(id = 2, name = "Radarr", isDefault = true, is4k = false, activeDirectory = "/media/movies", activeProfileId = 1),
        )
        val servers = listOf(server4k, regular)

        val selectedIndex = SeerrRequestDefaults.defaultRadarrServerIndex(servers)

        assertEquals(1, selectedIndex)
        val selected = servers[selectedIndex]
        assertEquals(
            "/media/movies",
            selected.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(selected, selected.rootFolders)].path,
        )
        assertEquals(
            "HD-1080p",
            selected.profiles[SeerrRequestDefaults.defaultProfileIndex(selected, selected.profiles)].name,
        )
    }

    @Test
    fun animeDefaultsFallBackToRegularWhenAbsent() {
        // No anime-scoped fields at all, and an EMPTY anime tag list — both
        // must behave as "absent", falling back to the regular defaults.
        val server = sonarr(
            id = 1,
            name = "Sonarr Main",
            activeDirectory = "/media/tv",
            activeProfileId = 1,
            profiles = listOf(profile(1, "HD-1080p"), profile(2, "Anime-1080p")),
            rootFolders = listOf(folder(1, "/media/tv"), folder(2, "/media/anime")),
            nested = nestedDefaults(
                id = 1,
                name = "Sonarr Main",
                activeDirectory = "/media/tv",
                activeProfileId = 1,
                activeTags = listOf(3),
                activeAnimeTags = emptyList(),
            ),
        )

        assertEquals(
            "HD-1080p",
            server.profiles[SeerrRequestDefaults.defaultProfileIndex(server, server.profiles, isAnime = true)].name,
        )
        assertEquals(
            "/media/tv",
            server.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(server, server.rootFolders, isAnime = true)].path,
        )
        assertEquals(listOf(3), SeerrRequestDefaults.defaultTags(server, isAnime = true))
        assertEquals(listOf(3), SeerrRequestDefaults.defaultTags(server, isAnime = false))

        // Anime tags present → they win over the regular ones.
        val animeTagged = server.copy(
            server = server.server?.copy(activeAnimeTags = listOf(5, 6)),
        )
        assertEquals(listOf(5, 6), SeerrRequestDefaults.defaultTags(animeTagged, isAnime = true))
    }

    @Test
    fun nestedServiceDefaultsOutrankTopLevelFields() {
        // Nested (from /service/) and top-level defaults disagree — the nested
        // values must win for both profiles and root folders.
        val server = radarr(
            id = 1,
            name = "Radarr",
            activeDirectory = "/media/a",
            activeProfileId = 1,
            profiles = listOf(profile(1, "Any"), profile(2, "HD-1080p")),
            rootFolders = listOf(folder(1, "/media/a"), folder(2, "/media/b")),
            nested = nestedDefaults(id = 1, name = "Radarr", activeDirectory = "/media/b", activeProfileId = 2),
        )

        assertEquals(
            "HD-1080p",
            server.profiles[SeerrRequestDefaults.defaultProfileIndex(server, server.profiles)].name,
        )
        assertEquals(
            "/media/b",
            server.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(server, server.rootFolders)].path,
        )
    }

    @Test
    fun topLevelFieldsWinOnlyWhenNestedDefaultsAreAbsent() {
        // No nested server object — the detail's top-level fields are the
        // defaults (the `?:` fallback arm both server types carry).
        val radarrTop = radarr(
            id = 1,
            name = "Radarr",
            activeDirectory = "/media/movies",
            activeProfileId = 2,
            profiles = listOf(profile(1, "Any"), profile(2, "HD-1080p")),
            rootFolders = listOf(folder(1, "/media/movies"), folder(2, "/media/alt")),
        )
        val sonarrTop = sonarr(
            id = 1,
            name = "Sonarr",
            activeDirectory = "/media/tv",
            activeProfileId = 2,
            profiles = listOf(profile(1, "Any"), profile(2, "HD-1080p")),
            rootFolders = listOf(folder(1, "/media/tv"), folder(2, "/media/alt")),
        )

        assertEquals(
            "HD-1080p",
            radarrTop.profiles[SeerrRequestDefaults.defaultProfileIndex(radarrTop, radarrTop.profiles)].name,
        )
        assertEquals(
            "/media/movies",
            radarrTop.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(radarrTop, radarrTop.rootFolders)].path,
        )
        assertEquals(
            "HD-1080p",
            sonarrTop.profiles[SeerrRequestDefaults.defaultProfileIndex(sonarrTop, sonarrTop.profiles, isAnime = true)].name,
        )
        assertEquals(
            "/media/tv",
            sonarrTop.rootFolders[SeerrRequestDefaults.defaultRootFolderIndex(sonarrTop, sonarrTop.rootFolders, isAnime = true)].path,
        )
    }

    @Test
    fun absentOrUnmatchedDefaultsFallBackToIndexZero() {
        val noDefaults = radarr(
            id = 1,
            name = "Radarr",
            profiles = listOf(profile(1, "Any"), profile(2, "HD-1080p")),
            rootFolders = listOf(folder(1, "/media/movies"), folder(2, "/media/4k-movies")),
        )
        assertEquals(0, SeerrRequestDefaults.defaultProfileIndex(noDefaults, noDefaults.profiles))
        assertEquals(0, SeerrRequestDefaults.defaultRootFolderIndex(noDefaults, noDefaults.rootFolders))

        // A default directory that matches no root folder entry → index 0.
        val unmatched = noDefaults.copy(activeDirectory = "/media/elsewhere")
        assertEquals(0, SeerrRequestDefaults.defaultRootFolderIndex(unmatched, unmatched.rootFolders))

        // Null servers / null lists → 0.
        assertEquals(0, SeerrRequestDefaults.defaultProfileIndex(server = null, profiles = null))
        assertEquals(0, SeerrRequestDefaults.defaultRootFolderIndex(server = null, rootFolders = null))
        assertEquals(0, SeerrRequestDefaults.defaultProfileIndex(server = null, profiles = listOf(profile(1, "Any"))))

        // Empty or default-less server lists → index 0.
        assertEquals(0, SeerrRequestDefaults.defaultRadarrServerIndex(emptyList<SeerrRadarrServiceDetail>()))
        assertEquals(0, SeerrRequestDefaults.defaultSonarrServerIndex(emptyList<SeerrSonarrServiceDetail>()))
        assertEquals(0, SeerrRequestDefaults.defaultRadarrServerIndex(listOf(noDefaults)))
    }

    @Test
    fun tagsReapplyOnlyWhenServerOrAnimeChanges() {
        // The choreography's fold: defaults (and only defaults) re-apply when
        // this key changes — manual tag edits survive list churn for the same
        // server and are reset on a server or anime transition.
        val sameServer = SeerrRequestDefaults.tagsApplicationKey(serverId = 1, isAnime = false)
        assertEquals(sameServer, SeerrRequestDefaults.tagsApplicationKey(serverId = 1, isAnime = false))
        assertNotEquals(sameServer, SeerrRequestDefaults.tagsApplicationKey(serverId = 2, isAnime = false))
        assertNotEquals(sameServer, SeerrRequestDefaults.tagsApplicationKey(serverId = 1, isAnime = true))

        // Radarr tags come from the nested defaults only; absent → empty.
        assertEquals(emptyList<Int>(), SeerrRequestDefaults.defaultTags(radarr(id = 1, name = "Radarr")))
        assertEquals(
            listOf(7, 8),
            SeerrRequestDefaults.defaultTags(radarr(id = 1, name = "Radarr", nested = nestedDefaults(activeTags = listOf(7, 8)))),
        )
    }

    @Test
    fun allSeasonsSelectedByDefault_untilAManualPickExists() {
        val seasons = listOf(SeerrSeason(seasonNumber = 1), SeerrSeason(seasonNumber = 2))

        assertTrue(SeerrRequestDefaults.selectAllSeasonsByDefault(seasons, selectedSeasonNumbers = emptyList()))
        assertFalse(SeerrRequestDefaults.selectAllSeasonsByDefault(seasons, selectedSeasonNumbers = listOf(1)))
        assertFalse(SeerrRequestDefaults.selectAllSeasonsByDefault(emptyList(), selectedSeasonNumbers = emptyList()))
    }
}
