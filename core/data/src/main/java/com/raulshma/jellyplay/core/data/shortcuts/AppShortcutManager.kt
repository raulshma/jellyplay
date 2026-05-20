package com.raulshma.jellyplay.core.data.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
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

    private fun pushContinueListeningShortcut(track: TrackInfo) {
        val intent = Intent(context, Class.forName(MAIN_ACTIVITY_CLASS)).apply {
            action = ACTION_PLAY_AUDIO
            putExtra(EXTRA_ITEM_ID, track.itemId)
            putExtra(EXTRA_TITLE, track.title)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_CONTINUE_LISTENING)
            .setShortLabel(track.title.ifBlank { "Continue Listening" })
            .setLongLabel("Play ${track.title}")
            .setIcon(createShortcutIcon(track))
            .setIntent(intent)
            .setLongLived(true)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    private fun createShortcutIcon(track: TrackInfo): IconCompat {
        val artworkUrl = audioPlaybackManager.albumArtUrl.value
        return if (!artworkUrl.isNullOrBlank()) {
            try {
                IconCompat.createWithContentUri(artworkUrl)
            } catch (_: Exception) {
                createDefaultIcon()
            }
        } else {
            createDefaultIcon()
        }
    }

    private fun createDefaultIcon(): IconCompat {
        return IconCompat.createWithResource(context, getDefaultShortcutIcon())
    }

    private fun getDefaultShortcutIcon(): Int {
        return context.resources.getIdentifier(
            "ic_shortcut_play_music", "drawable", context.packageName
        )
    }

    companion object {
        const val ACTION_CONTINUE_WATCHING = "com.raulshma.jellyplay.action.CONTINUE_WATCHING"
        const val ACTION_SEARCH = "com.raulshma.jellyplay.action.SEARCH"
        const val ACTION_PLAY_MUSIC = "com.raulshma.jellyplay.action.PLAY_MUSIC"
        const val ACTION_DOWNLOADS = "com.raulshma.jellyplay.action.DOWNLOADS"
        const val ACTION_PLAY_AUDIO = "com.raulshma.jellyplay.action.PLAY_AUDIO"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_TITLE = "extra_title"

        private const val SHORTCUT_ID_CONTINUE_LISTENING = "continue_listening"
        private const val MAX_DYNAMIC_SHORTCUTS = 3
        private const val MAIN_ACTIVITY_CLASS = "com.raulshma.jellyplay.MainActivity"
    }
}

private data class TrackInfo(
    val itemId: String,
    val title: String,
    val artist: String,
)
