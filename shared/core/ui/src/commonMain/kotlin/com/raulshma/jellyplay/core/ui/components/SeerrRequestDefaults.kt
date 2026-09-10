package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceProfile
import com.raulshma.jellyplay.core.model.seerr.SeerrServiceRootFolder
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail

/**
 * Pure preselect decision table for the Seerr request sheet
 * ([SeerrRequestPanel]): which server, quality profile, root folder, tag set
 * and season spread the sheet starts from once service details arrive.
 * Extracted verbatim from the panel's inline closures (the `animeDefault`
 * helper, the `defaultIndex` local and the auto-select `LaunchedEffect`
 * bodies) so the JVM test lane can pin it — previously the only pin was a
 * legacy :core:ui androidTest no CI lane runs (the `PlayerKeyPolicy`
 * precedent: leaf math in a pure object beside the component, effect shells
 * stay in composition). No Compose types in these signatures.
 *
 * Decision rules (Jellyseerr's requester, mirrored):
 *  - default server: the first entry flagged default (top-level OR nested
 *    /service/ view) that is NOT a 4K instance — the app never requests 4K,
 *    so a 4K instance flagged default must not win over the regular one;
 *    empty or default-less lists fall back to index 0;
 *  - quality profile / root folder: the nested server defaults (from the
 *    /service/ endpoint) outrank the detail's top-level fields; for anime
 *    series the anime-scoped default wins with the regular default as
 *    fallback ([animeDefault] — Jellyseerr falls back the same way); an
 *    id/path matching no list entry (or no default at all) falls back to
 *    index 0, coerced into list bounds;
 *  - tags: same nested/anime resolution, with an EMPTY anime tag list
 *    treated as absent (falls back to the regular tags); radarr has no anime
 *    arm and no top-level fallback — its tags come from the nested defaults
 *    only.
 *
 * Re-application choreography — the genuinely stateful half, modelled here
 * as explicit rules while the remember/`LaunchedEffect` shells stay in the
 * panel. Server, profile and root-folder defaults are re-derived from the
 * LATEST inputs every time the server lists, current profiles or anime flag
 * change: a manual selection survives only until the next input change
 * (e.g. a refresh) re-runs the fold — there is no "first details win".
 * Tags are the deliberate exception: [tagsApplicationKey] collapses
 * (server id, anime) into the key the panel compares against the last
 * APPLIED key, so manual tag edits survive list churn within one server and
 * are reset only on a server/anime transition. Seasons default to every
 * season ([selectAllSeasonsByDefault]) for as long as no manual per-season
 * pick exists.
 *
 * Declared delta vs the former inline closures: none — every decision is
 * byte-identical; only the leaf math moved out of composition.
 */
internal object SeerrRequestDefaults {

    /**
     * The server's anime-scoped default ([anime]) when requesting an anime
     * series, else its regular default ([regular]) — Jellyseerr's requester
     * falls back the same way for profiles, root folders and tags.
     */
    fun <T> animeDefault(isAnime: Boolean, anime: T?, regular: T?): T? =
        if (isAnime) anime ?: regular else regular

    /**
     * Index of the movie (radarr) server the sheet preselects; see the object
     * KDoc's default-server rule. Empty list → 0. (Named per media type, not
     * overloaded: the two server list types erase to the same JVM signature.)
     */
    fun defaultRadarrServerIndex(radarrServers: List<SeerrRadarrServiceDetail>): Int =
        defaultIndex(radarrServers.size) { radarrServers[it].isRequestDefault() }

    /**
     * Index of the series (sonarr) server the sheet preselects; the exact
     * twin of [defaultRadarrServerIndex]. Empty list → 0.
     */
    fun defaultSonarrServerIndex(sonarrServers: List<SeerrSonarrServiceDetail>): Int =
        defaultIndex(sonarrServers.size) { sonarrServers[it].isRequestDefault() }

    /**
     * Index into [profiles] of the radarr server's default quality profile:
     * the nested `activeProfileId` outranking the top-level one (movies have
     * no anime arm). Unmatched or absent default → 0.
     */
    fun defaultProfileIndex(
        server: SeerrRadarrServiceDetail?,
        profiles: List<SeerrServiceProfile>?,
    ): Int {
        val defaultId = server?.server?.activeProfileId ?: server?.activeProfileId
        return indexOfDefaultProfile(profiles, defaultId)
    }

    /**
     * Index into [profiles] of the sonarr server's default quality profile:
     * anime series use the anime-scoped `activeAnimeProfileId` with the
     * regular `activeProfileId` as fallback, then the top-level
     * `activeProfileId` when the nested defaults are absent. Unmatched or
     * absent default → 0.
     */
    fun defaultProfileIndex(
        server: SeerrSonarrServiceDetail?,
        profiles: List<SeerrServiceProfile>?,
        isAnime: Boolean,
    ): Int {
        val nested = server?.server
        val defaultId = animeDefault(isAnime, nested?.activeAnimeProfileId, nested?.activeProfileId)
            ?: server?.activeProfileId
        return indexOfDefaultProfile(profiles, defaultId)
    }

