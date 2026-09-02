package com.raulshma.jellyplay.core.data.remote

import android.util.Log
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sends `PlaybackStart` / `PlaybackProgress` / `PlaybackStopped` to the
 * Jellyfin server while a remotely-initiated session is active.
 *
 * One progress loop polls the active engine / audio manager every 10s while
 * playing and every 60s while paused, until [stopSession] is called.
 */
class RemotePlaybackReporter(
    private val playbackRepository: PlaybackRepository,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val authRepository: AuthRepository,
    private val activePlayerController: ActivePlayerController,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressJob: Job? = null
    private var sessionId: String? = null
    private var currentItemId: String? = null
    private var currentMediaSourceId: String? = null
    private var lastPositionTicks: Long = 0L

    /**
     * Begin reporting a new remotely-initiated session. Generates a fresh
     * `sessionId` (UUID) and reports `PlaybackStart` to the server.
     */
    suspend fun startSession(
        itemIds: List<String>,
        startPositionTicks: Long,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mediaSourceId: String? = null,
    ) {
        stopSessionInternal(sendStop = false)

        val firstId = itemIds.firstOrNull() ?: return
        if (!isAuthenticated()) {
            Log.w(TAG, "Cannot start remote session: not authenticated")
            return
        }

        audioPlaybackManager.remoteSessionActive = true

        val newSessionId = java.util.UUID.randomUUID().toString()
        sessionId = newSessionId
        currentItemId = firstId
        currentMediaSourceId = mediaSourceId
        lastPositionTicks = startPositionTicks

        playbackRepository.reportPlaybackStart(
            info = PlaybackStartInfo(
                itemId = firstId,
                sessionId = newSessionId,
                mediaSourceId = mediaSourceId,
                playMethod = playMethod,
            ),
        )
        Log.d(TAG, "Started remote session: sessionId=$newSessionId itemId=$firstId pos=$startPositionTicks")

        progressJob = scope.launch {
            while (isActive) {
                val playing = currentPositionTicksAndPaused()
                if (playing != null) {
                    val (pos, paused) = playing
                    lastPositionTicks = pos
                    val itemId = currentItemId ?: continue
                    val sid = sessionId ?: continue
                    playbackRepository.reportPlaybackProgress(
                        progress = PlaybackProgress(
                            itemId = itemId,
                            sessionId = sid,
                            positionTicks = pos,
                            isPaused = paused,
                            playMethod = playMethod,
                            mediaSourceId = currentMediaSourceId,
                        ),
                    )
                }
                delay(if (isPausedNow()) 60_000L else 10_000L)
            }
        }
    }

    /**
     * Stop the active session and send a final `PlaybackStopped` to the server.
     */
    suspend fun stopSession() {
        stopSessionInternal(sendStop = true)
    }

    private suspend fun stopSessionInternal(sendStop: Boolean) {
        progressJob?.cancel()
        progressJob = null
        val sid = sessionId
        val id = currentItemId
        if (sendStop && sid != null && id != null) {
            playbackRepository.reportPlaybackStopped(
                itemId = id,
                sessionId = sid,
                positionTicks = lastPositionTicks,
            )
            Log.d(TAG, "Stopped remote session: sessionId=$sid itemId=$id pos=$lastPositionTicks")
        }
        sessionId = null
        currentItemId = null
        currentMediaSourceId = null
        audioPlaybackManager.remoteSessionActive = false
    }

    private fun currentPositionTicksAndPaused(): Pair<Long, Boolean>? {
        val engine: RemotePlayableEngine? = activePlayerController.engine
        if (engine != null) {
            return engine.currentPositionMs * 10_000L to !engine.isPlaying.value
        }
        val pos = audioPlaybackManager.currentPosition.value
        val paused = !audioPlaybackManager.isPlaying.value
        if (audioPlaybackManager.hasActiveSession) {
            return pos * 10_000L to paused
        }
        return null
    }

    private fun isPausedNow(): Boolean {
        val engine = activePlayerController.engine
        if (engine != null) return !engine.isPlaying.value
        return !audioPlaybackManager.isPlaying.value
    }

    private suspend fun isAuthenticated(): Boolean = try {
        authRepository.isAuthenticated.first()
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val TAG = "RemotePlaybackRpt"
    }
}
