package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.network.WebSocketBackoffPolicy
import kotlinx.coroutines.delay

/**
 * The reconnect loop state both plugin SSE streams share (the events stream in
 * [JellyPlayEventsRepository], the settings re-sync stream in
 * [JellyPlayLiveResyncConnector]): [WebSocketBackoffPolicy] — the module's ONE
 * reconnect-backoff law — carried over to SSE with unbounded attempts (the
 * streams never leave the fast schedule; the inbox/messages surfaces are the
 * durability half) and a 60s ceiling. [onConnected] resets the schedule on the
 * first frame a (re)connection delivers; [awaitRetryDelay] suspends out the
 * current step and arms the next.
 */
internal class SseReconnectLoop {

    private val backoff = WebSocketBackoffPolicy(
        maxAttempts = Int.MAX_VALUE,
        maxDelayMs = MAX_DELAY_MILLIS,
        shiftCap = SHIFT_CAP,
    )

    private var attempt = 1

    /** A frame landed — the connection is up; the next drop waits the base step. */
    fun onConnected() {
        attempt = 1
    }

    /** Suspends the current backoff step, then arms the next (doubling, capped). */
    suspend fun awaitRetryDelay() {
        // delayMs is non-null by construction (attempts unbounded); the
        // coalescing 0 keeps the call total.
        delay(backoff.delayMs(attempt) ?: 0L)
        attempt++
    }

    private companion object {
        /** The SSE schedule's ceiling (the WS law's 30s default is the socket lane's). */
        const val MAX_DELAY_MILLIS = 60_000L

        /**
         * `2^6 * 1s` = 64s → the ceiling binds from the 7th attempt; the WS
         * law's default cap of 4 would plateau at 16s.
         */
        const val SHIFT_CAP = 6
    }
}
