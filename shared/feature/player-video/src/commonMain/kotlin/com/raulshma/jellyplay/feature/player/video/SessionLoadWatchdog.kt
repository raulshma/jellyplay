package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.monotonicNowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Diagnostics and stage latency tracking for session media loads.
 * Tracks monotonic duration spent in each progressive loading stage and
 * runs progressive watchdog checks to detect slow network, server lag,
 * or decoder stalls before hard timeout.
 */
internal class SessionLoadWatchdog(
    private val scope: CoroutineScope,
    private val onWarning: (warningMessage: String) -> Unit = {},
    private val onTimeout: (stageName: String) -> Unit = {},
    /**
     * The stage clock. Injectable so the virtual-time tests can assert exact
     * latency values; production reads the platform monotonic clock.
     */
    private val nowMs: () -> Long = { monotonicNowMillis() },
) {
    private val _stageLatencies = LinkedHashMap<String, Long>()
    private var currentStageStartedAtMs: Long = 0L
    private var currentStageName: String = ""
    private var watchdogJob: Job? = null
    private var isCancelled = false

    val stageLatencies: Map<String, Long> get() = _stageLatencies.toMap()

    fun start(initialStage: String) {
        isCancelled = false
        watchdogJob?.cancel()
        _stageLatencies.clear()
        currentStageStartedAtMs = nowMs()
        currentStageName = initialStage
        armStageWatchdog(initialStage)
    }

    fun recordStage(newStageName: String) {
        if (isCancelled) return
        val now = nowMs()
        if (currentStageName.isNotEmpty() && currentStageStartedAtMs > 0L) {
            accumulateStageLatency(currentStageName, now - currentStageStartedAtMs)
        }
        currentStageName = newStageName
        currentStageStartedAtMs = now
        armStageWatchdog(newStageName)
    }

    fun complete() {
        // Idempotent: the settle sites (onEngineReady, the external-player
        // arms) may race a [cancel] from failLoad — a second completion must
        // not re-write (and double-count) the final stage's latency.
        if (isCancelled) return
        isCancelled = true
        val now = nowMs()
        if (currentStageName.isNotEmpty() && currentStageStartedAtMs > 0L) {
            accumulateStageLatency(currentStageName, now - currentStageStartedAtMs)
        }
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun cancel() {
        isCancelled = true
        watchdogJob?.cancel()
        watchdogJob = null
    }

    /**
     * Kills the pending stage schedule WITHOUT ending the load's latency
     * tracking (unlike [cancel], the next [recordStage] re-arms normally).
     * The engine-replacement window ([PlayerSessionManager.createReplacementEngine])
     * uses this before its first suspension so the OUTGOING load's deadline
     * can neither land [LoadPhase.Failed] mid-swap nor stop() the replacement
     * engine; the replacement's fresh PrimingBuffer ladder arms at the
     * reloadWithEngine tail.
     */
    fun cancelSchedule() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun snapshotLatencies(): Map<String, Long> = _stageLatencies.toMap()

    /**
     * The ONE latency write: a stage visited repeatedly across engine
     * replacements (the mid-priming reloadWithEngine re-enters PrimingBuffer)
     * ACCUMULATES its completed durations instead of overwriting the prior
     * visit; a single visit keeps the plain elapsed value.
     */
    private fun accumulateStageLatency(stageName: String, elapsedMs: Long) {
        _stageLatencies[stageName] = (_stageLatencies[stageName] ?: 0L) + elapsedMs
    }

    private fun armStageWatchdog(stageName: String) {
        watchdogJob?.cancel()
        if (isCancelled) return
        watchdogJob = scope.launch {
            when (stageName) {
                "FetchingMetadata" -> {
                    delay(STAGE_METADATA_WARN_MS)
                    if (!isActive || isCancelled) return@launch
                    onWarning("Contacting server (network slow)...")
                }
                "ResolvingStream" -> {
                    delay(STAGE_RESOLVE_WARN_MS)
                    if (!isActive || isCancelled) return@launch
                    onWarning("Resolving stream with server (taking longer than expected)...")
                }
                "PrimingBuffer" -> {
                    delay(BUFFER_PRIMING_WARN_MS)
                    if (!isActive || isCancelled) return@launch
                    onWarning("Priming player buffer (slow network)...")
                    delay(BUFFER_PRIMING_PROMPT_MS - BUFFER_PRIMING_WARN_MS)
                    if (!isActive || isCancelled) return@launch
                    onWarning("Playback taking longer to start than usual.")
                    delay(STAGE_TOTAL_TIMEOUT_MS - BUFFER_PRIMING_PROMPT_MS)
                    if (!isActive || isCancelled) return@launch
                    onTimeout(stageName)
                }
                else -> {
                    // Local setup stages like "Preparing" do not perform network IO
                }
            }
        }
    }

    companion object {
        const val STAGE_METADATA_WARN_MS = 1_500L
        const val STAGE_RESOLVE_WARN_MS = 2_000L
        const val BUFFER_PRIMING_WARN_MS = 3_000L
        const val BUFFER_PRIMING_PROMPT_MS = 8_000L
        const val STAGE_TOTAL_TIMEOUT_MS = 20_000L
    }
}
