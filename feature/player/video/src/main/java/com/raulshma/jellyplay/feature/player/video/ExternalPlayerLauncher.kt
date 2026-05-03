package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.raulshma.jellyplay.core.model.PlayerType

/**
 * Handles launching video playback in external or third-party players
 * based on the user's preferred player setting.
 */
object ExternalPlayerLauncher {

    /**
     * Attempts to launch the given [streamUrl] in the player dictated by [playerType].
     *
     * @return `true` if the player was launched (or at least an intent was fired),
     *         `false` if the player type should be handled internally.
     */
    fun tryLaunch(
        context: Context,
        playerType: PlayerType,
        streamUrl: String,
        title: String,
        startPositionMs: Long = 0,
    ): Boolean {
        return when (playerType) {
            PlayerType.EXO_PLAYER -> false // handled internally by VideoPlayerScreen

            PlayerType.MPV -> {
                launchMpv(context, streamUrl, title, startPositionMs)
                true
            }

            PlayerType.LIBVLC -> {
                launchVlc(context, streamUrl, title, startPositionMs)
                true
            }

            PlayerType.EXTERNAL -> {
                launchGenericExternal(context, streamUrl, title, startPositionMs)
                true
            }
        }
    }

    private fun launchMpv(context: Context, url: String, title: String, startPositionMs: Long) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            // mpv-android specific extras
            setPackage("is.xyz.mpv")
            putExtra("title", title)
            if (startPositionMs > 0) {
                putExtra("position", (startPositionMs / 1000).toInt()) // seconds
            }
        }
        launchOrFallback(context, intent, "mpv-android", url, title, startPositionMs)
    }

    private fun launchVlc(context: Context, url: String, title: String, startPositionMs: Long) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            setPackage("org.videolan.vlc")
            putExtra("title", title)
            if (startPositionMs > 0) {
                putExtra("position", startPositionMs) // VLC expects milliseconds
            }
            putExtra("from_start", startPositionMs <= 0)
        }
        launchOrFallback(context, intent, "VLC", url, title, startPositionMs)
    }

    private fun launchGenericExternal(
        context: Context,
        url: String,
        title: String,
        startPositionMs: Long,
    ) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title)
            if (startPositionMs > 0) {
                putExtra("position", startPositionMs)
            }
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with…"))
        } catch (e: Exception) {
            Toast.makeText(context, "No video player found", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Tries the targeted intent first; if the specific app isn't installed
     * falls back to a generic chooser so the user can pick any player.
     */
    private fun launchOrFallback(
        context: Context,
        intent: Intent,
        appName: String,
        url: String,
        title: String,
        startPositionMs: Long,
    ) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "$appName not installed — opening chooser",
                Toast.LENGTH_SHORT,
            ).show()
            launchGenericExternal(context, url, title, startPositionMs)
        }
    }
}
