package com.raulshma.jellyplay.core.data.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.toBitmap
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioPlaybackManager: AudioPlaybackManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val maxDynamicShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        .coerceAtMost(MAX_DYNAMIC_SHORTCUTS)

    fun observePlaybackForDynamicShortcuts() {
        scope.launch {
            combine(
                audioPlaybackManager.currentPlayingItemId.filterNotNull(),
                audioPlaybackManager.title,
                audioPlaybackManager.artist,
            ) { itemId, title, artist -> TrackInfo(itemId, title, artist) }
                .distinctUntilChanged { old, new -> old.itemId == new.itemId }
                .collect { track -> pushContinueListeningShortcut(track) }
        }
    }

    private suspend fun pushContinueListeningShortcut(track: TrackInfo) {
        val intent = Intent(context, mainActivityClass).apply {
            action = ACTION_PLAY_AUDIO
            putExtra(EXTRA_ITEM_ID, track.itemId)
            putExtra(EXTRA_TITLE, track.title)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val icon = try {
            createShortcutIcon()
        } catch (_: Exception) {
            createDefaultIcon()
        }

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_CONTINUE_LISTENING)
            .setShortLabel(track.title.ifBlank { "Continue Listening" })
            .setLongLabel("Play ${track.title}")
            .setIcon(icon)
            .setIntent(intent)
            .setLongLived(true)
            .build()

        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (_: Exception) {
            val fallbackShortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_CONTINUE_LISTENING)
                .setShortLabel(track.title.ifBlank { "Continue Listening" })
                .setLongLabel("Play ${track.title}")
                .setIcon(createDefaultIcon())
                .setIntent(intent)
                .setLongLived(true)
                .build()
            try {
                ShortcutManagerCompat.pushDynamicShortcut(context, fallbackShortcut)
            } catch (_: Exception) { }
        }
    }

    private suspend fun createShortcutIcon(): IconCompat {
        val artworkUrl = audioPlaybackManager.albumArtUrl.value
        if (artworkUrl.isNullOrBlank()) return createDefaultIcon()

        // ShortcutManagerCompat renders the icon at the system shortcut size
        // (~48–96 dp × density, i.e. ≤ ~288 px on xxxhdpi). Decoding at 1080²
        // allocated a ~4.5 MB bitmap (ARGB_8888) for a ≤0.3 MB target — wasted
        // heap + extra decode time on a path that runs on every audio shortcut
        // push (often during playback transitions). 192 px covers the largest
        // rendered size; the framework downscales anyway, this just avoids the
        // over-decode.
        val request = ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(192)
            // Disable the memory cache so we decode a private Bitmap instance.
            // toBitmap() otherwise returns the shared cache Bitmap (also held
            // by any Compose AsyncImage showing albumArtUrl). Handing that
            // shared Bitmap to ShortcutManagerCompat risks the BitmapPool
            // recycling it out from under the system while it serializes the
            // icon. Matches WidgetImageLoader/JellyPlayNotificationProvider.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = result.image?.toBitmap() ?: return createDefaultIcon()
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun createDefaultIcon(): IconCompat {
        return IconCompat.createWithResource(context, getDefaultShortcutIcon())
    }

    private val defaultShortcutIconRes by lazy {
        context.resources.getIdentifier(
            "ic_shortcut_play_music", "drawable", context.packageName
        )
    }

    private fun getDefaultShortcutIcon(): Int = defaultShortcutIconRes

    companion object {
        const val ACTION_CONTINUE_WATCHING = "com.raulshma.jellyplay.action.CONTINUE_WATCHING"
        const val ACTION_SEARCH = "com.raulshma.jellyplay.action.SEARCH"
        const val ACTION_PLAY_MUSIC = "com.raulshma.jellyplay.action.PLAY_MUSIC"
        const val ACTION_DOWNLOADS = "com.raulshma.jellyplay.action.DOWNLOADS"
        const val ACTION_PLAY_AUDIO = "com.raulshma.jellyplay.action.PLAY_AUDIO"
        // Static launcher shortcuts
        const val ACTION_SURPRISE_ME = "com.raulshma.jellyplay.action.SURPRISE_ME"
        const val ACTION_SETTINGS = "com.raulshma.jellyplay.action.SETTINGS"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_TITLE = "extra_title"

        private const val SHORTCUT_ID_CONTINUE_LISTENING = "continue_listening"
        private const val MAX_DYNAMIC_SHORTCUTS = 3
        private const val MAIN_ACTIVITY_CLASS = "com.raulshma.jellyplay.MainActivity"
    }

    private val mainActivityClass by lazy { Class.forName(MAIN_ACTIVITY_CLASS) }
}

private data class TrackInfo(
    val itemId: String,
    val title: String,
    val artist: String,
)
