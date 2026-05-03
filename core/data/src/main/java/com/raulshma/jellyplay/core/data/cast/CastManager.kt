package com.raulshma.jellyplay.core.data.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var castPlayer: CastPlayer? = null
    private var sessionListener: SessionAvailabilityListener? = null

    val isCastAvailable: Boolean
        get() = try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.currentCastSession?.isConnected == true ||
                    castContext.castState != CastState.NO_DEVICES_AVAILABLE
        } catch (_: Exception) {
            false
        }

    val isConnected: Boolean
        get() = try {
            CastContext.getSharedInstance(context)
                .sessionManager.currentCastSession?.isConnected == true
        } catch (_: Exception) {
            false
        }

    fun getCastPlayer(listener: Player.Listener): CastPlayer? {
        if (!isConnected) return null
        if (castPlayer == null) {
            try {
                val castContext = CastContext.getSharedInstance(context)
                sessionListener = object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() {}
                    override fun onCastSessionUnavailable() {
                        listener.onPlaybackStateChanged(Player.STATE_ENDED)
                    }
                }
                castPlayer = CastPlayer(castContext).apply {
                    addListener(listener)
                    setSessionAvailabilityListener(sessionListener!!)
                }
            } catch (_: Exception) {
                return null
            }
        }
        return castPlayer
    }

    fun release() {
        castPlayer?.release()
        castPlayer = null
        sessionListener = null
    }

    fun loadMedia(
        mediaItem: MediaItem,
        startPositionMs: Long = 0,
        listener: Player.Listener,
    ) {
        val player = getCastPlayer(listener) ?: return
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }
}
