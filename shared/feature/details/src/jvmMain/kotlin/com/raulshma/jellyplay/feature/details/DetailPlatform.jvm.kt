package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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

/** Desktop per-item audio playback: silent no-op (see [DetailAudioPlayback] doc). */
private object NoopDetailAudioPlayback : DetailAudioPlayback {
    override fun play(itemId: String) = Unit
}

/** Desktop ambient theme music: silent no-op (no desktop player for it v1). */
private object NoopDetailThemeMusic : DetailThemeMusic {
    override fun playThemeFor(itemId: String) = Unit
    override fun stop() = Unit
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
    single<DetailAudioPlayback> { NoopDetailAudioPlayback }
    single<DetailThemeMusic> { NoopDetailThemeMusic }
    single<DetailStorageProbe> { DesktopDetailStorageProbe(dataDir) }
    // Wave 16C: the jvm-only detail defs (dependency closure reaches the
    // jvmShared halves of core:data — AudioQueueFacade, DownloadIntake,
    // OfflineSyncManager, SyncPlayManager) moved here out of commonMain's
    // detailsModule, which is now the wasm-clean module the web shell
    // registers. Desktop registers BOTH modules; these defs resolve exactly
    // as before (same Koin defs, different module home).
    single { DownloadLifecycleActions.Factory(get(), get(), get(), get()) }
    single { ResyncActions.Factory(get(), get()) }
    single { WatchPartyActions.Factory(get(), get()) }
    single { DetailActionFactories(get(), get(), get(), get()) }
    viewModel {
        DetailViewModel(
            storageProbe = get(),
            strings = get(),
            mediaRepository = get(),
            userDataMutator = get(),
            mediaDetailProvider = get(),
            playbackRepository = get(),
            imageUrlProvider = get(),
            offlineRepository = get(),
            stores = get(),
            remoteDiscovery = get(),
            audioPlaybackManager = get(),
            audioQueueFacade = get(),
            themeMusicPlayer = get(),
            actionFactories = get(),
            mediaDownloadActions = get(),
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
            userDataMutator = get(),
            imageUrlProvider = get(),
            mediaDownloadActions = get(),
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