    /**
     * Index into [rootFolders] of the radarr server's default root folder
     * (matched by path); the nested `activeDirectory` outranks the top-level
     * one. Unmatched or absent default → 0.
     */
    fun defaultRootFolderIndex(
        server: SeerrRadarrServiceDetail?,
        rootFolders: List<SeerrServiceRootFolder>?,
    ): Int {
        val defaultDir = server?.server?.activeDirectory ?: server?.activeDirectory
        return indexOfDefaultRootFolder(rootFolders, defaultDir)
    }

    /**
     * Index into [rootFolders] of the sonarr server's default root folder:
     * anime series use the anime-scoped `activeAnimeDirectory` with the
     * regular `activeDirectory` as fallback, then the top-level
     * `activeDirectory` when the nested defaults are absent. Unmatched or
     * absent default → 0.
     */
    fun defaultRootFolderIndex(
        server: SeerrSonarrServiceDetail?,
        rootFolders: List<SeerrServiceRootFolder>?,
        isAnime: Boolean,
    ): Int {
        val nested = server?.server
        val defaultDir = animeDefault(isAnime, nested?.activeAnimeDirectory, nested?.activeDirectory)
            ?: server?.activeDirectory
        return indexOfDefaultRootFolder(rootFolders, defaultDir)
    }

    /**
     * Default tag ids for a movie (radarr) request: nested `activeTags` only —
     * radarr details carry no top-level or anime-scoped tag fields. Empty when
     * the server (or its nested defaults) is absent.
     */
    fun defaultTags(server: SeerrRadarrServiceDetail?): List<Int> =
        server?.server?.activeTags ?: emptyList()

    /**
     * Default tag ids for a series (sonarr) request: anime series use the
     * anime-scoped `activeAnimeTags` — with an EMPTY list treated as absent,
     * falling back to the regular `activeTags`. Empty when neither default
     * exists.
     */
    fun defaultTags(server: SeerrSonarrServiceDetail?, isAnime: Boolean): List<Int> {
        val nested = server?.server
        return animeDefault(
            isAnime,
            nested?.activeAnimeTags?.takeIf { it.isNotEmpty() },
            nested?.activeTags,
        ) ?: emptyList()
    }

    /**
     * The tags re-application key: defaults are re-applied (wiping manual tag
     * edits) only when this key CHANGES, i.e. on a server or anime transition
     * — never on mere list churn for the same server. The panel holds the
     * last applied key and folds each arrival against it.
     */
    fun tagsApplicationKey(serverId: Int?, isAnime: Boolean): Pair<Int?, Boolean> = serverId to isAnime

    /**
     * True while the sheet should sit on "all seasons": some season has
     * arrived AND the user has not made a manual per-season pick yet (once
     * [selectedSeasonNumbers] is non-empty the default stays out of the way).
     */
    fun selectAllSeasonsByDefault(
        seasons: List<SeerrSeason>,
        selectedSeasonNumbers: List<Int>,
    ): Boolean = seasons.isNotEmpty() && selectedSeasonNumbers.isEmpty()

    /**
     * First index of a server the app would actually request through —
     * flagged default (top-level OR nested view) and not 4K — else 0,
     * coerced into bounds. The `defaultIndex` local's verbatim math.
     */
    private fun defaultIndex(serverCount: Int, isRequestDefault: (index: Int) -> Boolean): Int =
        if (serverCount == 0) {
            0
        } else {
            ((0 until serverCount).indexOfFirst(isRequestDefault).takeIf { it >= 0 } ?: 0)
                .coerceIn(0, serverCount - 1)
        }

    private fun SeerrRadarrServiceDetail.isRequestDefault(): Boolean =
        (isDefault || server?.isDefault == true) && !is4k

    private fun SeerrSonarrServiceDetail.isRequestDefault(): Boolean =
        (isDefault || server?.isDefault == true) && !is4k

    private fun indexOfDefaultProfile(profiles: List<SeerrServiceProfile>?, defaultId: Int?): Int {
        val index = profiles?.indexOfFirst { it.id == defaultId }?.takeIf { it >= 0 } ?: 0
        return index.coerceAtMost((profiles?.size ?: 1) - 1).coerceAtLeast(0)
    }

    private fun indexOfDefaultRootFolder(rootFolders: List<SeerrServiceRootFolder>?, defaultDir: String?): Int {
        val index = rootFolders?.indexOfFirst { it.path == defaultDir }?.takeIf { it >= 0 } ?: 0
        return index.coerceAtMost((rootFolders?.size ?: 1) - 1).coerceAtLeast(0)
    }
}
