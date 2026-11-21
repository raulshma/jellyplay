package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalFeatureGate
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.network.arr.SonarrApiClient
import kotlinx.coroutines.flow.first

/**
 * The arr auto-arm stage of the auto-download pass (gh-225 follow-up "arr
 * auto-arm"): one pass-level step that arms every series the user's Sonarr
 * instances monitor AND the Jellyfin library actually contains, so the pass's
 * own universe union picks them up the same run. Runs inside
 * [com.raulshma.jellyplay.core.data.worker.AutoDownloadCheck.checkOnce] after
 * the retention sweep and before the universe derivation — a series armed here
 * joins the SAME pass (its lookahead anchor builds from the catalogue snapshot
 * this pass loads).
 *
 * Gates (in order, cheapest first — any closed gate is a silent no-op):
 *  1. the `autoArmFromArr` preference (read off the slice the pass already
 *     loaded — one DataStore read per pass, no second one here);
 *  2. the Direct *arr Integration experimental flag
 *     ([ExperimentalFeatureGate.directArrEnabled] — the flag gates the whole
 *     feature);
 *  3. at least one Sonarr server configured ([ArrRepository.resolveServers]).
 * There is no separate offline gate: the pass runs online-only and arr/
 * Jellyfin reads fail offline exactly as they do on every other arr surface —
 * the failure arms below turn that into "stage skipped".
 *
 * Choreography:
 *  - `GET /api/v3/series` on every configured Sonarr server
 *    ([SonarrApiClient.getSeriesList]), filtered to `monitored` rows (the
 *    `tvdbId == 0` wire-omission sentinel rows are skipped — they cannot join
 *    the Jellyfin match);
 *  - ONE paged Jellyfin read building `tvdbId → (seriesId, title)` straight
 *    from the enumerated rows: the series universe pages in through
 *    [MediaInfoApiClient.getItemsWithUserData] (the WatchHistoryRepository
 *    paging precedent) and that read projects `ProviderIds`
 *    (MediaItem.providerIds), so no per-series detail fan-out is needed — the
 *    former getMediaDetail over every series id cold-read the whole library's
 *    detail cache every pass;
 *  - the in-memory join picks the monitored tvdb ids with a Jellyfin match and
 *    NO existing policy row; each becomes
 *    [SeriesDownloadPolicyStore.armSeries]`(jellyfinSeriesId, jellyfinTitle ?:
 *    sonarrTitle)`.
 *
 * Invariants:
 *  - **Add-only.** Nothing here deletes, un-arms, or rewrites a row:
 *    [SeriesDownloadPolicyStore.armSeries] is insert-if-absent, and the
 *    candidate filter skips every series that already carries a row — an
 *    explicit NEVER tombstone (the manual disarm), an override, or an earlier
 *    arm is never clobbered or resurrected.
 *  - **Fail-closed per stage.** Every failure this stage can hit is a
 *    whole-stage failure (server resolution, any Sonarr series list, a
 *    Jellyfin enumeration page): it aborts the stage — the caller's
 *    runCatching turns it into "skip the stage, the rest of the pass
 *    continues". There are no per-series reads anymore (the match index is
 *    one paged enumeration), so the former per-series swallow arm is gone:
 *    a series either appears on the enumeration rows or it doesn't.
 *  - **No budget consumption.** Arming writes policy rows, never transfers —
 *    the pass's INSTANT `maxPerPass` budget is untouched (the enqueues happen
 *    in the pass's own loop over the widened universe).
 */
