package com.raulshma.jellyplay.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class JellyPlayTileService : TileService() {

    @Inject lateinit var audioPlaybackManager: AudioPlaybackManager

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        job = CoroutineScope(Dispatchers.Main).launch {
            audioPlaybackManager.isPlaying.collect { isPlaying ->
                updateTile(isPlaying)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
        job = null
    }

    override fun onClick() {
        super.onClick()
        val isPlaying = audioPlaybackManager.isPlaying.value
        if (isPlaying) {
            audioPlaybackManager.pause()
            updateTile(false)
        } else if (audioPlaybackManager.hasActiveSession) {
            audioPlaybackManager.resume()
            updateTile(true)
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

    private fun updateTile(isPlaying: Boolean = false) {
        qsTile?.apply {
            state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (audioPlaybackManager.hasActiveSession) {
                audioPlaybackManager.title.value.ifEmpty { getString(R.string.app_name) }
            } else {
                getString(R.string.app_name)
            }
            contentDescription = if (isPlaying) "Playing" else getString(R.string.app_name)
            updateTile()
        }
    }
}
