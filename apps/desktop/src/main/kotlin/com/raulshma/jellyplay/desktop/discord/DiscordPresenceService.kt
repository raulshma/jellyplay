package com.raulshma.jellyplay.desktop.discord

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Discord Rich Presence orchestrator (feature 4.2): folds the app-wide
 * [NowPlayingReporter] snapshot + the active video engine's play state + the
 * desktop audio queue's play state + [SyncPlayManager.currentGroupFlow] into
 * one [DiscordPresenceMapping.mapActivity] verdict, dedupes it, and pushes
 * it through the [DiscordIpcClient]. The toggle
 * (`discord_presence_enabled`, the screensaver store's desktop-shell block)
 * gates the CONNECTION: off (the default) the client stays stopped — the
 * presence service is disconnected and Discord clears whatever it still
 * renders — and flipping it on (re)starts the pipe and the payload
 * collector.
 *
 * Join handling: the client's `ACTIVITY_JOIN` events carry the secret this
 * service published ([DiscordPresenceMapping.mapActivity]'s Join secret);
 * [DiscordPresenceMapping.decodeJoinSecret] decodes it back to the group id
 * and [handleJoinSecret] runs [SyncPlayManager.joinGroup] — the Discord
 * Join button joins the watch party.
 *
 * Never fatal: every publisher/consumer failure logs and degrades — presence
 * must not be able to take playback down with it. Idempotent [start] (the
 * launchDesktopStartup idiom); the collection dies with [scope].
 */
internal class DiscordPresenceService(
    private val scope: CoroutineScope,
    private val client: DiscordIpcClient,
    private val nowPlayingReporter: NowPlayingReporter,
    private val activePlayerController: ActivePlayerController,
    /** The desktop audio queue's play state (the manager's isPlaying flow). */
    private val audioIsPlaying: Flow<Boolean>,
    private val syncPlayManager: SyncPlayManager,
    /** The `discord_presence_enabled` toggle — off keeps the client disconnected. */
    private val enabled: Flow<Boolean>,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private var collectJob: Job? = null

    /** Idempotent app-lifetime kickoff; arms the toggle-driven collector. */
    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            // The mapped store flow re-emits on any screensaver-pref write;
            // only the toggle's own flips may (re)arm the pipe.
            enabled.distinctUntilChanged().collectLatest { on ->
                if (!on) {
                    // Off (the default): the pipe stays down — disconnecting
                    // makes Discord drop the presence it still renders.
                    client.stop()
                    return@collectLatest
                }
                client.start()
                activityPayloads().collect { payload ->
                    runCatchingRethrowingCancellation { client.setActivity(payload) }
                        .onFailure { Log.w(TAG, "Presence update failed", it) }
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        client.stop()
    }

    /**
     * The deduped `SET_ACTIVITY` payload stream: the folded activity spec,
     * distinct so an unchanged presence never re-sends, THEN serialized with
     * a fresh per-send nonce (dedup compares the spec — a nonce folded in
     * before it would make every emission distinct and the guard dead).
     * Null spec → the clear payload. Collected only while the toggle is on.
     */
    internal fun activityPayloads(): Flow<String> {
        // The video engine's play state re-subscribes per bound engine —
        // combine cannot see through a nested StateFlow.
        val videoPlaying = activePlayerController.activeEngine
            .flatMapLatest { engine -> engine?.isPlaying ?: flowOf(false) }
        return combine(
            nowPlayingReporter.nowPlaying,
            videoPlaying,
            audioIsPlaying,
            syncPlayManager.currentGroupFlow,
        ) { meta, videoPlaying, audioPlaying, group ->
            val isPlaying = when (meta?.kind) {
                NowPlayingReporter.Kind.VIDEO -> videoPlaying
                NowPlayingReporter.Kind.MUSIC -> audioPlaying
                null -> false
            }
            DiscordPresenceMapping.mapActivity(meta, isPlaying, group, nowMs())
        }
            .distinctUntilChanged()
            .map { spec ->
                DiscordPresenceMapping.setActivityPayload(
                    spec,
                    ProcessHandle.current().pid(),
                    DiscordPresenceMapping.newNonce(),
                )
            }
    }

    /**
     * The Discord Join button's handler: decodes the join secret into the
     * SyncPlay group id and joins it on the shell's scope. The pure half
     * ([DiscordPresenceMapping.decodeJoinSecret]) is separately testable.
     */
    internal fun handleJoinSecret(secret: String) {
        val groupId = DiscordPresenceMapping.decodeJoinSecret(secret)
        if (groupId == null) {
            Log.w(TAG, "Discord join secret did not decode to a SyncPlay group")
            return
        }
        scope.launch {
            runCatchingRethrowingCancellation { syncPlayManager.joinGroup(groupId) }
                .onFailure { Log.w(TAG, "Discord-joined SyncPlay group failed: $groupId", it) }
                .onSuccess { Log.d(TAG, "Joined SyncPlay group $groupId via Discord") }
        }
    }

    companion object {
        private const val TAG = "DiscordPresenceService"
    }
}
