package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.AudioCachePolicyGuard
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.QueuePersistenceHelper
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.data.playback.SleepCountdownClock
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.repository.JellyPlayEventsRepository
import com.raulshma.jellyplay.core.data.repository.JellyPlayPreferencesSyncAdapter
import com.raulshma.jellyplay.core.data.repository.JellyPushRepository
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.first
import com.raulshma.jellyplay.core.data.session.SessionIdentityProvider
import com.raulshma.jellyplay.core.data.sync.OfflineSyncComparator
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.model.EpochMillisSource
import com.raulshma.jellyplay.core.model.SystemTimeSource
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The session / playback / sync / syncplay / worker family of the
 * dataJvmModule split (C4 part 2, batch 3 — see [dataJvmModule] for the
 * construction-owner rules): the session-identity spine, the portable
 * playback helpers, and the offline-sync manager. Binding bodies moved
 * verbatim from the pre-split single-module layout.
 */
internal val dataSessionPlaybackModule: Module = module {
    // ── C4 part 2, batch 3: session / playback / sync / syncplay / worker ──
    // Constructors mirrored verbatim from the moved impls. The types whose
    // ctor deps live in the Android-only legacy core:data remainder
    // (AudioPlaybackManager + the media3 audio graph, owned by
    // androidCoreDataModule there) are deliberately NOT defined here:
    // DefaultAudioQueueFacade only. OfflineSyncManager, AudioLyricsManager and
    // (playback flips) PlaybackSourceResolverImpl all moved off that
    // list as their ctor deps became Koin-resolvable.

    // D3: the seam types moved to :shared:core:model (so core:network below
    // this module can adopt them); this module stays their Koin owner —
    // core:model deliberately carries no Koin module.
    single<TimeSource> { SystemTimeSource() }

    // the commonMain-promoted repository impls (SearchHistory /
    // ItemPlaybackPreference / PlaybackOutbox / MoodPlaylist) take the
    // commonMain EpochMillisSource clock seam — bind it to the SAME
    // SystemTimeSource single above (one framework per clock; the fakes in
    // jvmTest satisfy the seam through the TimeSource supertype).
    single<EpochMillisSource> { get<TimeSource>() }

    single { HomeSession(get(), get(DatastoreQualifiers.applicationScope)) }

    // The identity seam the promoted commonMain graph consumes
    // (SeerrRepositoryImpl's cache keys + SessionCacheRegistry's transition
    // subscription). Binds the SAME HomeSession singleton — android/desktop
    // behavior unchanged.
    single<SessionIdentityProvider> { get<HomeSession>() }

    single {
        SessionCacheRegistry(
            sessionIdentity = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single {
        // The show-missing-episodes preference read feeds the catalogue's
        // online `isMissing` episodes filter (hide is the default; offline
        // snapshots never consult it).
        val libraryStore: LibraryStore = get()
        EpisodeCatalogueImpl(
            libraryApiClient = get(),
            offlineRepository = get(),
            homeSession = get(),
            sessionCacheRegistry = get(),
            showMissingEpisodes = { libraryStore.library.first().showMissingEpisodes },
        )
    }
    single<EpisodeCatalogue> { get<EpisodeCatalogueImpl>() }

    single { DownloadConcurrencyLimiter() }

    single { PlayerLifecycleManager(get()) }

    single { QueuePersistenceHelper(get()) }

    // Playback flips: the sleep-timer countdown (then SleepTimerManager) moved
    // from the legacy :core:data shim — SystemClock.elapsedRealtime became the
    // TimeSource seam above, and the sleep-timer fold later collapsed that
    // manager into the commonMain [SleepCountdown] core (the alias
    // AudioSleepTimerManager interface died with it: every host — video VM,
    // audio VM, both queue managers, the reader — resolves THIS single, whose
    // clock binds the same TimeSource single, so the wall-clock timing model
    // is unchanged). The audio/live player VMs resolve this single through
    // Koin directly since their migrations; legacy core:data's
    // AudioPlaybackManager resolves this single from androidCoreDataModule.
    single {
        SleepCountdown(
            clock = SleepCountdownClock { get<TimeSource>().nowElapsedRealtimeMillis() },
        )
    }

    // Playback flips: AdaptiveBitrateManager moved from the legacy
    // core:data shim — ConnectivityManager became the NetworkMonitor seam
    // (null-network/metered parity documented on the class). Its consumers
    // (feature:details DownloadLifecycleActions, feature:player:video
    // PlayerCastController / PlaybackSession / PlayerSessionManager /
    // VideoPlayerViewModel) resolve this single from Koin directly.
    single { AdaptiveBitrateManager(get(), get(), get()) }

    // V3 livetv conveyor: the mini-player holder moved from the legacy
    // core:data shim (one framework per type — @Singleton/@Inject stripped at
    // the move). Consumers (app FloatingPlayerState, feature:player:video
    // VideoPlayerViewModel, livetv's ChannelsViewModel) resolve this single
    // directly from Koin; ChannelsViewModel resolves it
    // directly from here.
    single { VideoMiniPlayerState() }

    single {
        AudioCachePolicyGuard(
            audioCacheStore = get(),
            networkMonitor = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }

    single { OfflineSyncComparator(get()) }

    // V3 downloads conveyor: OfflineSyncManager flipped from the interim
    // direct-construction DataModule provider to a Koin single (C4 flip
    // pattern) — every ctor dep is now Koin-resolvable: the DAOs via
    // databaseDaosModule, the comparator/PlaybackRepository via this module,
    // OfflineModeManager via the platform data modules, the application scope
    // via DatastoreQualifiers, and — since the downloads conveyor moved the
    // engine — DownloadRepository from this module's own single (feature:
    // details' ResyncActions shares the same instance through Koin).
    // `writer` reuses the DownloadRepository single: the interface extends
    // OfflineDownloadWriter, so no separate definition is needed. The former
    // Hilt→Koin→Hilt edge (interop MediaRepository) died with the
    // MediaRepository cluster flip below — the mediaRepository dep is now
    // this module's own MediaRepositoryImpl single on both platforms, so the
    // graph is pure Koin from OfflineSyncManager down.
    single {
        OfflineSyncManager(
            mediaRepository = get(),
            writer = get<DownloadRepository>(),
            downloadRepository = get(),
            offlineMediaDao = get(),
            syncBaselineDao = get(),
            comparator = get(),
            offlineModeManager = get(),
            playbackRepository = get(),
            appScope = get(DatastoreQualifiers.applicationScope),
            timeSource = get(),
        )
    }

    // ------------------------------------------------------------------
    // jellyfin-plugin-jellyplay companion plugin cluster (ADR 0010):
    // capability probe store, opt-in profile sync engine over the
    // user_prefs DataStore, live events/messages face.
    // ------------------------------------------------------------------
    single { com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore(get(), get()) }

    // The per-feature enable/disable seam over the probe above: probe
    // AVAILABLE AND the user's toggle (an ordinary `pluginFeature.<key>.enabled`
    // pref in the SAME user prefs DataStore the sync adapter below mirrors).
    single {
        com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate(
            statusStore = get(),
            dataStore = get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.userPreferencesDataStore),
            scope = get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.applicationScope),
        )
    }

    // The home fetcher's plugin-row leaf transport (JellyPlayHomeSectionSources,
    // declared in core:network): the wiring twin of NetworkKoinModules'
    // SeerrHomeSectionSourcesImpl, built HERE because only this module sees
    // both the plugin transport (JellyPlayPluginApiClient, networkJvmModule)
    // and the capability probe (the status store above) — the network module
    // resolves this binding cross-module via getOrNull, so graphs without the
    // plugin cluster simply fetch no plugin rows. Declared beside the probe
    // store it gates through; the row policy (TTL, mapping, ordering) stays
    // in the fetcher.
    single<com.raulshma.jellyplay.core.network.library.JellyPlayHomeSectionSources> {
        // getKoin() captured at construction; the client read deferred to the
        // first home fetch. Resolving LibraryApiClient eagerly here would
        // cycle with the client impl (networkJvmModule) whose construction
        // resolves THIS binding via getOrNull.
        val koin = getKoin()
        JellyPlayHomeSectionSourcesImpl(
            apiClient = get(),
            statusStore = get(),
            featureGate = get(),
            libraryClient = { koin.get() },
        )
    }

    single {
        val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> =
            get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.userPreferencesDataStore)
        JellyPlayPreferencesSyncAdapter(
            dataStore = dataStore,
            namespace = "prefs",
            // Per-device namespaces never sync (see docs/jellyplay-plugin.md).
            // The plugin feature toggles' `pluginFeature.` prefix is
            // deliberately NOT excluded — those toggles are ordinary synced
            // prefs (JellyPlayFeatureGate); only jpsync.* reservations and
            // these per-device prefixes may ever land here.
            excludedPrefixes = listOf("dream", "screensaver"),
        )
    }

    single {
        val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> =
            get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.userPreferencesDataStore)
        ProfileSyncRepository(
            apiClient = get(),
            statusStore = get(),
            sessionCacheRegistry = get(),
            adapters = listOf(JellyPlayPreferencesSyncAdapter(dataStore)),
            deviceProfile = detectDeviceProfile(),
            deviceIdProvider = jpsyncDeviceIdProvider(dataStore),
            nowMillis = { System.currentTimeMillis() },
            persistenceScope = get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.applicationScope),
            loadEnabled = {
                dataStore.data.first()[booleanPreferencesKey("jpsync.device.sync_enabled")] ?: false
            },
            saveEnabled = { value ->
                dataStore.edit { it[booleanPreferencesKey("jpsync.device.sync_enabled")] = value }
            },
        )
    }

    single {
        JellyPlayEventsRepository(
            apiClient = get(),
            statusStore = get(),
            scope = get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.applicationScope),
            deviceName = detectDeviceName(),
            devicePlatform = detectDeviceProfile(),
            appVersion = appVersionString(),
            deviceIdProvider = jpsyncDeviceIdProvider(
                get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.userPreferencesDataStore),
            ),
        )
    }

    // The push face (the plugin's push wave): the SAME device identity as the
    // events repository above (jpsync.device.id + name/platform/version), the
    // distributor seam resolved getOrNull — Android's notification module
    // registers the UnifiedPush connector impl, desktop registers nothing and
    // the machine parks on NoDistributor (JellyPushDistributor's KDoc).
    single {
        val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> =
            get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.userPreferencesDataStore)
        JellyPushRepository(
            apiClient = get(),
            statusStore = get(),
            featureGate = get(),
            dataStore = dataStore,
            scope = get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.applicationScope),
            deviceName = detectDeviceName(),
            devicePlatform = detectDeviceProfile(),
            appVersion = appVersionString(),
            deviceIdProvider = jpsyncDeviceIdProvider(dataStore),
            distributor = getOrNull(),
        )
    }

    // The events face's lifecycle owner: starts the SSE stream on the auth-true
    // edge, stops it on sign-out, maps events onto the user surfaces. Both
    // surface seams are nullable getOrNull resolutions bridged app-side (the
    // core:data-cannot-see-core:ui rule, DownloadOutcomeMessenger idiom):
    // JellyPlayNewMediaNotifier — the Android tray path, registered in the
    // platform notification module (desktop registers nothing → log-only);
    // JellyPlayBroadcastMessenger — bridged to the shared UserMessageBus by
    // both shells' interop modules. Self-starting on the shared application
    // scope at Koin creation, the same singleton-collector idiom as
    // HomeSession / SessionCacheRegistry — one wiring covers the Android and
    // desktop shells.
    single(createdAtStart = true) {
        com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionController(
            authRepository = get(),
            eventsRepository = get(),
            statusStore = get(),
            featureGate = get(),
            broadcastMessenger = getOrNull(),
            newMediaNotifier = getOrNull(),
            pushRepository = get(),
            scope = get(com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers.applicationScope),
        ).apply { start() }
    }
}

