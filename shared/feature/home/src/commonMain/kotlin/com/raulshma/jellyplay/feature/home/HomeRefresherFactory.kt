package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.HomeSectionPrefs
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import kotlinx.coroutines.CoroutineScope

/**
 * Construction seam for [HomeRefresher]: owns the refresher's pure-DI
 * collaborators so they stop surfacing on [HomeViewModel]'s constructor —
 * the VM's interface was widening by one parameter per new refresher
 * dependency, none of which the VM itself used. The factory is the single
 * place a new refresher collaborator lands; [create] takes only the inputs
 * that are genuinely VM-owned runtime state (its scope, its preference
 * mirrors as read-only providers, and the sync holder's drain gate).
 *
 * This delegates — it adds no behavioural seam: [HomeRefresherTest] keeps
 * constructing the refresher directly, and the VM passes the same values it
 * passed before.
 *
 * [offlineModeManager] sits on [create] rather than the factory because
 * the VM itself uses it (ToggleOfflineMode) — it is a DI bean the VM
 * owns, not a pure refresher collaborator.
 */
internal class HomeRefresherFactory constructor(
    private val timeSource: TimeSource,
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val arrRepository: ArrRepository,
    private val orderHomeSections: OrderHomeSectionsUseCase,
    private val widgetDataStore: WidgetDataStore,
    private val continueWatchingBroadcaster: ContinueWatchingBroadcaster,
    private val tvWatchNextScheduler: TvWatchNextScheduler,
    private val librarySyncHook: LibrarySyncHook,
) {
    fun create(
        scope: CoroutineScope,
        offlineModeManager: OfflineModeManager,
        awaitOutboxDrained: suspend () -> Unit,
        sectionPrefsProvider: () -> HomeSectionPrefs,
        seerrPreferencesProvider: () -> SeerrPreferences,
        discoverEnabledProvider: () -> Boolean,
        directArrEnabledProvider: () -> Boolean,
        androidTvWatchNextEnabledProvider: () -> Boolean,
    ): HomeRefresher = HomeRefresher(
        scope = scope,
        timeSource = timeSource,
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository,
        arrRepository = arrRepository,
        orderHomeSections = orderHomeSections,
        widgetDataStore = widgetDataStore,
        continueWatchingBroadcaster = continueWatchingBroadcaster,
        tvWatchNextScheduler = tvWatchNextScheduler,
        librarySyncHook = librarySyncHook,
        offlineModeManager = offlineModeManager,
        awaitOutboxDrained = awaitOutboxDrained,
        sectionPrefsProvider = sectionPrefsProvider,
        seerrPreferencesProvider = seerrPreferencesProvider,
        discoverEnabledProvider = discoverEnabledProvider,
        directArrEnabledProvider = directArrEnabledProvider,
        androidTvWatchNextEnabledProvider = androidTvWatchNextEnabledProvider,
    )
}
