package com.raulshma.jellyplay.core.data.remote

import android.util.Log
import com.raulshma.jellyplay.core.data.playback.NowPlayingVerdict
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
 * playing and every 60s while paused, until [stopSession] is called. The
 * poll itself is a [NowPlayingVerdict] read: the engine-vs-manager fork
 * this class used to restate twice (`currentPositionTicksAndPaused` +
 * `isPausedNow`) is the verdict's [NowPlayingVerdict.progressTicksAndPaused]
 * / [NowPlayingVerdict.isPausedNow], and the remote-session flag it used to
 * write on the audio manager is the verdict's cell (this reporter is still
 * the ONE writer — via the verdict's internal mark, at the same two
 * protocol points).
 */
class RemotePlaybackReporter(
    private val playbackRepository: PlaybackRepository,
    private val nowPlayingVerdict: NowPlayingVerdict,
    private val authRepository: AuthRepository,
    /**
     * The Koin application scope (DatastoreQualifiers.applicationScope) the
     * IO scope hangs off as a SupervisorJob child — only the per-session
     * progress job is cancelled locally, so the child now dies with an
     * app-level teardown instead of outliving it.
     */
    private val applicationScope: CoroutineScope,
) {

    private val scope = CoroutineScope(SupervisorJob(applicationScope.coroutineContext[Job]) + Dispatchers.IO)
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

        nowPlayingVerdict.markRemoteSession(true)

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
                val playing = nowPlayingVerdict.progressTicksAndPaused()
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
                delay(if (nowPlayingVerdict.isPausedNow()) 60_000L else 10_000L)
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
        nowPlayingVerdict.markRemoteSession(false)
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