/** Desktop shells sync under the "desktop" profile, Android under "phone" (TV shells override later). */
private fun detectDeviceProfile(): String {
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    return if (os.contains("windows") || os.contains("linux") || os.contains("mac")) "desktop" else "phone"
}

/**
 * The shared per-device identity read-or-create — the reserved
 * `jpsync.device.id` pref (`jpsync.device.*` keys never sync, adapter rule).
 * ONE home for the id all three plugin seams carry: the profile sync, the
 * events face and the push face attach to the SAME device record.
 */
private fun jpsyncDeviceIdProvider(
    dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
): suspend () -> String = {
    val key = stringPreferencesKey("jpsync.device.id")
    dataStore.data.first()[key]
        ?: UUID.randomUUID().toString().also { fresh -> dataStore.edit { it[key] = fresh } }
}

private fun detectDeviceName(): String =
    System.getProperty("os.name")?.let { "$it device" } ?: "JellyPlay device"

/** App version from the JVM manifest (best-effort; empty string when unpackaged). */
private fun appVersionString(): String =
    JellyPlayEventsRepository::class.java.`package`?.implementationVersion ?: "dev"

/**
 * The session-aware transport satisfying the home fetcher's
 * [com.raulshma.jellyplay.core.network.library.JellyPlayHomeSectionSources]
 * for the PLUGIN_ROW home rows: the row reads forward verbatim onto the
 * plugin api client (ADR 0010's one-family rule — every `jellyplay/` route
 * rides `JellyPlayPluginApiClient`), the item resolution forwards onto the
 * library client's batched ids read, and the two capability gates read ONLY
 * the probe store's registry — the ONE gating mechanism (ADR 0010 §6).
 *
 * Gate semantics: a store still UNKNOWN (never probed this session) runs the
 * once-per-session capabilities probe first — home is the first feature
 * surface a plugin user meets, and no other surface probed on its behalf yet
 * — then reads the registry. UNAVAILABLE is trusted for the session (the
 * store's own failure semantics; callers re-probe on the next explicit
 * refresh), so a plugin-absent server pays one probe, never one per home
 * fetch. Every gate answer is `AVAILABLE && <row feature on>`, byte-for-byte
 * the docs/jellyplay-plugin.md gating rule.
 */
