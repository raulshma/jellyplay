package com.raulshma.jellyplay.core.data.worker

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

// V3 downloads conveyor: moved verbatim from the legacy :core:data shim's
// src/test (same package) alongside the DownloadTransferRunner it scripts.

/**
 * Test double for [DownloadTransferClient]. Scripts responses (or thrown
 * exceptions) per request in FIFO order, so a test can set up the exact
 * HTTP-status / body / failure sequence the transfer loop will see — the 416
 * recovery, for example, consumes two responses (the 416, then the 200 retry).
 *
 * Mirrors the `FakeMediaEngine` shape: implements the production interface,
 * records what was requested, and exposes the scripted outcomes. Lives in
 * `jvmTest` (same module as the runner — no cross-module test need).
 *
 * [MultiConnectionDownloadStrategyTest] drives the fake with N concurrent
 * chunk requests, whose arrival order is nondeterministic — so besides the
 * FIFO queue it accepts a [replyByRequest] replier keyed on the request
 * itself (the strategy's chunks are distinguishable by their `Range` header),
 * and a [Reply.ThrowAny] variant that can throw non-`IOException` throwables
 * (to exercise the strategies' failure-message overrides).
 */
class FakeDownloadTransferClient : DownloadTransferClient {

    /** A scripted reply to one [TransferRequest]. */
    sealed interface Reply {
        /** A normal response with [code], [body] bytes, and optional total size. */
        data class Status(
            val code: Int,
            val body: ByteArray = ByteArray(0),
            /**
             * Authoritative total size surfaced via [TransferResponse.totalSize].
             * For 206 this is the `/T` Content-Range value; for 200 the
             * Content-Length. Null = unknown (chunked/transcoded).
             */
            val totalSize: Long? = null,
        ) : Reply {
            override fun equals(other: Any?): Boolean =
                this === other || (other is Status && code == other.code &&
                    body.contentEquals(other.body) && totalSize == other.totalSize)
            override fun hashCode(): Int = arrayOf(code, body, totalSize).contentHashCode()
        }

        /** A response whose body is a pre-constructed stream (streaming/cancel scenarios). */
        data class Stream(
            val code: Int,
            val stream: InputStream,
            val totalSize: Long? = null,
        ) : Reply

        /** Throw this [exception] when the request is executed. */
        data class Throw(val exception: IOException) : Reply

        /**
         * Like [Throw] but for any [Throwable] — non-IO throwables are how a
         * test reaches the strategy-level generic-failure classification that
         * an `IOException` never produces.
         */
        data class ThrowAny(val exception: Throwable) : Reply
    }

    private val queue: ArrayDeque<Reply> = ArrayDeque()

    /**
     * Fallback replier consulted when the FIFO queue is empty. Keyed on the
     * request because concurrent callers (multi-connection chunks) have no
     * deterministic arrival order to script against.
     */
    private var fallback: ((TransferRequest) -> Reply)? = null

    // Concurrent chunk callers append from Dispatchers.IO threads — guard the
    // capture list (the FIFO queue itself is only raced in request-driven mode,
    // where it stays empty).
    val requests: MutableList<TransferRequest> =
        java.util.Collections.synchronizedList(mutableListOf<TransferRequest>())

    /** Enqueues one or more scripted replies (FIFO). */
    fun enqueue(vararg replies: Reply) {
        replies.forEach { queue.addLast(it) }
    }

    /** Scripts every request the FIFO queue doesn't cover via [replier]. */
    fun replyByRequest(replier: (TransferRequest) -> Reply) {
        fallback = replier
    }

    /** Convenience for enqueuing a simple 2xx body response. */
    fun enqueueOk(body: ByteArray, totalSize: Long? = body.size.toLong(), code: Int = 200) {
        enqueue(Reply.Status(code = code, body = body, totalSize = totalSize))
    }

    override suspend fun execute(request: TransferRequest): TransferResponse {
        requests += request
        return when (val reply = queue.removeFirstOrNull() ?: fallback?.invoke(request)
            ?: error("No scripted reply for $request")) {
            is Reply.Throw -> throw reply.exception
            is Reply.ThrowAny -> throw reply.exception
            is Reply.Status -> FakeResponse(reply.code, reply.totalSize) { ByteArrayInputStream(reply.body) }
            is Reply.Stream -> FakeResponse(reply.code, reply.totalSize) { reply.stream }
        }
    }

    /** Whether every enqueued reply has been consumed. */
    val isExhausted: Boolean get() = queue.isEmpty()

    private class FakeResponse(
        override val code: Int,
        override val totalSize: Long?,
        private val bodyProvider: () -> InputStream,
    ) : TransferResponse {
        private var bodyOpened = false
        private var closed = false

        override fun openBody(): InputStream {
            check(!closed) { "response already closed" }
            check(!bodyOpened) { "body already opened" }
            bodyOpened = true
            return bodyProvider()
        }

        override fun close() {
            closed = true
        }
    }
}