class ArrAutoArmSync(
    /** The Direct *arr Integration gate (`directArrEnabled`, hot + eager). */
    private val featureGate: ExperimentalFeatureGate,
    /** Server resolution (manual + Seerr-discovered Sonarr instances). */
    private val arrRepository: ArrRepository,
    /** The per-server `GET /api/v3/series` read. */
    private val sonarrApiClient: SonarrApiClient,
    /** The arm-row store (the check's own policy surface). */
    private val policyStore: SeriesDownloadPolicyStore,
    /** The active-user identity for the series enumeration read. */
    private val authApiClient: AuthApiClient,
    /** The paged series-universe read (`getItemsWithUserData`, ProviderIds projected). */
    private val mediaInfoApiClient: MediaInfoApiClient,
) {

    /**
     * Runs one arm-sync pass. [prefs] is the slice the calling check already
     * loaded — the stage reads `autoArmFromArr` from it instead of re-reading
     * the DataStore, so the gate and the pass share one snapshot. May throw
     * (whole-stage failures propagate; the caller folds them into "skip the
     * stage") — cancellation always propagates via the caller's
     * runCatchingRethrowingCancellation wrap.
     */
    suspend fun sync(prefs: DownloadsSlice) {
        // Gate 1 — the opt-in pref. The overwhelmingly common exit: one field
        // check on the already-loaded slice, no Koin resolution beyond the ctor.
        if (!prefs.autoArmFromArr) return
        // Gate 2 — the experimental flag gates the whole Direct *arr feature.
        if (!featureGate.directArrEnabled.value) return

        // Gate 3 — server resolution. A resolution failure is a stage failure
        // (the discovery stack may be mid-retry); an empty Sonarr list is a
        // legitimate no-op (the feature is on but nothing points at Sonarr).
        val summary = arrRepository.resolveServers().getOrElse {
            Log.w(TAG, "arr auto-arm skipped: server resolution failed", it)
            return
        }
        val sonarrServers = summary.sonarrServers
        if (sonarrServers.isEmpty()) return

        val monitoredByTvdb = collectMonitoredByTvdb(sonarrServers)
        if (monitoredByTvdb.isEmpty()) return

        // The active-user identity for the enumeration read (no user →
        // nothing to enumerate against).
        val user = authApiClient.currentUser.first() ?: return
        val jellyfinByTvdb = buildJellyfinIndexByTvdb(userId = user.id)
        if (jellyfinByTvdb.isEmpty()) return

        // The join: monitored tvdb ids that Jellyfin also has → candidate
        // (Jellyfin series id → tvdbId) pairs (deduped — two Sonarr rows may
        // share a tvdb id, and a series row carries one id).
        val candidateTvdbBySeriesId = HashMap<String, Int>()
        monitoredByTvdb.keys.forEach { tvdbId ->
            jellyfinByTvdb[tvdbId]?.let { candidateTvdbBySeriesId.putIfAbsent(it.seriesId, tvdbId) }
        }
        if (candidateTvdbBySeriesId.isEmpty()) return

        // Add-only filter: every id that already carries a policy row — an
        // explicit override, a NEVER tombstone (manual disarm wins; the stage
        // must never re-arm it), or an earlier arm — is skipped. Missing ids
        // are the un-armed series this stage exists to arm.
        val existingRows = policyStore.seriesPolicies(candidateTvdbBySeriesId.keys.toList())

        candidateTvdbBySeriesId.forEach { (seriesId, tvdbId) ->
            if (seriesId in existingRows) return@forEach
            // Name precedence: the Jellyfin item's own title, the Sonarr
            // title as fallback (a blank Jellyfin name carries no display
            // value; the armed list needs one).
            val jellyfinTitle = jellyfinByTvdb.getValue(tvdbId).title
            val sonarrTitle = monitoredByTvdb.getValue(tvdbId)
            policyStore.armSeries(
                seriesId = seriesId,
                seriesName = jellyfinTitle.ifBlank { sonarrTitle },
            )
        }
    }

    /**
     * Fan out over ALL configured Sonarr servers: monitored rows only,
     * tvdb-id join key (0 = wire-omitted sentinel → cannot match, skip).
     * A failure on ANY server aborts the stage (the empty return reads as
     * the stage skip in [sync]): arming from the healthy half of a
     * partially-down fleet would make the armed set depend on which server
     * happened to answer first, and the next pass re-arms the rest anyway
     * (add-only). First row per tvdb id wins.
     */
    private suspend fun collectMonitoredByTvdb(
        sonarrServers: List<ArrServerConfig>,
    ): Map<Int, String> {
        val monitoredByTvdb = HashMap<Int, String>()
        for (server in sonarrServers) {
            val seriesList = sonarrApiClient.getSeriesList(server).getOrElse {
                Log.w(TAG, "arr auto-arm skipped: ${server.name} series list failed", it)
                return emptyMap()
            }
            seriesList.forEach { series ->
                if (series.monitored && series.tvdbId != 0 && series.tvdbId !in monitoredByTvdb) {
                    monitoredByTvdb[series.tvdbId] = series.title
                }
            }
        }
        return monitoredByTvdb
    }

    /**
     * ONE paged Jellyfin read building tvdbId → (seriesId, title) straight
     * off the enumeration rows: the read projects ProviderIds, so the match
     * index needs no per-series detail fan-out. An enumeration-page failure
     * aborts the stage (getOrThrow propagates to the caller's runCatching
     * through [sync]).
     */
    private suspend fun buildJellyfinIndexByTvdb(
        userId: String,
    ): Map<Int, JellyfinSeriesMatch> {
        val jellyfinByTvdb = HashMap<Int, JellyfinSeriesMatch>()
        var startIndex = 0
        while (true) {
            val (total, items) = mediaInfoApiClient.getItemsWithUserData(
                userId = userId,
                includeItemTypes = listOf(SERIES_ITEM_TYPE),
                sortBy = "SortName",
                sortOrder = "Ascending",
                startIndex = startIndex,
                limit = PAGE_SIZE,
            ).getOrThrow()
            if (items.isEmpty()) break
            items.forEach { item ->
                // Rows without a projected (or parseable) tvdb id cannot
                // match, skip; the tvdbId != 0 guard mirrors the Sonarr-side
                // sentinel. First row per tvdb id wins.
                val tvdbId = item.providerIds?.get("tvdb")?.toIntOrNull() ?: return@forEach
                if (tvdbId != 0 && tvdbId !in jellyfinByTvdb) {
                    jellyfinByTvdb[tvdbId] = JellyfinSeriesMatch(
                        seriesId = item.id,
                        title = item.name,
                    )
                }
            }
            startIndex += items.size
            if (items.size < PAGE_SIZE || startIndex >= total) break
        }
        return jellyfinByTvdb
    }

    /** One Jellyfin match row of the tvdb index. */
    private data class JellyfinSeriesMatch(
        val seriesId: String,
        val title: String,
    )

    companion object {
        private const val TAG = "ArrAutoArmSync"

        /** Jellyfin `includeItemTypes` token for series rows. */
        private const val SERIES_ITEM_TYPE = "Series"

        /** The series-universe page size (WatchHistoryRepository's batch). */
        private const val PAGE_SIZE = 200
    }
}
