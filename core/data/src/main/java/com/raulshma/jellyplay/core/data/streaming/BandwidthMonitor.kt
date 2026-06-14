package com.raulshma.jellyplay.core.data.streaming

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Tracks rolling bandwidth measurements and provides the current best
 * estimate. Measurements are added via [addSample]; consumers can observe
 * [estimatedBandwidthKbps] to react to changes (e.g. for adaptive quality
 * selection).
 */
@Singleton
class BandwidthMonitor @Inject constructor() {
    private val samples = ArrayDeque<BandwidthSample>()
    private val maxSamples = 10

    private val _estimatedBandwidthKbps = MutableStateFlow(0.0)
    val estimatedBandwidthKbps: StateFlow<Double> = _estimatedBandwidthKbps.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private val _totalElapsedMs = MutableStateFlow(0L)
    val totalElapsedMs: StateFlow<Long> = _totalElapsedMs.asStateFlow()

    fun addSample(bytesTransferred: Long, elapsedMs: Long) {
        if (bytesTransferred <= 0L || elapsedMs <= 0L) return
        synchronized(samples) {
            samples.addLast(BandwidthSample(bytesTransferred, elapsedMs))
            while (samples.size > maxSamples) {
                val removed = samples.removeFirst()
                _totalBytes.value = (_totalBytes.value - removed.bytes).coerceAtLeast(0L)
                _totalElapsedMs.value = (_totalElapsedMs.value - removed.elapsedMs).coerceAtLeast(0L)
            }
            _totalBytes.value = _totalBytes.value + bytesTransferred
            _totalElapsedMs.value = _totalElapsedMs.value + elapsedMs
            _estimatedBandwidthKbps.value = computeAverageKbpsInternal()
        }
    }

    fun reset() {
        synchronized(samples) { samples.clear() }
        _totalBytes.value = 0L
        _totalElapsedMs.value = 0L
        _estimatedBandwidthKbps.value = 0.0
    }

    private fun computeAverageKbpsInternal(): Double {
        if (samples.isEmpty()) return 0.0
        val totalBytes = samples.sumOf { it.bytes }
        val totalMs = samples.sumOf { it.elapsedMs }
        if (totalMs == 0L) return 0.0
        return (totalBytes * 8.0) / (totalMs / 1000.0) / 1000.0
    }

    fun computeAverageKbps(): Double = synchronized(samples) { computeAverageKbpsInternal() }
}

data class BandwidthSample(
    val bytes: Long,
    val elapsedMs: Long,
)
