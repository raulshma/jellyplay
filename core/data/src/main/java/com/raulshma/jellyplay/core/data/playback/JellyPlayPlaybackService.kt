package com.raulshma.jellyplay.core.data.playback

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.raulshma.jellyplay.core.data.di.koin

@UnstableApi
class JellyPlayPlaybackService : MediaLibraryService(), PlaybackSessionManager.Listener {

    // Wave 8A: Hilt left this module — the two media3 singletons resolve from
    // the Koin container (androidCoreDataModule), which the app composition
    // root starts before super.onCreate(). Lazy keeps construction off the
    // service's critical path until a lifecycle callback actually needs them.
    private val sessionManager: PlaybackSessionManager by lazy { koin().get() }
    private val audioPlaybackManager: AudioPlaybackManager by lazy { koin().get() }

    private var notificationProvider: JellyPlayNotificationProvider? = null

    override fun onCreate() {
        super.onCreate()
        // Hold our own reference so onDestroy can release the provider's
        // coroutine scope + cached bitmap. Without this the provider (held
        // only weakly by the parent MediaLibraryService) leaks a
        // SupervisorJob + Dispatchers.IO scope and a Bitmap on every service
        // destroy/recreate cycle.
        val provider = JellyPlayNotificationProvider(this)
        notificationProvider = provider
        setMediaNotificationProvider(provider)
        sessionManager.addListener(this)
    }

    override fun onSessionChanged(newSession: MediaSession?, oldSession: MediaSession?) {
        if (oldSession != null && isSessionAdded(oldSession)) {
            removeSession(oldSession)
        }
        if (newSession != null && !isSessionAdded(newSession)) {
            addSession(newSession)
        }
        if (newSession == null) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return sessionManager.currentSession as? MediaLibrarySession
    }

    @UnstableApi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = sessionManager.currentSession
        val player = session?.player ?: run {
            stopSelf()
            return
        }
        // Keep background audio alive only while actually playing. If the user
        // swipes the app away while paused (or buffering/idle/ended), release
        // the player + wake lock so they don't leak via a lingering ongoing
        // notification. Mirrors Spotify/Apple/Google Music behaviour.
        if (!player.isPlaying) {
            audioPlaybackManager.stopAndRelease()
            stopSelf()
            // Let Media3 run its task-removed bookkeeping (clearing the
            // media-button receiver / task description) before returning.
            super.onTaskRemoved(rootIntent)
            return
        }
        // Playing in the background: defer to Media3's default, which keeps the
        // task alive while playing. This override exists only to stop + release
        // when playback is actually paused.
    }

    override fun onDestroy() {
        sessionManager.removeListener(this)
        val session = sessionManager.currentSession
        if (session != null) {
            sessionManager.clearSession(session)
            try { session.release() } catch (_: Exception) { }
        }
        // Release ExoPlayer, audio effects, and the audio MediaSession held by
        // AudioPlaybackManager so they don't linger after the service is destroyed.
        audioPlaybackManager.stopAndRelease()
        // Release our notification provider so its coroutine scope and cached
        // artwork bitmap are torn down cleanly.
        notificationProvider?.release()
        notificationProvider = null
        super.onDestroy()
    }
}
