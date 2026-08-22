package com.raulshma.jellyplay.core.network.interceptor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import okio.buffer
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BandwidthInterceptor @Inject constructor() : Interceptor {

    private val _estimatedBandwidthKbps = MutableStateFlow(0.0)
    val estimatedBandwidthKbps: StateFlow<Double> = _estimatedBandwidthKbps.asStateFlow()

    private val samples = ConcurrentLinkedDeque<Sample>()
    private val maxSamples = 10
    private val lock = ReentrantLock()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isMediaStream = path.contains("/Audio/") || path.contains("/Videos/") || path.contains("/stream")
        if (!isMediaStream) {
            return chain.proceed(request)
        }

        val originalResponse = chain.proceed(request)
        val responseBody = originalResponse.body ?: return originalResponse

        return originalResponse.newBuilder()
            .body(CountingBody(responseBody) { bytesTransferred ->
                addSample(bytesTransferred, responseBody.contentLength())
            })
            .build()    }

    private fun addSample(bytesTransferred: Long, contentLength: Long) {
        if (bytesTransferred <= 0) return
        val now = System.nanoTime()
        if (!lock.tryLock()) return
        try {
            samples.addLast(Sample(bytesTransferred, now))
            while (samples.size > maxSamples) {
                samples.removeFirst()
            }
            if (samples.size >= 2) {
                val totalBytes = samples.sumOf { it.bytes }
                val elapsedNs = samples.last().timestampNs - samples.first().timestampNs
                if (elapsedNs > 0) {
                    val elapsedSec = elapsedNs / 1_000_000_000.0
                    val kbps = (totalBytes * 8.0) / elapsedSec / 1000.0
                    val current = _estimatedBandwidthKbps.value
                    if (current == 0.0 || kotlin.math.abs(kbps - current) / current > 0.05) {
                        _estimatedBandwidthKbps.value = kbps
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private data class Sample(val bytes: Long, val timestampNs: Long)

    private class CountingBody(
        private val delegate: okhttp3.ResponseBody,
        private val onComplete: (Long) -> Unit,
    ) : okhttp3.ResponseBody() {
        override fun contentType(): okhttp3.MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()

        override fun source(): okio.BufferedSource {
            val raw = delegate.source()
            val forwarding = object : okio.ForwardingSource(raw) {
                private var totalBytesRead = 0L
                private var lastSampleBytes = 0L
                private val sampleIntervalBytes = 256L * 1024L

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    if (bytesRead != -1L) {
                        totalBytesRead += bytesRead
                    }
                    if (bytesRead == -1L || totalBytesRead - lastSampleBytes >= sampleIntervalBytes) {
                        // Report the delta since the previous sample, not the
                        // cumulative total. addSample sums per-sample byte
                        // counts; passing cumulative values would double-count
                        // and over-estimate throughput by ~Nx.
                        onComplete(totalBytesRead - lastSampleBytes)
                        lastSampleBytes = totalBytesRead
                    }
                    return bytesRead
                }
            }
            return forwarding.buffer()
        }

        override fun close() {
            delegate.close()
        }
    }
}
