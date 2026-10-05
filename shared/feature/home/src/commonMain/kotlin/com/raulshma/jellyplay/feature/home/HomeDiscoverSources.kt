package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.HomeFreshness
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.seerr.DiscoverSectionType
import com.raulshma.jellyplay.core.model.seerr.SeerrDiscoverParams
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/**
 * Deep module extracted from [HomeRefresher]: the WHAT of the discover-shaped
 * side fetches — the legacy Seerr discover-grid fan-out (up to five
 * [SeerrRepository] round-trips behind the discover TTL gate) and the direct
 * *arr "Recently Grabbed" calendar fetch. Previously both bodies sat inline on
 * the refresher, so every new discover/arr-shaped side effect widened the
 * refresher (and both construction surfaces) with its repositories; now the
 * refresher's constructor takes THIS collaborator and a new side-effect
 * dependency lands here alone.
 *
 * Division of labour with [HomeRefresher] (the DiscoverRowsCoordinator split,
 * repeated): the refresher keeps WHEN — the concurrent fetch-group schedule in
 * [HomeRefresher.fetchOnce] (both calls ride its `async` groups and are
 * awaited at the same points), the tracked standalone [RefreshTrigger.DiscoverEnabled]
 * job, and the cache invalidation in the manual-refresh preamble
 * ([invalidate]); this class owns WHAT — the fan-out shape, the per-pref Seerr
 * calls, the *arr calendar window, and the state fields they write
 * (`discoverSections` / `recentlyGrabbed`).
 *
 * Writes go through the refresher's SINGLE [HomeRefreshState] store (the
 * [state] reference handed in at construction is the refresher's own
 * `MutableStateFlow`) — extracted machinery, not a second writer; the VM keeps
 * folding one state object.
 */
internal class HomeDiscoverSources(
    private val clock: HomeClock,
    private val seerrRepository: SeerrRepository,
    private val arrRepository: ArrRepository,
    /** The discover fan-out's LAN fast-skip (Local network → no Seerr round-trips). */
    private val offlineModeManager: OfflineModeManager,
    /** The refresher's own state store — handed in so the side-fetch writes land in the single UiState fold. */
    private val state: MutableStateFlow<HomeRefreshState>,
) {

    /**
     * Discover-sections TTL gate (see HomeFreshness.DISCOVER_TTL_MS /
     * [fetchDiscoverSections]). The CUSTOM Seerr discover rows need no gate
     * here: they ride the home-sections fetch itself (network-layer TTL +
     * last-known-good — see HomeSectionsFetcher.fetchSeerrDiscoverRows); this
     * gate covers the legacy fixed grid.
     */
    private val discoverCache = TtlCacheGate(HomeFreshness.DISCOVER_TTL_MS)

    /**
     * Resets the discover-sections TTL so the next [fetchDiscoverSections]
     * actually hits the network. Called by the refresher on user-initiated
     * refresh — the custom Seerr rows ride the forced home-sections fetch
     * instead (the network layer's force acts as their invalidation), and the
     * legacy fixed grid is what this gate still covers.
     */
    fun invalidate() {
        discoverCache.invalidate()
    }

    /**
     * Refreshes the *arr calendar window and pushes the merged list into
     * [HomeRefreshState.recentlyGrabbed] as [SeerrSearchItem]s (reusing the
     * TMDB card model so no new card UI is needed). Window is now → +30
     * days so "coming soon" + freshly-grabbed items both surface. Failures
     * degrade to empty; the *arr repository already swallows per-server
     * errors.
     */
    suspend fun fetchRecentlyGrabbed() {
        val now = clock.today()
        val end = now.plus(30, DateTimeUnit.DAY)
        // ArrRepository takes kotlinx.datetime.LocalDate — the refresher's
        // HomeClock seam now speaks kotlinx LocalDate natively.
        arrRepository.refreshCalendar(now, end)
        val items = arrRepository.calendar(now, end).first()
        state.update { it.copy(recentlyGrabbed = items.map { it.toSeerrSearchItem() }) }
    }

    suspend fun fetchDiscoverSections(prefs: SeerrPreferences) {
        if (!prefs.enabled || !prefs.discoverEnabled) return
        if (offlineModeManager.networkStatus.value == NetworkStatus.Local) return
        // Trending/popular change slowly; cache discover results for
        // HomeFreshness.DISCOVER_TTL_MS so "just sitting on Home" doesn't fan
        // out up to 5 Seerr round-trips per minute (periodic refresh + per
        // pref change). A user-initiated refresh (swipe-to-refresh) bypasses
        // this gate via [invalidate].
        val now = clock.nowEpochMillis()
        if (!discoverCache.shouldFetch(now)) return

        val today = clock.today().toString()

        // coroutineScope, not the outer VM scope: the Seerr fan-out must be a
        // child of the calling refresh job (or the tracked fetchDiscover job),
        // so [stop] / the VM's going-online timeout cancels in-flight requests
        // — launching on the VM scope let them escape cancellation and run to
        // completion abandoned.
        val newSections = coroutineScope {
            val deferredResults = mutableListOf<Pair<DiscoverSectionType, Deferred<Result<List<SeerrSearchItem>>>>>()

            if (prefs.discoverTrending) {
                deferredResults.add(DiscoverSectionType.TRENDING to async { seerrRepository.getTrending() })
            }
            if (prefs.discoverPopularMovies) {
                deferredResults.add(DiscoverSectionType.POPULAR_MOVIES to async { seerrRepository.getDiscoverMovies() })
            }
            if (prefs.discoverPopularTv) {
                deferredResults.add(DiscoverSectionType.POPULAR_TV to async { seerrRepository.getDiscoverTv() })
            }
            if (prefs.discoverUpcomingMovies) {
                deferredResults.add(
                    DiscoverSectionType.UPCOMING_MOVIES to
                        async { seerrRepository.getDiscoverMovies(params = SeerrDiscoverParams(releaseDateGte = today)) },
                )
            }
            if (prefs.discoverUpcomingTv) {
                deferredResults.add(
                    DiscoverSectionType.UPCOMING_TV to
                        async { seerrRepository.getDiscoverTv(params = SeerrDiscoverParams(releaseDateGte = today)) },
                )
            }

            val sections = mutableMapOf<DiscoverSectionType, List<SeerrSearchItem>>()
            for ((type, deferred) in deferredResults) {
                deferred.await().onSuccess { items ->
                    sections[type] = items
                }
            }
            sections
        }

        discoverCache.markFetched(clock.nowEpochMillis())
        state.update { it.copy(discoverSections = newSections) }
    }
}