private class JellyPlayHomeSectionSourcesImpl(
    private val apiClient: com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient,
    private val statusStore: com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore,
    /**
     * The per-feature gate (probe AND the user's toggle) — the row gates read
     * the fresh one-shot arm ([JellyPlayFeatureGate.isAvailableNow]) because
     * they run right after the once-per-session synchronous probe refresh,
     * where the reactive flow's value could still be one dispatch stale.
     */
    private val featureGate: com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate,
    /**
     * Deferred [com.raulshma.jellyplay.core.network.api.LibraryApiClient]
     * read — see the binding's comment: resolving the client eagerly here
     * would cycle with the client impl that hosts this adapter.
     */
    private val libraryClient: () -> com.raulshma.jellyplay.core.network.api.LibraryApiClient,
) : com.raulshma.jellyplay.core.network.library.JellyPlayHomeSectionSources {

    override suspend fun seasonalRowsEnabled(): Boolean =
        rowsEnabled(com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures.SeasonalRows)

    override suspend fun customRowsEnabled(): Boolean =
        rowsEnabled(com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures.CustomRows)

    override suspend fun getSeasonalRow(keyword: String?): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowResult?> =
        apiClient.getSeasonalRow(keyword)

    override suspend fun getCustomRowCatalog(): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog?> =
        apiClient.getCustomRowCatalog()

    override suspend fun getCustomRow(title: String): Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowResult?> =
        apiClient.getCustomRow(title)

    override suspend fun getItemsByIds(ids: List<String>): Result<List<com.raulshma.jellyplay.core.model.MediaItem>> =
        libraryClient().getItemsByIds(ids)

    /**
     * Probe-when-UNKNOWN, then the ONE gate seam — probe AVAILABLE AND the
     * user's per-feature toggle (`pluginFeature.<key>.enabled`), so a switched
     * off `seasonal-rows`/`custom-rows` arm resolves locally with no port
     * calls and the next home fetch picks a toggle flip up.
     */
    private suspend fun rowsEnabled(feature: String): Boolean {
        if (statusStore.status.value == com.raulshma.jellyplay.core.model.JellyPlayPluginStatus.UNKNOWN) {
            statusStore.refresh()
        }
        return featureGate.isAvailableNow(feature)
    }
}
