package com.raulshma.jellyplay.core.data.streaming

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The connection-speed probe behind the settings screen's "Test connection
 * speed" action: measures the achievable DOWNLINK throughput by pulling a
 * bounded payload from Cloudflare's public speed endpoint and converts the
 * two samples' best result into a [StreamingQuality] tier.
 *
 * Shape: the HTTP transfer itself sits behind the [SpeedSampleExecutor]
 * seam — the production executor rides the Koin-shared download
 * [OkHttpClient] ([SpeedSampleExecutors.okHttp]); tests inject a fake, and
 * the tier mapping ([proposedQualityTier]) is pure and pinned without any
 * I/O. Two samples are taken and the FASTER one wins (a slow first sample
 * is dominated by connection ramp-up / TLS handshake, so max-of-two reads
 * as the honest achievable rate); when both samples fail (offline, blocked
 * endpoint, timeout) the probe yields `null` and the UI reports the failure.
 */
class ConnectionSpeedTester(
    private val executor: SpeedSampleExecutor,
) {

    /**
     * One bounded download: read up to [maxBytes] within [deadlineMs], report
     * the bytes actually received and the wall time the transfer took, or
     * `null` on any failure. Implementations must not throw on transport
     * failures — the failure IS the null return.
     */
    fun interface SpeedSampleExecutor {
        suspend fun sample(url: String, maxBytes: Long, deadlineMs: Long): SpeedSample?
    }

    /** One completed sample: [bytes] received over [elapsedMs] of wall time. */
    data class SpeedSample(val bytes: Long, val elapsedMs: Long)

    /**
     * Runs the probe: two [PAYLOAD_BYTES] downloads against
     * [SPEED_TEST_URL] with a hard [SAMPLE_DEADLINE_MS] per sample, the
     * faster sample converted to Mbps. `null` when no sample completed.
     */
    suspend fun measureMbps(): Float? {
        val samples = coroutineScope {
            listOf(
                async { runSample() },
                async { runSample() },
            ).awaitAll()
        }.filterNotNull()
        // max-of-two: a slow first sample is dominated by connection ramp-up,
        // so the FASTER one reads as the achievable rate. Empty (both failed)
        // folds to null — the probe's failure contract.
        return samples.maxOrNull()
    }

    /** One deadline-guarded sample converted to Mbps (NaN/negative guards → null). */
    private suspend fun runSample(): Float? {
        val sample = withTimeoutOrNull(SAMPLE_DEADLINE_MS) {
            runCatchingRethrowingCancellation {
                executor.sample(SPEED_TEST_URL, PAYLOAD_BYTES, SAMPLE_DEADLINE_MS)
            }.getOrNull()
        } ?: return null
        if (sample.elapsedMs <= 0L || sample.bytes <= 0L) return null
        val mbps = sample.bytes * 8.0 / (sample.elapsedMs / 1000.0) / 1_000_000.0
        return mbps.takeIf { it.isFinite() && it > 0.0 }?.toFloat()
    }

    companion object {
        /** Cloudflare's public speed-test download endpoint (no auth, no cache key). */
        const val SPEED_TEST_URL: String = "https://speed.cloudflare.com/__down?bytes=8000000"

        /** The bounded payload per sample — enough to saturate common home links in ~1 s. */
        const val PAYLOAD_BYTES: Long = 8_000_000L

        /** The hard per-sample deadline — a stalled probe must not hang the settings screen. */
        const val SAMPLE_DEADLINE_MS: Long = 12_000L

        /**
         * The measured-Mbps → quality-tier mapping (pure; jvmTest-pinned):
         * below 2 Mbps the 360p tier, then 480p/720p/1080p at 5/10/20, and
         * anything 20+ is 4K-class (the enum has no 1440p rung — the UHD_4K
         * tier is its ceiling).
         */
        fun proposedQualityTier(mbps: Float): StreamingQuality = when {
            mbps < 2f -> StreamingQuality.LOW_360P
            mbps < 5f -> StreamingQuality.SD_480P
            mbps < 10f -> StreamingQuality.HD_720P
            mbps < 20f -> StreamingQuality.FHD_1080P
            else -> StreamingQuality.UHD_4K
        }
    }
}

/**
 * The production [ConnectionSpeedTester.SpeedSampleExecutor]: one streaming
 * GET over the shared download client, reading up to [maxBytes] from the
 * response body. The Koin binding lives beside the AdaptiveBitrateManager
 * single ([com.raulshma.jellyplay.core.data.di.DataSessionPlaybackKoinModule]).
 */
object SpeedSampleExecutors {

    fun okHttp(client: OkHttpClient): ConnectionSpeedTester.SpeedSampleExecutor =
        ConnectionSpeedTester.SpeedSampleExecutor { url, maxBytes, _ ->
            // The probe deadline is enforced by the caller's withTimeoutOrNull;
            // the client's own timeouts stay as the transport-level backstop.
            sampleOnce(client, url, maxBytes)
        }

    private suspend fun sampleOnce(
        client: OkHttpClient,
        url: String,
        maxBytes: Long,
    ): ConnectionSpeedTester.SpeedSample? = runCatchingRethrowingCancellation {
        client.newCall(
            Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache, no-store")
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            val startedAt = System.nanoTime()
            val source = body.source()
            var read = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            while (read < maxBytes) {
                val toRead = minOf(buffer.size.toLong(), maxBytes - read).toInt()
                val n = source.inputStream().read(buffer, 0, toRead)
                if (n < 0) break
                read += n
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            if (read == 0L) return@use null
            ConnectionSpeedTester.SpeedSample(
                bytes = read,
                elapsedMs = elapsedMs.coerceAtLeast(1L),
            )
        }
    }.getOrNull()

    private const val BUFFER_SIZE = 64 * 1024
}
