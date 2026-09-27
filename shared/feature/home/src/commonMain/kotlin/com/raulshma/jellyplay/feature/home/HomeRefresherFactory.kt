package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Construction seam for [HomeRefresher]: owns the refresher's pure-DI
 * collaborators so they stop surfacing on [HomeViewModel]'s constructor —
 * the VM's interface was widening by one parameter per new refresher
 * dependency, none of which the VM itself used. The factory is the single
 * place a new refresher collaborator lands; [create] takes only the inputs
 * that are genuinely VM-owned runtime state (its scope, its preference
 * mirrors bundled as the read-only [HomeFetchInputs] providers, and the sync
 * holder's drain gate).
 *
 * This delegates — it adds no behavioural seam: [HomeRefresherTest] keeps
 * constructing the refresher directly, and the VM passes the same values it
 * passed before.
 *
 * The discover/arr side-fetch collaborator ([HomeDiscoverSources]) is
 * assembled here in [create] from the repositories this factory already
 * owns — a future discover/arr-shaped side-effect dependency widens that
 * class and this factory, not the refresher's constructor.
 *
 * [offlineModeManager] sits on [create] rather than the factory because
 * the VM itself uses it (ToggleOfflineMode) — it is a DI bean the VM
 * owns, not a pure refresher collaborator.
 */
internal class HomeRefresherFactory constructor(
    private val clock: HomeClock,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val arrRepository: ArrRepository,
    private val orderHomeSections: OrderHomeSectionsUseCase,
    private val widgetDataStore: WidgetDataStore,
    private val continueWatchingBroadcaster: ContinueWatchingBroadcaster,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    private val librarySyncHook: LibrarySyncHook,
    /** Local TOC cache — the Continue Reading row's page-count source. */
    private val bookTocCacheRepository: BookTocCacheRepository,
) {
    fun create(
        scope: CoroutineScope,
        offlineModeManager: OfflineModeManager,
        awaitOutboxDrained: suspend () -> Boolean,
        fetchInputs: HomeFetchInputs,
    ): HomeRefresher {
        // ONE state store for the refresher and its side-fetch collaborator —
        // both write into the same single HomeRefreshState fold (the store is
        // the seam that lets the discover/arr bodies live off the refresher
        // without becoming a second state owner).
        val state = MutableStateFlow(HomeRefreshState())
        return HomeRefresher(
            scope = scope,
            clock = clock,
            mediaRepository = mediaRepository,
            orderHomeSections = orderHomeSections,
            widgetDataStore = widgetDataStore,
            continueWatchingBroadcaster = continueWatchingBroadcaster,
            tvWatchNextScheduler = tvWatchNextScheduler,
            librarySyncHook = librarySyncHook,
            bookTocCacheRepository = bookTocCacheRepository,
            offlineModeManager = offlineModeManager,
            stateStore = state,
            discoverSources = HomeDiscoverSources(
                clock = clock,
                seerrRepository = seerrRepository,
                arrRepository = arrRepository,
                offlineModeManager = offlineModeManager,
                state = state,
            ),
            awaitOutboxDrained = awaitOutboxDrained,
            fetchInputs = fetchInputs,
        )
    }
}
