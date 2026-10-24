package com.raulshma.jellyplay.feature.details

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.Manifest
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.raulshma.jellyplay.core.data.playback.AmbientVideoPlayer
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
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
                putExtra(Intent.EXTRA_TEXT, DeepLinkGrammar.mediaLink(itemId))
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
    // The jvm-only detail defs (dependency closure reaches the
    // jvmShared halves of core:data — AudioQueueFacade, DownloadIntake,
    // OfflineSyncManager, SyncPlayManager) moved here out of commonMain's
    // detailsModule. Android registers BOTH modules; these defs resolve exactly
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
            mediaCatalogueEnrichments = get(),
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
            // The direct-TMDB details fallback behind the externalRatings
            // enrichment (core:network single, NetworkKoinModules).
            tmdbApiClient = get(),
            // The awards-badge toggle read (the AppearanceStore single, the
            // datastore common graph) — the externalRatings OMDb leg's pref half.
            appearanceStore = get(),
        )
    }
    // #147 merge: Collection/Person VMs left commonMain when their closure
    // reached MediaDownloadActions (core:data jvmShared) — defs live here and
    // in desktopDetailsPlatformModule now, the DetailViewModel precedent.
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
 * Android actual of the trailer-host seam: verbatim delegation to legacy
 * core:ui's WebView YouTube iframe player (the only consumer of it in the
 * repo; it stays legacy until a desktop embed story exists).
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

/**
 * Android actual of the ambient-clip seam: a `TextureView` whose
 * `SurfaceTexture` is bound to the process-wide AmbientVideoPlayer single
 * (feature/home's `AmbientHeroVideoSurface` twin — the same start/stop
 * driver shape, url-guarded so a superseded hero's dispose cannot kill the
 * mounted clip).
 */
@Composable
internal actual fun AmbientThemeVideoSurface(url: String, modifier: Modifier) {
    val player = koinInject<AmbientVideoPlayer>()
    val currentPlayer by rememberUpdatedState(player)
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        surfaceTexture = texture
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(destroyedTexture: SurfaceTexture): Boolean {
                        surfaceTexture = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )

    LaunchedEffect(url, surfaceTexture) {
        val texture = surfaceTexture
        if (texture == null) {
            currentPlayer.stop(url)
            return@LaunchedEffect
        }
        val surface = Surface(texture)
        try {
            currentPlayer.start(url, surface)
            awaitCancellation()
        } finally {
            currentPlayer.stop(url)
            surface.release()
        }
    }
}

/**
 * Android/TV carries the full notification stack — the ⋮ menu's notify
 * entry and the upcoming rows' bell render here.
 */
internal actual val platformHasNotificationStack: Boolean = true

/**
 * The app's cold-start permission prompt pattern (MainActivity:
 * `checkSelfPermission(POST_NOTIFICATIONS)` → `launcher.launch`) mirrored
 * into composition behind the activity-result launcher, so a first enable
 * from the ⋮ menu or the bell raises the Android 13+ system dialog when the
 * permission is not yet granted. Below API 33 (notifications on by default)
 * and when already granted, the returned lambda no-ops — the pure gate
 * ([shouldRequestPostNotifications]) decides.
 */
@Composable
internal actual fun rememberSeriesNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { /* grant/deny is read fresh at the next fire; the config written either way */ }
    return remember(context, launcher) {
        {
            if (shouldRequestPostNotifications(
                    sdkInt = Build.VERSION.SDK_INT,
                    granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED,
                )
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
