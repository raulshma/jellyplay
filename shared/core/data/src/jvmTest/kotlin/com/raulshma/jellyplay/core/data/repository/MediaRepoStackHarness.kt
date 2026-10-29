package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.session.SessionCacheRegistry
import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.SystemTimeSource
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.network.api.AuthApiClient
import com.raulshma.jellyplay.core.network.api.CollectionApiClient
import com.raulshma.jellyplay.core.network.api.ImageUrlApiClient
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.PlaylistApiClient
import com.raulshma.jellyplay.core.network.library.HomeSectionsCachePort
import com.raulshma.jellyplay.core.network.realtime.UserDataRealtimeChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The stack handle [mediaRepoStackFor] returns: the built repository plus the
 * collaborators a suite drives directly. The network family mocks are NOT
 * re-exposed — a suite stubs through the mocks IT declared and passed in,
 * so its expectations stay suite-owned.
 */
internal class MediaRepoStack(
    val repository: MediaRepositoryImpl,
    /**
     * The facade-split twin, built over the SAME [MediaRepositoryInternals]
     * single the repository takes — a playlist edit made through it drops a
     * detail cached through [repository] (the cross-surface invalidation the
     * facade split preserved).
     */
    val playlistRepository: PlaylistRepositoryImpl,
    /**
     * The real catalogue single the repo's invalidation funnel drops into —
     * the same production surface the funnel invalidates, so a
     * mutation → snapshot-drop is observed end-to-end (through the suite's
     * library mock) instead of against a catalogue double.
     */
    val episodeCatalogue: EpisodeCatalogueImpl,
    /**
     * The announcer leaf the build wires (the late-binding arming handle is
     * harness-internal); suites announce through it directly.
     */
    val announcer: UserDataAnnouncerImpl,
)

/**
 * Shared construction harness for the media-repo stack (the [announcerFor]
 * spirit, one level up): owns ONCE the ~40-line construction ritual five
 * suites used to hand-build ([MediaRepositoryImplTest],
 * [MediaRepositoryDetailCacheGroupTest], [MediaRepositoryCacheInvalidationTest],
 * [MediaRepositoryHomeSectionsCacheTest],
 * [MediaRepositoryHomeSectionsSwrPersistTest]) — family mocks per
 * [MediaRepositoryImpl] ctor seam, real identity machinery, real caches. The
 * invariants that make the wiring observable are documented here, not
 * re-commented per suite:
 *
 *  - ONE relaxed mock per family seam (auth / library / collection /
 *    playlist): the ctor's exact seams, matching the DI graph's
 *    one-impl-many-seams shape. The session flow rides the auth mock —
 *    [sessionFlow] is stubbed onto it here, so a suite never stubs
 *    `session` itself.
 *  - REAL [HomeSession] + [SessionCacheRegistry] over [sessionFlow]. The
 *    default is permanently null (logged out), so suites that never switch
 *    identity key everything under `CacheIdentity.UNKNOWN` — the expected
 *    key surface. Identity-switch suites pass their own flow and drive it;
 *    pick [scopeDispatcher] = Unconfined to make the emission → transition →
 *    cache-drop chain synchronous on the setter's stack (Default leaves the
 *    observers a real-dispatcher settle window).
 *  - REAL [EpisodeCatalogueImpl] transplant (see [MediaRepoStack]) and REAL
 *    [HomeSectionsSnapshotStore] over [homeSectionCacheDao] (default: a
 *    relaxed DAO the store's reads/writes never observe).
 *  - ONE shared [MediaRepositoryInternals] across the media + playlist
 *    facade pair (the facade split's cross-surface invalidation shape).
 *  - [announcerFor]'s late-binding arming: the announcer is constructed
 *    before the repository it arms and reads the built instance at announce
 *    time.
 *
 * Suites keep their own stubs/expectations (declared mocks are passed
 * through; undeclared knobs take inert defaults) — the harness owns wiring,
 * never behavior. [HomeFeedRealStackContractTest] deliberately does NOT use
 * this harness: it is the real-stack executable spec (ADR-0008) and
 * hand-builds the cross-layer shell.
 */
