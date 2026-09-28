package com.raulshma.jellyplay.desktop.discord

import com.raulshma.jellyplay.core.data.log.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The hand-rolled Discord Rich Presence IPC client (feature 4.2): connects
 * to the local Discord client over [DiscordIpcTransport], performs the
 * `v:1` [DiscordIpcOpCode.OP_HANDSHAKE], answers the server's PING probes,
 * publishes `SET_ACTIVITY` frames, and dispatches the `ACTIVITY_JOIN` event
 * to [onActivityJoin] (the Join button's secret — the SyncPlay deep link).
 *
 * Lifecycle contract (matching the desktop startup idiom):
 *  - [start] is idempotent and launches its loops on the injected [scope];
 *    every failure degrades to a log line, never a crash — Discord may not
 *    even be installed;
 *  - [setActivity] stores the desired payload and sends it when connected;
 *    a send while disconnected simply updates the desired state, and the
 *    next (re)connect publishes it after the READY dispatch — so pipe loss
 *    converges instead of dropping updates;
 *  - a lost pipe restarts the connect loop with exponential backoff
 *    ([BACKOFF_START_MS] doubling up to [BACKOFF_MAX_MS]).
 *
 * The transport arrives through [transportFactory] so tests can substitute
 * an in-memory pipe; the default opens Discord's real endpoints.
 */
internal class DiscordIpcClient(
    private val clientId: String,
    private val scope: CoroutineScope,
    /** The raw Join secret (`jellyplay://syncplay/{groupId}`) on a user's Join click. */
    private val onActivityJoin: (String) -> Unit,
    private val transportFactory: () -> DiscordIpcTransport? = DiscordIpcTransport::openFirstAvailable,
) {

    private val started = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)

    /** The desired activity payload (SET_ACTIVITY JSON); null = clear. */
    private val desiredPayload = AtomicReference<String?>(null)

    /** Guards the single write path (service collector + reader pongs). */
    private val writeMutex = Mutex()

    private var connectJob: Job? = null

    @Volatile
    private var transport: DiscordIpcTransport? = null

    /** Idempotent app-lifetime kickoff — the DesktopDownloadManager idiom. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        connectJob = scope.launch(Dispatchers.IO) { connectLoop() }
    }

    fun stop() {
        started.set(false)
        connectJob?.cancel()
        connectJob = null
        closeTransport()
    }

    /**
     * Publishes one `SET_ACTIVITY` payload frame (the caller builds it —
     * including the clear shape, an `activity: null` body). Safe from any
     * thread; sends only while connected, otherwise the payload becomes the
     * post-(re)connect desired state. The initial desired state is "never
     * published" ([desiredPayload] null) — an app that starts with nothing
     * playing publishes nothing.
     */
    suspend fun setActivity(payload: String) {
        desiredPayload.set(payload)
        val active = transport ?: return
        if (!connected.get()) return
        writeMutex.withLock {
            try {
                active.write(
                    DiscordIpcFrameCodec.encode(
                        DiscordIpcFrame(opCode = DiscordIpcOpCode.OP_FRAME, payload = payload)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "SET_ACTIVITY write failed — the pipe is gone; reconnecting", e)
                closeTransport()
            }
        }
    }

    /**
     * Connect → handshake → serve → (pipe loss) → back off → retry. Runs
     * until [stop] or the scope dies. The desired payload publishes right
     * after the handshake so a reconnect re-asserts the current activity.
     */
    private suspend fun connectLoop() {
        var backoffMs = BACKOFF_START_MS

        /** Waits the current backoff, then doubles it (capped) for the next miss. */
        suspend fun backOff() {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(BACKOFF_MAX_MS)
        }

        while (started.get() && scope.isActive) {
            val channel = transportFactory()
            if (channel == null) {
                // Discord not running — no connection to lose, retry quietly.
                backOff()
                continue
            }
            backoffMs = BACKOFF_START_MS
            transport = channel
            try {
                channel.write(
                    DiscordIpcFrameCodec.encode(
                        DiscordIpcFrame(
                            opCode = DiscordIpcOpCode.OP_HANDSHAKE,
                            payload = DiscordIpcFrameCodec.handshakePayload(clientId),
                        )
                    )
                )
                connected.set(true)
                Log.d(TAG, "Connected to Discord IPC; publishing desired activity")
                // Re-assert (or first-publish) the desired activity.
                desiredPayload.get()?.let { setActivity(it) }
                serve(channel)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Discord IPC pipe lost — reconnecting", e)
            } finally {
                connected.set(false)
                // Close only THIS loop's pipe: a stop/start race (the toggle
                // flapping) can have a newer loop already owning [transport]
                // — killing its connection from the cancelled loop's finally
                // would sever the fresh handshake mid-flight.
                if (transport === channel) {
                    closeTransport()
                } else {
                    runCatching { channel.close() }
                }
            }
            if (!started.get()) return
            backOff()
        }
    }

    /**
     * The blocking read loop: dispatch events, echo pings. Every decoded
     * frame is a JSON payload; only the two shapes this client cares about
     * are parsed — the PING (opcode, no JSON needed) and the ACTIVITY_JOIN
     * dispatch. Everything else is logged at debug and dropped.
     */
    private fun serve(channel: DiscordIpcTransport) {
        val header = ByteArray(DiscordIpcFrameCodec.HEADER_SIZE)
        while (started.get()) {
            if (readFully(channel, header) != header.size) return
            val head = DiscordIpcFrameCodec.decodeHeader(header) ?: return
            if (!DiscordIpcFrameCodec.isValidPayloadLength(head.payloadLength)) return
            val payload = ByteArray(head.payloadLength)
            if (head.payloadLength > 0 && readFully(channel, payload) != head.payloadLength) return

            when (head.opCode) {
                DiscordIpcOpCode.OP_PING -> {
                    Log.d(TAG, "Ping from Discord — ponging")
                    try {
                        channel.write(
                            DiscordIpcFrameCodec.encode(DiscordIpcFrame(DiscordIpcOpCode.OP_PONG, payload.decodeToString()))
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Pong write failed", e)
                        return
                    }
                }
                DiscordIpcOpCode.OP_CLOSE -> {
                    Log.d(TAG, "Discord closed the IPC pipe")
                    return
                }
                DiscordIpcOpCode.OP_FRAME -> dispatchFrame(payload.decodeToString())
                else -> Unit
            }
        }
    }

    /** Extracts the `ACTIVITY_JOIN` dispatch's `data.secret` and hands it to [onActivityJoin]. */
    private fun dispatchFrame(payload: String) {
        val secret = extractJoinSecret(payload) ?: return
        Log.d(TAG, "Discord ACTIVITY_JOIN received")
        runCatching { onActivityJoin(secret) }
            .onFailure { Log.w(TAG, "Join handler failed", it) }
    }

    private fun readFully(channel: DiscordIpcTransport, buffer: ByteArray): Int {
        var read = 0
        while (read < buffer.size) {
            val n = channel.read(buffer, read, buffer.size - read)
            if (n <= 0) return if (read == 0) -1 else read
            read += n
        }
        return read
    }

    private fun closeTransport() {
        connected.set(false)
        transport?.let { runCatching { it.close() } }
        transport = null
    }

    companion object {
        private const val TAG = "DiscordIpcClient"
        private const val BACKOFF_START_MS = 1_000L
        private const val BACKOFF_MAX_MS = 60_000L

        /**
         * The one external-data parse: `{"cmd":"DISPATCH","evt":"ACTIVITY_JOIN",
         * "data":{"secret":"…"}}` → the secret. Deliberately a tiny
         * string-level extraction (no JSON tree) — the shape is fixed by the
         * protocol and anything else is ignored.
         */
        internal fun extractJoinSecret(payload: String): String? {
            if (!payload.contains(ACTIVITY_JOIN_EVENT)) return null
            val secretKey = "\"secret\""
            val keyIndex = payload.indexOf(secretKey).takeIf { it >= 0 } ?: return null
            val openQuote = payload.indexOf('"', keyIndex + secretKey.length).takeIf { it >= 0 } ?: return null
            val closeQuote = payload.indexOf('"', openQuote + 1).takeIf { it >= 0 } ?: return null
            val secret = payload.substring(openQuote + 1, closeQuote)
            return secret.takeIf { it.isNotEmpty() }
        }

        private const val ACTIVITY_JOIN_EVENT = "\"ACTIVITY_JOIN\""
    }
}
