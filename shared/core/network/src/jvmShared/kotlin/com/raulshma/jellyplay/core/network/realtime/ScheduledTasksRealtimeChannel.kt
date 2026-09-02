package com.raulshma.jellyplay.core.network.realtime

import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.network.NetworkLog
import com.raulshma.jellyplay.core.network.api.toScheduledTaskInfo
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.websocket.WebSocketEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Realtime stream of Jellyfin scheduled tasks over the shared [JellyfinWebSocketClient].
 *
 * Jellyfin does NOT push scheduled-task updates by default — the client must send a
 * `ScheduledTasksInfoStart` message to begin the subscription, and the server then
 * pushes the *full* `TaskInfo[]` array at a fixed interval (default 1s) regardless
 * of whether values changed. This mirrors what the Jellyfin TS SDK's
 * `api.subscribe([ScheduledTasksInfo], cb)` does under the hood (it sends the
 * Start/Stop messages and re-subscribes on reconnect).
 *
 * This channel is ref-counted via [shareIn] + [SharingStarted.WhileSubscribed]:
 * the first collector sends `ScheduledTasksInfoStart`; the last collector leaving
 * (after a 5s grace window) sends `ScheduledTasksInfoStop`. The WS connection
 * itself is owned app-lifetime by `MainViewModel`; we only own the subscription
 * lifecycle.
 *
 * On any socket reconnect (false→true transition of [JellyfinWebSocketClient.isConnected])
 * the Start message is re-sent, because the server drops per-socket subscriptions
 * when the connection closes.
 */
class ScheduledTasksRealtimeChannel(
    private val webSocketClient: JellyfinWebSocketClient,
    private val scope: CoroutineScope,
) {
    /**
     * Wall time of the last successfully parsed ScheduledTasksInfo push
     * (0 = none this process). Freshness signal for [tasks] consumers: the
     * shared flow replays its last emission to every new collector, and that
     * replay can be arbitrarily old when the socket has since died — judge
     * recency from this stamp, not from collection time.
     */
    @Volatile var lastPushAtMs: Long = 0L
        private set

    private val rawTasks: Flow<List<ScheduledTaskInfo>> = callbackFlow {
        // Tracks (re)connection so we can re-subscribe after a socket drop.
        var lastConnected = webSocketClient.isConnected.value
        if (lastConnected) sendStart()

        val connectionJob: Job = scope.launch {
            webSocketClient.isConnected.collect { connected ->
                if (connected && !lastConnected) {
                    NetworkLog.d(TAG, "Socket reconnected, re-subscribing to ScheduledTasksInfo")
                    sendStart()
                }
                lastConnected = connected
            }
        }

        val eventsJob: Job = scope.launch {
            webSocketClient.events.collect { event ->
                if (event.type == MESSAGE_SCHEDULED_TASKS_INFO) {
                    // A malformed push is skipped entirely — no emission (the
                    // last good list stays) and no [lastPushAtMs] stamp (a
                    // push we cannot parse is not evidence of freshness).
                    val parsed = parseTasks(event) ?: return@collect
                    lastPushAtMs = System.currentTimeMillis()
                    trySend(parsed)
                }
            }
        }

        // If the socket wasn't connected when we started, wait for it then subscribe.
        if (!lastConnected) {
            scope.launch {
                // Suspend until the socket reports connected, then subscribe.
                webSocketClient.isConnected.first { it }
                sendStart()
            }
        }

        awaitClose {
            connectionJob.cancel()
            eventsJob.cancel()
            sendStop()
        }
    }
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = UNSUBSCRIBE_GRACE_MS),
            replay = 1,
        )

    /**
     * The full scheduled-task list as last pushed by the server. Emits on every
     * server push whose parsed contents differ from the previous emission.
     * Nothing until the first successfully parsed push lands; malformed
     * pushes are skipped so the last good list stays.
     */
    val tasks: Flow<List<ScheduledTaskInfo>> = rawTasks

    /**
     * Convenience: the "Scan media library" task (key `RefreshLibrary`),
     * or null when not present / not running. Consumed by the dashboard hero
     * scan-library button for live progress.
     */
    val scanLibraryTask: Flow<ScheduledTaskInfo?> = rawTasks
        .map { tasks -> tasks.firstOrNull { it.key == KEY_SCAN_LIBRARY } }

    private fun sendStart() {
        // Data format: "$initialDelayMs,$intervalMs" — 0ms initial, 1000ms cadence.
        webSocketClient.sendMessageWithDataString(
            MESSAGE_SCHEDULED_TASKS_START,
            DATA_SUBSCRIBE_1HZ,
        )
    }

    private fun sendStop() {
        webSocketClient.sendMessage(MESSAGE_SCHEDULED_TASKS_STOP)
    }

    private fun parseTasks(event: WebSocketEvent): List<ScheduledTaskInfo>? {
        val data = event.dataArray ?: return null
        return try {
            buildList {
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    add(obj.toScheduledTaskInfo())
                }
            }
        } catch (e: Exception) {
            NetworkLog.w(TAG, "Failed to parse ScheduledTasksInfo payload", e)
            null
        }
    }

    private companion object {
        private const val TAG = "ScheduledTasksRT"
        private const val MESSAGE_SCHEDULED_TASKS_INFO = "ScheduledTasksInfo"
        private const val MESSAGE_SCHEDULED_TASKS_START = "ScheduledTasksInfoStart"
        private const val MESSAGE_SCHEDULED_TASKS_STOP = "ScheduledTasksInfoStop"
        private const val DATA_SUBSCRIBE_1HZ = "0,1000"
        private const val UNSUBSCRIBE_GRACE_MS = 5_000L
        private const val KEY_SCAN_LIBRARY = "RefreshLibrary"
    }
}
