package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.raulshma.jellyplay.core.model.PlayerType

/**
 * Handles launching video playback in external third-party players
 * via an Android Intent chooser.
 *
 * Only used when the user selects [PlayerType.EXTERNAL].
 * mpv and LibVLC are now embedded as in-app engines via [com.raulshma.jellyplay.feature.player.video.engine.MediaEngine].
 */
object ExternalPlayerLauncher {

    /**
     * Attempts to launch the given [streamUrl] via an external player intent.
     *
     * @return `true` if a chooser was shown, `false` if the player type
     *         should be handled internally.
     */
    fun tryLaunch(
        context: Context,
        playerType: PlayerType,
        streamUrl: String,
        title: String,
        startPositionMs: Long = 0,
    ): Boolean {
        if (playerType != PlayerType.EXTERNAL) return false

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(streamUrl), "video/*")
            putExtra("title", title)
            if (startPositionMs > 0) {
                putExtra("position", startPositionMs)
            }
        }

        return try {
            context.startActivity(Intent.createChooser(intent, "Open with…"))
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No video player found", Toast.LENGTH_LONG).show()
            false
        }
    }
}
