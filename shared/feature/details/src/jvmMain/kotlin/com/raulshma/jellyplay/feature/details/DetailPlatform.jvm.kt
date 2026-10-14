package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop actual of the detail share seam: no-op (documented dead-click —
 * subtitle-tester settings-row precedent; there is no desktop share sheet
 * v1, and the options-menu entry stays visible).
 */
@Composable
internal actual fun rememberShareMediaAction(itemId: String, chooserTitle: String): () -> Unit =
    remember(itemId) {
        { /* no-op on desktop v1 */ }
    }

/** Desktop ambient theme music: silent no-op (no desktop player for it v1). */
private object NoopDetailThemeMusic : DetailThemeMusic {
    override fun playThemeFor(itemId: String) = Unit
    override fun stop() = Unit
    override fun setMuted(muted: Boolean) = Unit
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false)
}

/**
 * Desktop storage probe: usable space on the appdata downloads volume — the
 * SAME root `DesktopDownloadStorageLayout` resolves (`dataDir/downloads`,
 * `dataDir/downloads/music` for audio), so the detail screen's free-space
 * hint agrees with where desktop downloads actually land. Falls back to the
 * dataDir itself when the subtree does not exist yet (first run).
 */
class DesktopDetailStorageProbe(
    private val dataDir: Path,
) : DetailStorageProbe {
    override suspend fun availableBytes(isAudio: Boolean): Long = withContext(Dispatchers.IO) {
        val base = File(dataDir.toFile(), if (isAudio) "downloads/music" else "downloads")
        val probe = if (base.exists()) base else base.parentFile ?: dataDir.toFile()
        probe.usableSpace
    }
}

/** Desktop platform pick for the details module (registered in desktop Main.kt). */
fun desktopDetailsPlatformModule(dataDir: Path): Module = module {
    single<DetailThemeMusic> { NoopDetailThemeMusic }
    single<DetailStorageProbe> { DesktopDetailStorageProbe(dataDir) }
    // The jvm-only detail defs (dependency closure reaches the
    // jvmShared halves of core:data — AudioQueueFacade, DownloadIntake,
    // OfflineSyncManager, SyncPlayManager) moved here out of commonMain's
    // detailsModule. Desktop registers BOTH modules; these defs resolve exactly
    // as before (same Koin defs, different module home).
    single { DownloadLifecycleActions.Factory(get(), get(), get(), get()) }
    single { ResyncActions.Factory(get(), get()) }
    single { WatchPartyActions.Factory(get(), get()) }
    single { MetadataAdminActions.Factory(get(), get(), get()) }
    single { DetailActionFactories(get(), get(), get(), get(), get()) }
    viewModel {
        DetailViewModel(
            storageProbe = get(),
            strings = get(),
            mediaRepository = get(),
            libraryApiClient = get(),
            collectionApiClient = get(),
            userDataMutator = get(),
            mediaDetailProvider = get(),
            playbackRepository = get(),
            imageUrlProvider = get(),
            offlineRepository = get(),
            stores = get(),
            remoteDiscovery = get(),
            audioQueueFacade = get(),
            themeMusicPlayer = get(),
            actionFactories = get(),
            mediaDownloadActions = get(),
            bookTocCacheRepository = get(),
            readerAnnotationsRepository = getOrNull(),
            bookTocProber = getOrNull(),
            // ADR 0010 companion-plugin seams — registered core:data /
            // core:network singles (DataSessionPlaybackKoinModule /
            // NetworkKoinModules); the VM null-guards everything anyway.
            pluginStatusStore = get(),
            pluginRatingsApi = get(),
            pluginRecommendationsApi = get(),
            pluginMarkersApi = get(),
            jellyPlayFeatureGate = get(),
            // D4: the direct-TMDB details fallback behind the externalRatings
            // enrichment (core:network single, NetworkKoinModules).
            tmdbApiClient = get(),
        )
    }
    // #147 merge: Collection/Person VMs left commonMain when their closure
    // reached MediaDownloadActions (core:data jvmShared) — defs live here and
    // in androidDetailsModule now, the DetailViewModel precedent.
    viewModel {
        CollectionDetailViewModel(
            mediaRepository = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
            mediaDownloadActions = get(),
        )
    }
    viewModel {
        PersonDetailViewModel(
            mediaRepository = get(),
            libraryApiClient = get(),
            userDataMutator = get(),
            imageUrlProvider = get(),
            mediaDownloadActions = get(),
            projections = get(),
        )
    }
}

/**
 * Desktop actual of the trailer-host seam: no in-app YouTube embed v1 —
 * fire the embed-failed path so call sites take their existing fallback
 * (external browser link / autoplay overlay hidden), exactly like an
 * Android WebView embed failure.
 */
@Composable
internal actual fun InlineTrailerPlayerHost(
    videoKey: String,
    modifier: Modifier,
    muted: Boolean,
    showControls: Boolean,
    autoplay: Boolean,
    focusable: Boolean,
    cropToFill: Boolean,
    onEmbedFailed: () -> Unit,
) {
    LaunchedEffect(videoKey) { onEmbedFailed() }
}

/**
 * Desktop actual of the D22 ambient-clip seam (v1): no ambient video on
 * desktop — the hero stays the backdrop image (the static-hero parity the
 * plan pins; the YouTube embed path degrades through its own actual above).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal actual fun AmbientThemeVideoSurface(url: String, modifier: Modifier) {
    // Static backdrop: intentionally empty.
}

/**
 * D12: desktop has no notification stack — the ⋮ menu's notify entry and
 * the D7 rows' bell are hidden outright (the store-side config writes are
 * reachable only through the hidden surfaces, so nothing is ever
 * configured for a platform that cannot deliver).
 */
internal actual val platformHasNotificationStack: Boolean = false

/**
 * D12: desktop permission seam — no POST_NOTIFICATIONS runtime flow exists
 * off Android; the lambda is a no-op (the surfaces that would invoke it are
 * hidden by [platformHasNotificationStack] anyway).
 */
@Composable
internal actual fun rememberSeriesNotificationPermissionRequest(): () -> Unit =
    remember { { /* no notifications to permit on desktop */ } }