internal fun mediaRepoStackFor(
    authClient: AuthApiClient = mockk(relaxed = true),
    libraryApiClient: LibraryApiClient = mockk(relaxed = true),
    collectionApiClient: CollectionApiClient = mockk(relaxed = true),
    imageUrlApiClient: ImageUrlApiClient = mockk(relaxed = true),
    playlistApiClient: PlaylistApiClient = mockk(relaxed = true),
    playedStateSync: PlayedStateSync = mockk(relaxed = true),
    offlineRepository: OfflineRepository = mockk(relaxed = true),
    sessionFlow: MutableStateFlow<ActiveSession?> = MutableStateFlow(null),
    /**
     * Both identity scopes hang off this dispatcher: Default mirrors the
     * production @ApplicationScope discipline (observers settle across a
     * dispatch — suites wait or [kotlinx.coroutines.delay]); Unconfined runs
     * the whole identity chain on the setter's stack (no settle window).
     */
    scopeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    timeSource: TimeSource = SystemTimeSource(),
    homeSectionCacheDao: HomeSectionCacheDao = mockk(relaxed = true),
    homeSectionsCachePort: HomeSectionsCachePort = mockk(relaxed = true),
    userDataRealtimeChannel: UserDataRealtimeChannel = mockk(relaxed = true),
): MediaRepoStack {
    /** Late-binding handle the announcer's arming lambda reads (set at build time). */
    var built: MediaRepositoryImpl? = null

    every { authClient.session } returns sessionFlow
    val homeSession = HomeSession(
        authClient,
        CoroutineScope(SupervisorJob() + scopeDispatcher),
    )
    val sessionCacheRegistry = SessionCacheRegistry(
        homeSession,
        CoroutineScope(SupervisorJob() + scopeDispatcher),
    )
    val episodeCatalogue = EpisodeCatalogueImpl(
        libraryApiClient,
        offlineRepository,
        homeSession,
        sessionCacheRegistry,
    )
    val internals = MediaRepositoryInternals(libraryApiClient, homeSession, sessionCacheRegistry)
    val announcer = announcerFor(homeSession) { built }
    // The persisted home pipeline — the same store single the Koin graph
    // wires, over the suite's (usually inert) DAO. One instance shared by
    // the ledger (its identity-action SWR clear + the flight-body persist)
    // and the repository's cold-open reads.
    val homeSnapshotStore = HomeSectionsSnapshotStore(
        homeSectionCacheDao,
        homeSession,
        timeSource,
    )
    // The freshness ledger — the same ctor-injected collaborator the Koin
    // graph wires, built over the harness's real machinery so the chassis
    // (spec registry, stale ladder, write-generation funnel, eviction
    // ladders) is the production shape.
    val ledger = MediaFreshnessLedger(
        homeSession = homeSession,
        sessionCacheRegistry = sessionCacheRegistry,
        homeSnapshotStore = homeSnapshotStore,
        episodeCatalogue = episodeCatalogue,
        internals = internals,
        homeSectionsCachePort = homeSectionsCachePort,
        timeSource = timeSource,
    )
    val repository = MediaRepositoryImpl(
        libraryApiClient = libraryApiClient,
        collectionApiClient = collectionApiClient,
        // The image-URL family seam (the photo-folder cover probe's read).
        imageUrlApiClient = imageUrlApiClient,
        // The home cache-maintenance port (the write/roll paths' verbs).
        homeSectionsCachePort = homeSectionsCachePort,
        homeSnapshotStore = homeSnapshotStore,
        playedStateSync = playedStateSync,
        userDataRealtimeChannel = userDataRealtimeChannel,
        userDataAnnouncer = announcer,
        ledger = ledger,
        internals = internals,
    ).also { built = it }
    val playlistRepository = PlaylistRepositoryImpl(playlistApiClient, internals)
    return MediaRepoStack(repository, playlistRepository, episodeCatalogue, announcer)
}
