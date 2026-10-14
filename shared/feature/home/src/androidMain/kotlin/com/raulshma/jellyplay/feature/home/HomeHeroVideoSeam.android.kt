package com.raulshma.jellyplay.feature.home

import android.graphics.SurfaceTexture
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
import androidx.compose.ui.viewinterop.AndroidView
import com.raulshma.jellyplay.core.data.playback.AmbientVideoPlayer
import kotlinx.coroutines.awaitCancellation
import org.koin.compose.koinInject

/** Android/TV actual: the ExoPlayer-backed ambient transport is registered. */
internal actual val heroVideoPlatformCapable: Boolean = true

/**
 * Android actual of the hero-clip surface: a `TextureView` whose
 * `SurfaceTexture` (once available) is wrapped in a [Surface] and handed to
 * the process-wide [AmbientVideoPlayer] single. A single [LaunchedEffect]
 * keyed on (url, texture) is the only start/stop driver — every gate flip
 * unmounts this composable (HomeHero's `Crossfade`), and the url-guarded
 * [AmbientVideoPlayer.stop] makes an outgoing hero candidate's dispose a
 * no-op against the incoming candidate's already-started clip.
 */
@Composable
internal actual fun AmbientHeroVideoSurface(url: String, modifier: Modifier) {
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

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
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
 * Android/TV actual of the H7 embed fallback: core:ui's WebView YouTube
 * player with the detail backdrop's exact ambient configuration (muted, no
 * controls, cropped to fill) — [onEmbedFailed] folds the hero back to the
 * static image, the same degradation the detail screen uses.
 */
@Composable
internal actual fun HeroTrailerEmbedSurface(
    videoKey: String,
    modifier: Modifier,
    onEmbedFailed: () -> Unit,
) {
    com.raulshma.jellyplay.core.ui.components.InlineTrailerPlayer(
        videoKey = videoKey,
        modifier = modifier,
        muted = true,
        showControls = false,
        autoplay = true,
        focusable = false,
        cropToFill = true,
        onEmbedFailed = onEmbedFailed,
    )
}
