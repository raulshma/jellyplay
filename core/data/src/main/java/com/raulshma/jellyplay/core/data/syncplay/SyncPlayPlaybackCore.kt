package com.raulshma.jellyplay.core.data.syncplay

import android.util.Log
import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface PlaybackCoreCallbacks {
    fun localPlay()
    fun localPause()
    fun localSeek(positionMs: Long)
    fun setPlaybackRate(rate: Float)
    fun currentPositionMs(): Long
    fun durationMs(): Long
    fun isPlaying(): Boolean
    fun onSyncStateChanged(synced: Boolean, syncing: Boolean)
}

@Singleton
class SyncPlayPlaybackCore @Inject constructor(
    private val timeSyncManager: TimeSyncManager,
    private val controller: SyncPlayController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var lastCommand: SyncPlayPlaybackCommand? = null

    @Volatile
    private var currentPlaylistItemId: String? = null

    @Volatile
    private var syncEnabled = false

    @Volatile
    private var syncCorrectionJob: Job? = null

    @Volatile
    private var scheduledCommandJob: Job? = null

    @Volatile
    private var enableSyncJob: Job? = null

    @Volatile
    private var speedToSyncJob: Job? = null

    @Volatile
    private var pendingItemLoad = false

    @Volatile
    private var lastPlayCommandTimeMs = 0L

    private val _ignoreWait = MutableStateFlow(false)
    val ignoreWait: StateFlow<Boolean> = _ignoreWait.asStateFlow()

    @Volatile
    private var callbacks: PlaybackCoreCallbacks? = null

    @Volatile
    private var lastScheduledCommand: SyncPlayPlaybackCommand? = null

    fun setCallbacks(cb: PlaybackCoreCallbacks) {
        callbacks = cb
    }

    fun clearCallbacks() {
        callbacks = null
    }

    fun setCurrentPlaylistItemId(id: String?) {
        currentPlaylistItemId = id
    }

    fun setPendingItemLoad(pending: Boolean) {
        pendingItemLoad = pending
    }

    fun setIgnoreWait(ignore: Boolean) {
        _ignoreWait.value = ignore
        scope.launch { controller.setIgnoreWait(ignore) }
    }

    fun applyCommand(cmd: SyncPlayPlaybackCommand) {
        scope.launch {
            if (isDuplicate(cmd)) {
                Log.d(TAG, "Duplicate command detected: ${cmd.command}")
                return@launch
            }

            lastCommand = cmd
            if (cmd.playlistItemId.isNotBlank()) {
                currentPlaylistItemId = cmd.playlistItemId
            }

            if (pendingItemLoad) {
                Log.d(TAG, "Command deferred: waiting for item to finish loading")
                return@launch
            }

            when (cmd.command) {
                "Unpause" -> scheduleUnpause(cmd)
                "Pause" -> schedulePause(cmd)
                "Stop" -> scheduleStop()
                "Seek" -> scheduleSeek(cmd)
            }
        }
    }

    private fun isDuplicate(cmd: SyncPlayPlaybackCommand): Boolean {
        val last = lastScheduledCommand ?: return false
        if (last.command != cmd.command) return false
        if (last.whenMs != cmd.whenMs) return false
        if (last.positionTicks != cmd.positionTicks) return false
        if (last.playlistItemId != cmd.playlistItemId) return false
        return true
    }

    private fun scheduleUnpause(cmd: SyncPlayPlaybackCommand) {
        scheduledCommandJob?.cancel()
        enableSyncJob?.cancel()
        stopSyncCorrection()
        lastScheduledCommand = cmd
        lastPlayCommandTimeMs = System.currentTimeMillis()

        val cb = callbacks ?: return
        val playAtLocalMs = timeSyncManager.toLocal(cmd.whenMs)
        val nowMs = System.currentTimeMillis()
        val waitMs = playAtLocalMs - nowMs

        cb.onSyncStateChanged(synced = false, syncing = false)

        if (waitMs > 50) {
            scheduledCommandJob = scope.launch {
                cb.localPause()
                val preSeekTicks = estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
                val preSeekMs = safePositionMs(preSeekTicks, cb.durationMs())
                cb.localSeek(preSeekMs)

                delay(waitMs)

                val finalTicks = estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
                val finalMs = safePositionMs(finalTicks, cb.durationMs())
                cb.localSeek(finalMs)
                cb.localPlay()
                cb.onSyncStateChanged(synced = true, syncing = false)
                scheduleEnableSync()
            }
        } else {
            if (cb.isPlaying() && Math.abs(cb.currentPositionMs() - safePositionMs(estimateCurrentTicks(cmd.positionTicks, cmd.whenMs), cb.durationMs())) < 500) {
                Log.d(TAG, "Unpause: already playing and within 500ms")
                cb.onSyncStateChanged(synced = true, syncing = false)
                scheduleEnableSync()
                return
            }
            val estimatedTicks = estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
            val estimatedMs = safePositionMs(estimatedTicks, cb.durationMs())
            cb.localSeek(estimatedMs)
            cb.localPlay()
            cb.onSyncStateChanged(synced = true, syncing = false)
            scheduleEnableSync()
        }
    }

    private fun schedulePause(cmd: SyncPlayPlaybackCommand) {
        scheduledCommandJob?.cancel()
        enableSyncJob?.cancel()
        stopSyncCorrection()
        lastScheduledCommand = cmd

        val cb = callbacks ?: return
        val pauseAtLocalMs = timeSyncManager.toLocal(cmd.whenMs)
        val nowMs = System.currentTimeMillis()
        val waitMs = pauseAtLocalMs - nowMs

        val posTicks = if (cmd.positionTicks > 0) {
            estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
        } else {
            cb.currentPositionMs() * 10_000
        }
        val posMs = safePositionMs(posTicks, cb.durationMs())

        if (waitMs > 50) {
            scheduledCommandJob = scope.launch {
                delay(waitMs)
                cb.localSeek(posMs)
                cb.localPause()
                cb.onSyncStateChanged(synced = true, syncing = false)
            }
        } else {
            cb.localSeek(posMs)
            cb.localPause()
            cb.onSyncStateChanged(synced = true, syncing = false)
        }
    }

    private fun scheduleStop() {
        scheduledCommandJob?.cancel()
        enableSyncJob?.cancel()
        stopSyncCorrection()
        lastCommand = null
        lastScheduledCommand = null

        val cb = callbacks ?: return
        cb.localPause()
        cb.onSyncStateChanged(synced = true, syncing = false)
    }

    private fun scheduleSeek(cmd: SyncPlayPlaybackCommand) {
        scheduledCommandJob?.cancel()
        enableSyncJob?.cancel()
        stopSyncCorrection()
        lastScheduledCommand = cmd

        val cb = callbacks ?: return
        val posTicks = estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
        val posMs = safePositionMs(posTicks, cb.durationMs())

        cb.localPause()
        cb.localSeek(posMs)
        cb.onSyncStateChanged(synced = true, syncing = false)

        scope.launch {
            controller.reportReady(
                positionTicks = posTicks,
                isPlaying = false,
                playlistItemId = currentPlaylistItemId,
                whenMs = timeSyncManager.remoteNow(),
            )
        }
    }

    fun onPlaybackStateChanged(state: Int) {
        scope.launch {
            val cb = callbacks ?: return@launch
            val posTicks: Long
            try {
                posTicks = cb.currentPositionMs() * 10_000
            } catch (_: Exception) {
                return@launch
            }

            when (state) {
                STATE_IDLE, STATE_BUFFERING -> {
                    if (state == STATE_BUFFERING) {
                        val timeSincePlayCmd = System.currentTimeMillis() - lastPlayCommandTimeMs
                        if (timeSincePlayCmd < 2000 && lastCommand?.command == "Unpause") {
                            Log.d(TAG, "BUFFERING suppressed (Play command ${timeSincePlayCmd}ms ago)")
                            return@launch
                        }
                    }
                    stopSyncCorrection()
                    scope.launch {
                        controller.reportBuffering(
                            positionTicks = posTicks,
                            isPlaying = cb.isPlaying(),
                            playlistItemId = currentPlaylistItemId,
                            whenMs = timeSyncManager.remoteNow(),
                        )
                    }
                }
                STATE_READY -> {
                    if (pendingItemLoad) {
                        pendingItemLoad = false
                        cb.localPause()
                        Log.d(TAG, "READY (item load): pausing and reporting ready")
                        scope.launch {
                            controller.reportReady(
                                positionTicks = posTicks,
                                isPlaying = false,
                                playlistItemId = currentPlaylistItemId,
                                whenMs = timeSyncManager.remoteNow(),
                            )
                        }
                        cb.onSyncStateChanged(synced = false, syncing = true)
                    } else {
                        scope.launch {
                            controller.reportReady(
                                positionTicks = posTicks,
                                isPlaying = cb.isPlaying(),
                                playlistItemId = currentPlaylistItemId,
                                whenMs = timeSyncManager.remoteNow(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun performSyncCorrection() {
        if (!syncEnabled) return
        if (!isSyncCorrectionWarranted()) return

        val cb = callbacks ?: return
        val cmd = lastCommand ?: return
        if (cmd.command != "Unpause") return

        val currentPosMs = cb.currentPositionMs()
        val currentPosTicks = currentPosMs * 10_000
        val serverPosTicks = estimateCurrentTicks(cmd.positionTicks, cmd.whenMs)
        val diffTicks = serverPosTicks - currentPosTicks
        val diffMs = diffTicks / 10_000.0
        val absDiffMs = Math.abs(diffMs)

        if (absDiffMs < MIN_DELAY_SPEED_TO_SYNC) return

        if (absDiffMs >= MIN_DELAY_SKIP_TO_SYNC) {
            val seekMs = safePositionMs(serverPosTicks, cb.durationMs())
            cb.localSeek(seekMs)
            cb.onSyncStateChanged(synced = false, syncing = true)
            scope.launch {
                delay(MAX_DELAY_SPEED_TO_SYNC.toLong() / 2)
                cb.onSyncStateChanged(synced = true, syncing = false)
            }
            Log.d(TAG, "SkipToSync: diff=${diffMs}ms")
        } else if (absDiffMs < MAX_DELAY_SPEED_TO_SYNC) {
            var speedToSyncTime = SPEED_TO_SYNC_DURATION
            if (diffMs <= -speedToSyncTime * MIN_SPEED) {
                speedToSyncTime = Math.abs(diffMs) / (1.0 - MIN_SPEED)
            }
            val speed = (1.0 + diffMs / speedToSyncTime).toFloat().coerceAtLeast(MIN_SPEED.toFloat())
            cb.setPlaybackRate(speed)
            cb.onSyncStateChanged(synced = false, syncing = true)
            speedToSyncJob?.cancel()
            speedToSyncJob = scope.launch {
                delay(speedToSyncTime.toLong())
                if (syncEnabled) {
                    cb.setPlaybackRate(1.0f)
                    cb.onSyncStateChanged(synced = true, syncing = false)
                }
            }
            Log.d(TAG, "SpeedToSync: diff=${diffMs}ms, speed=$speed")
        }
    }

    private fun isSyncCorrectionWarranted(): Boolean {
        val cb = callbacks ?: return false
        val lastReported = lastPlayCommandTimeMs
        val latest = cb.currentPositionMs()
        val delta = kotlin.math.abs(lastReported - latest).toDouble()
        val drift = delta
        return drift >= SYNC_CORRECTION_THRESHOLD_MS
    }

    private fun scheduleEnableSync() {
        enableSyncJob?.cancel()
        enableSyncJob = scope.launch {
            delay(ENABLE_SYNC_TIMEOUT)
            syncEnabled = true
            startSyncCorrection()
        }
    }

    private fun startSyncCorrection() {
        syncCorrectionJob?.cancel()
        syncCorrectionJob = scope.launch {
            delay(SYNC_CORRECTION_INITIAL_DELAY)
            while (syncEnabled) {
                delay(SYNC_CORRECTION_INTERVAL)
                try {
                    performSyncCorrection()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Log.w(TAG, "Sync correction error", e)
                }
            }
        }
    }

    private fun stopSyncCorrection() {
        syncEnabled = false
        syncCorrectionJob?.cancel()
        val cb = callbacks
        if (cb != null) {
            scope.launch { cb.setPlaybackRate(1.0f) }
        }
    }

    fun reset() {
        scheduledCommandJob?.cancel()
        enableSyncJob?.cancel()
        speedToSyncJob?.cancel()
        stopSyncCorrection()
        lastCommand = null
        lastScheduledCommand = null
        pendingItemLoad = false
        lastPlayCommandTimeMs = 0L
    }

    fun onGroupLeft() {
        reset()
    }

    private fun estimateCurrentTicks(ticks: Long, whenMs: Long): Long {
        val remoteNow = timeSyncManager.remoteNow()
        val elapsedMs = remoteNow - whenMs
        return ticks + elapsedMs * 10_000
    }

    private fun safePositionMs(ticks: Long, durationMs: Long): Long {
        val ms = ticks / 10_000
        return if (durationMs > 0) ms.coerceIn(0, durationMs) else ms.coerceAtLeast(0)
    }

    companion object {
        private const val TAG = "SyncPlayPlaybackCore"
        private const val STATE_IDLE = 1
        private const val STATE_BUFFERING = 2
        private const val STATE_READY = 3

        private const val MIN_DELAY_SPEED_TO_SYNC = 60.0
        private const val MAX_DELAY_SPEED_TO_SYNC = 3000.0
        private const val SPEED_TO_SYNC_DURATION = 1000.0
        private const val MIN_DELAY_SKIP_TO_SYNC = 400.0
        private const val ENABLE_SYNC_TIMEOUT = 1500L
        private const val SYNC_CORRECTION_INITIAL_DELAY = 500L
        private const val SYNC_CORRECTION_INTERVAL = 2000L
        private const val SYNC_CORRECTION_THRESHOLD_MS = 100L
        private const val MIN_SPEED = 0.2
    }
}
