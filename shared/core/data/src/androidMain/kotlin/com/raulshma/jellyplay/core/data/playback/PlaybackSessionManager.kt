package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService.MediaLibrarySession

class PlaybackSessionManager(
    private val context: Context,
) {
    interface Listener {
        fun onSessionChanged(newSession: MediaSession?, oldSession: MediaSession?)
    }

    private val lock = Any()

    @GuardedBy("lock")
    private var _currentSession: MediaSession? = null
    val currentSession: MediaSession? get() = synchronized(lock) { _currentSession }

    @GuardedBy("lock")
    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        val session: MediaSession?
        synchronized(lock) {
            listeners.add(listener)
            session = _currentSession
        }
        if (session != null) {
            listener.onSessionChanged(session, null)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    /**
     * Sets the active session. Releases the previous session if it's still active
     * and the caller hasn't already released it.
     *
     * Priority guard: an actively-playing session is **not** displaced by an
     * incoming session whose player is idle. The audio singleton (created at app
     * launch) and audio crossfade rebuilds both call `setActiveSession`; without
     * this guard an idle audio session could evict a playing video session,
     * making the now-playing notification + media buttons target the wrong
     * player until the app was restarted. A playing→playing swap (audio
     * crossfade) still displaces normally; so does any swap where the holder is
     * not currently playing. The incoming session is released on rejection so
     * the caller's freshly-built session doesn't leak.
     */
    fun setActiveSession(session: MediaSession) {
        val oldSession: MediaSession?
        val currentListeners: List<Listener>
        synchronized(lock) {
            oldSession = _currentSession
            // Log the concrete session types + play states on every call so a
            // session-type regression (MediaLibrarySession downgraded to plain
            // MediaSession) is immediately visible in logcat.
            Log.d(
                TAG,
                "setActiveSession: challenger=${sessionKind(session)} (playing=${session.player.isPlaying}), " +
                    "holder=${oldSession?.let { sessionKind(it) }} (playing=${oldSession?.player?.isPlaying})",
            )
            if (oldSession != null &&
                oldSession !== session &&
                oldSession.player.isPlaying &&
                !session.player.isPlaying) {
                // Current session is actively playing and the challenger is idle:
                // refuse the eviction. Release the rejected session to avoid a leak.
                // Log (don't swallow silently) so a rejection-time leak is diagnosable —
                // mirrors the logging policy used by startPlaybackService below.
                Log.w(
                    TAG,
                    "Rejected idle challenger ${sessionKind(session)} (playing=${session.player.isPlaying}) " +
                        "against playing holder ${sessionKind(oldSession)} (playing=${oldSession.player.isPlaying})",
                )
                try {
                    session.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to release rejected idle challenger session", e)
                }
                return
            }
            _currentSession = session
            currentListeners = listeners.toList()
        }

        currentListeners.forEach { it.onSessionChanged(session, oldSession) }

        // Release the old session only if it's not the same as the new one.
        // Use try-catch to guard against double-release (isReleased is package-private).
        if (oldSession != null && oldSession !== session) {
            try {
                oldSession.release()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release replaced media session", e)
            }
        }
        // Ensure the playback service is started and will pick up this session
        startPlaybackService()
    }

    fun clearSession(session: MediaSession) {
        val oldSession: MediaSession?
        val currentListeners: List<Listener>
        synchronized(lock) {
            if (_currentSession === session) {
                oldSession = _currentSession
                _currentSession = null
                currentListeners = listeners.toList()
            } else {
                return
            }
        }
        currentListeners.forEach { it.onSessionChanged(null, oldSession) }
    }

    /**
     * Starts (and promotes to foreground) the host playback service so Media3
     * can post the now-playing notification for the active session.
     *
     * When the active player is playing (or buffering toward play) we use
     * [Context.startForegroundService]: this is the only form permitted while the
     * app is backgrounded (plain [Context.startService] throws
     * `BackgroundServiceStartNotAllowedException` on Android 8+). Media3's
     * `MediaSessionService` base calls `startForeground(...)` itself before
     * returning, so promotion is handled correctly. When the player is idle we
     * keep the lighter [Context.startService], which is legal while the app is
     * foregrounded during normal load setup and avoids a startForeground ANR.
     */
    private fun startPlaybackService() {
        val session = currentSession ?: return
        val player = session.player
        val shouldForeground = player.isPlaying ||
            player.playbackState == Player.STATE_BUFFERING ||
            (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
        try {
            val intent = Intent(context, JellyPlayPlaybackService::class.java)
            if (shouldForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Service may not be registered yet, the app may be in background
            // restriction, or startForegroundService may exceed the ANR budget.
            // Log (don't swallow silently) so future regressions are diagnosable.
            Log.w(TAG, "startPlaybackService failed (foreground=$shouldForeground)", e)
        }
    }

    companion object {
        private const val TAG = "PlaybackSessionManager"

        /**
         * Classifies a session as [MediaLibrarySession] vs plain [MediaSession]
         * — the fact that matters for [JellyPlayPlaybackService.onGetSession]'s
         * `as? MediaLibrarySession` cast, which rejects plain sessions.
         */
        private fun sessionKind(session: MediaSession): String =
            if (session is MediaLibrarySession) "MediaLibrarySession" else "MediaSession"
    }
}
