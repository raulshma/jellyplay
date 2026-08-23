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

        /** Throw this [exception] when the request is executed. */
        data class Throw(val exception: IOException) : Reply
    }

    private val queue: ArrayDeque<Reply> = ArrayDeque()
    val requests: MutableList<TransferRequest> = mutableListOf()

    /** Enqueues one or more scripted replies (FIFO). */
    fun enqueue(vararg replies: Reply) {
        replies.forEach { queue.addLast(it) }
    }

    /** Convenience for enqueuing a simple 2xx body response. */
    fun enqueueOk(body: ByteArray, totalSize: Long? = body.size.toLong(), code: Int = 200) {
        enqueue(Reply.Status(code = code, body = body, totalSize = totalSize))
    }

    override suspend fun execute(request: TransferRequest): TransferResponse {
        requests += request
        return when (val reply = queue.removeFirstOrNull() ?: error("No scripted reply for $request")) {
            is Reply.Throw -> throw reply.exception
            is Reply.Status -> FakeResponse(reply)
        }
    }

    /** Whether every enqueued reply has been consumed. */
    val isExhausted: Boolean get() = queue.isEmpty()

    private class FakeResponse(private val reply: Reply.Status) : TransferResponse {
        override val code: Int = reply.code
        override val totalSize: Long? = reply.totalSize
        private var bodyOpened = false
        private var closed = false

        override fun openBody(): InputStream {
            check(!closed) { "response already closed" }
            check(!bodyOpened) { "body already opened" }
            bodyOpened = true
            return ByteArrayInputStream(reply.body)
        }

        override fun close() {
            closed = true
        }
    }
}
