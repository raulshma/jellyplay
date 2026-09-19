package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong

class TimeSyncManager constructor(
    private val apiClient: JellyfinApiClient
) {
    private val offsetMs = AtomicLong(0)
    private val lastPingMs = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSyncing = java.util.concurrent.atomic.AtomicBoolean(false)
    private var syncJob: Job? = null
    private var forceUpdateJob: Job? = null

    private val measurements = ArrayDeque<Measurement>(maxCapacity + 1)
    private val _pingUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pingUpdated: SharedFlow<Unit> = _pingUpdated.asSharedFlow()

    private data class Measurement(
        val offset: Long,
        val delay: Long,
    )

    fun start() {
        if (!isSyncing.compareAndSet(false, true)) return
        syncJob?.cancel()
        syncJob = scope.launch {
            repeat(3) {
                sync()
                delay(1000)
            }
            _pingUpdated.tryEmit(Unit)
            while (isSyncing.get()) {
                delay(120_000)
                sync()
                _pingUpdated.tryEmit(Unit)
            }
        }
    }

    fun stop() {
        isSyncing.set(false)
        syncJob?.cancel()
        syncJob = null
        forceUpdateJob?.cancel()
        forceUpdateJob = null
        synchronized(measurements) {
            measurements.clear()
        }
    }

    fun forceUpdate() {
        forceUpdateJob?.cancel()
        forceUpdateJob = scope.launch {
            repeat(3) {
                sync()
                delay(500)
            }
            _pingUpdated.tryEmit(Unit)
        }
    }

    suspend fun sync() {
        try {
            val requestSent = System.currentTimeMillis()
            val result = apiClient.getServerTime()
            val responseReceived = System.currentTimeMillis()

            result.onSuccess { response ->
                val requestReceived = parseIso(response.requestReceptionTime)
                val responseSent = parseIso(response.responseTransmissionTime)

                val delay = responseReceived - requestSent
                val offset = ((requestReceived - requestSent) + (responseSent - responseReceived)) / 2

                synchronized(measurements) {
                    measurements.addLast(Measurement(offset, delay))
                    while (measurements.size > maxCapacity) {
                        measurements.removeFirst()
                    }

                    val best = measurements.minByOrNull { it.delay }
                    if (best != null) {
                        offsetMs.set(best.offset)
                        lastPingMs.set(best.delay / 2)
                    }
                }

                Log.d(TAG, "Time sync: offset=${offsetMs.get()}ms, ping=${lastPingMs.get()}ms, delay=${delay}ms")
            }.onFailure { error ->
                // Surface the failure so a silent 401 (expired token) or
                // persistent network error doesn't leave SyncPlay operating
                // with a stale offset forever. Previously this path was
                // invisible — only onSuccess logged.
                Log.w(TAG, "Time sync API failed", error)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            // Never swallow cancellation — breaks structured concurrency.
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Time sync failed", e)
        }
    }

    fun toRemote(localMs: Long): Long = localMs + offsetMs.get()
    fun toLocal(remoteMs: Long): Long = remoteMs - offsetMs.get()
    fun remoteNow(): Long = toRemote(System.currentTimeMillis())
    fun getPingMs(): Long = lastPingMs.get()

    /**
     * Projects a server-stamped playback position to the current moment: the
     * ticks recorded at remote wall-clock [whenMs], advanced by the remote time
     * elapsed since. Single home for the SyncPlay clock projection every core
     * and the bridge need; no clamping against duration — callers clamp where
     * the target surface requires it.
     */
    fun estimateCurrentTicks(positionTicks: Long, whenMs: Long): Long =
        projectCurrentTicks(positionTicks, remoteNow() - whenMs)

    private fun parseIso(iso: String): Long = parseIsoTimestamp(iso) ?: System.currentTimeMillis()

    companion object {
        private const val TAG = "TimeSyncManager"
        private const val maxCapacity = 8

        /** A Jellyfin tick is 100ns, so one millisecond of position is 10 000 ticks. */
        private const val TICKS_PER_MILLISECOND = 10_000L

        /** Whole-millisecond view of a ticks value — truncates sub-ms ticks toward zero. */
        fun ticksToMs(ticks: Long): Long = ticks / TICKS_PER_MILLISECOND

        /** Ticks view of a whole-millisecond duration — exact (10 000 ticks per ms). */
        fun msToTicks(ms: Long): Long = ms * TICKS_PER_MILLISECOND

        /**
         * Pure core of [estimateCurrentTicks]: advances [positionTicks] by
         * [elapsedMs] of playback. Consumers that hold a (possibly faked)
         * [TimeSyncManager] call this with the value read from its
         * [remoteNow][TimeSyncManager.remoteNow] seam rather than the instance
         * member, so a stubbed `remoteNow` still drives the projection — the
         * mock-based manager/core suites pin exactly that path.
         */
        fun projectCurrentTicks(positionTicks: Long, elapsedMs: Long): Long =
            positionTicks + msToTicks(elapsedMs)

        /**
         * Parses a Jellyfin server ISO-8601 timestamp: `Instant` form first,
         * `OffsetDateTime` fallback (the server has emitted both shapes).
         * Returns null for blank or unparseable input — the two former
         * hand-rolled ladders disagreed on the fallback ([TimeSyncManager]'s
         * `parseIso` substituted "now"; [SyncPlayEventHandler]'s
         * `parseTimestamp` degraded to 0 so a bad stamp never fails the
         * event), so the fallback stays caller-side by design.
         */
        fun parseIsoTimestamp(iso: String): Long? {
            if (iso.isBlank()) return null
            return try {
                Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {
                try {
                    OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
