package com.raulshma.jellyplay.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

@RequiresApi(Build.VERSION_CODES.N)
class JellyPlayTileService : TileService() {

    // Koin single (wave 8B — Hilt removal); lazy defers the playback graph's
    // construction until the tile is actually bound/listened to.
    private val audioPlaybackManager: AudioPlaybackManager by lazy { KoinPlatform.getKoin()!!.get() }

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        // Collect both play state and title so the tile can reflect three states:
        // playing, paused (session loaded but not playing), and inactive (no
        // session). QS tiles are binary (ACTIVE/INACTIVE), so the paused state is
        // surfaced via the label/contentDescription rather than the tile icon.
        job = tileScope.launch {
            kotlinx.coroutines.flow.combine(
                audioPlaybackManager.isPlaying,
                audioPlaybackManager.title,
            ) { isPlaying, title -> isPlaying to title }.collect { (isPlaying, title) ->
                updateTile(isPlaying, title)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
        job = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // TileService instances are created/destroyed by the system (listening
        // bind/unbind, user pulling the tile); without cancelling tileScope the
        // SupervisorJob + main-dispatcher association leak per recreate.
        tileScope.cancel()
    }

    override fun onClick() {
        super.onClick()
        val isPlaying = audioPlaybackManager.isPlaying.value
        if (isPlaying) {
            audioPlaybackManager.pause()
            updateTile(false, audioPlaybackManager.title.value)
        } else if (audioPlaybackManager.hasActiveSession) {
            audioPlaybackManager.resume()
            updateTile(true, audioPlaybackManager.title.value)
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile(isPlaying: Boolean = false, title: String = "") {
        val hasSession = audioPlaybackManager.hasActiveSession
        qsTile?.apply {
            state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = when {
                // Paused: a track is loaded but not playing.
                hasSession && !isPlaying -> {
                    val t = title.ifEmpty { getString(R.string.app_name) }
                    getString(R.string.tile_paused_label, t)
                }
                // Playing: show the track title.
                hasSession -> title.ifEmpty { getString(R.string.app_name) }
                // No session: just the app name.
                else -> getString(R.string.app_name)
            }
            contentDescription = when {
                isPlaying -> getString(R.string.tile_playing_cd)
                hasSession -> getString(R.string.tile_paused_cd)
                else -> getString(R.string.app_name)
            }
            updateTile()
        }
    }
}
