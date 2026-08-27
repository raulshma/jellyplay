package com.raulshma.jellyplay.feature.details

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android actual of the detail share seam — the legacy
 * `Intent.createChooser(ACTION_SEND)` body verbatim (NEW_TASK included),
 * with the chooser title pre-resolved in composition by the caller.
 */
@Composable
internal actual fun rememberShareMediaAction(itemId: String, chooserTitle: String): () -> Unit {
    val context = LocalContext.current
    return remember(itemId, context) {
        {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "jellyplay://media/$itemId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
        }
    }
}

/**
 * Android storage probe — the legacy DetailViewModel body verbatim
 * (DIRECTORY_MUSIC/DIRECTORY_MOVIES external volume, filesDir fallback,
 * StatFs block math), extracted behind the common seam.
 */
class AndroidDetailStorageProbe(
    private val context: Context,
) : DetailStorageProbe {
    override suspend fun availableBytes(isAudio: Boolean): Long = withContext(Dispatchers.IO) {
        val downloadDir = context.getExternalFilesDir(
            if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES,
        ) ?: context.filesDir
        val stat = StatFs(downloadDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }
}

/** Android platform pick for the details module (registered app-side). */
fun androidDetailsModule(context: Context): Module = module {
    single<DetailStorageProbe> { AndroidDetailStorageProbe(context) }
    // Wave 16C: the jvm-only detail defs (dependency closure reaches the
    // jvmShared halves of core:data — AudioQueueFacade, DownloadIntake,
    // OfflineSyncManager, SyncPlayManager) moved here out of commonMain's
    // detailsModule, which is now the wasm-clean module the web shell
    // registers. Android registers BOTH modules; these defs resolve exactly
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
        )
    }
}

/**
 * Android actual of the trailer-host seam: verbatim delegation to legacy
 * core:ui's WebView YouTube iframe player (the only consumer of it in the
 * repo; it stays legacy until a desktop/web embed story exists).
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
    com.raulshma.jellyplay.core.ui.components.InlineTrailerPlayer(
        videoKey = videoKey,
        modifier = modifier,
        muted = muted,
        showControls = showControls,
        autoplay = autoplay,
        focusable = focusable,
        cropToFill = cropToFill,
        onEmbedFailed = onEmbedFailed,
    )
}
