package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.streaming.BandwidthMonitor

/**
 * The Android music ticker's bandwidth-estimation half — the sampling block
 * the position tick body previously inlined (the desktop ticker's declared
 * stopping point is the shared [AudioQueuePolicy] plan; it has no bandwidth
 * sampling). Extracted as a collaborator so the estimator's own invariants
 * live beside it instead of inside 20 lines of ticker body:
 *
 *  - ONE sample opportunity per ACTIVE tick (paused ticks never reach
 *    [onTick] — the ticker suppresses them), sampled every
 *    [SAMPLE_PERIOD_TICKS]-th call (20 × 250 ms = one sample per ~5 s of
 *    active playback).
 *  - The estimate is BITRATE-TIER-ASSUMED, not measured: the bytes for the
 *    buffered-position delta are derived from the current tier's
 *    `targetKbps` (`kbps × deltaMs / 8 / 1000`), and only a POSITIVE delta
 *    (and a positive derived byte count) becomes a sample — a stall or a
 *    buffer flush must not read as zero bandwidth.
 *  - [lastBufferedPositionMs] advances ONLY on sampled ticks (the historical
 *    shape: ticks between samples do not touch it), and a new
 *    [AudioBandwidthSampler] starts from zero, so the FIRST sample after a
 *    tracking start sees the whole buffered-so-far delta — both exactly as
 *    the inlined block behaved (its locals were reset per tracking start).
 *
 * Main-thread confined by contract (one caller: the manager's main-thread
 * tick body).
 */
internal class AudioBandwidthSampler(
    private val bandwidthMonitor: BandwidthMonitor,
    /** The current bitrate tier's assumed `targetKbps` (the estimate's basis). */
    private val assumedKbpsProvider: () -> Int,
) {
    private var sampleTick = 0
    private var lastBufferedPositionMs = 0L

    /** One active-tick sample opportunity — see the class KDoc for the cadence. */
    fun onTick(bufferedPositionMs: Long) {
        sampleTick++
        if (sampleTick < SAMPLE_PERIOD_TICKS) return
        sampleTick = 0
        val deltaMs = (bufferedPositionMs - lastBufferedPositionMs).coerceAtLeast(0L)
        lastBufferedPositionMs = bufferedPositionMs
        if (deltaMs <= 0L) return
        val assumedKbps = assumedKbpsProvider()
        val estimatedBytes = (assumedKbps.toLong() * deltaMs) / 8L / 1000L
        if (estimatedBytes > 0L) {
            bandwidthMonitor.addSample(estimatedBytes, deltaMs)
        }
    }

    private companion object {
        /** Sample period in active ticks (~5 s at the 250 ms music cadence). */
        const val SAMPLE_PERIOD_TICKS = 20
    }
}
