package com.raulshma.jellyplay.core.network.realtime

import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import org.json.JSONObject

/**
 * Realtime stream of `UserDataChanged` pushes over the shared
 * [JellyfinWebSocketClient].
 *
 * Unlike [ScheduledTasksRealtimeChannel], the server pushes these
 * automatically to the authenticated user's socket sessions — there is no
 * Start/Stop subscription, so there is also nothing to re-send on reconnect.
 * The WS connection itself is owned app-lifetime by `MainViewModel`; this
 * channel is a pure filter/map over the inbound event stream, ref-counted via
 * [shareIn] + [SharingStarted.WhileSubscribed] (5s grace, after which the
 * upstream collection stops until the next collector).
 *
 * Events are filtered to the engine's current user id — belt-and-braces,
 * since the server should only send this socket's user's changes, but a
 * mismatched push must never trigger another user's refresh. Malformed
 * payloads (missing `UserId` / bad `UserDataList`) are silently dropped.
 */
class UserDataRealtimeChannel(
    private val webSocketClient: JellyfinWebSocketClient,
    private val engine: JellyfinApiEngine,
    private val scope: CoroutineScope,
) {
    /**
     * Parsed user-data changes for the current user. replay = 0: a late
     * collector must not be shown a stale change.
     */
    val changes: Flow<UserDataChange> = webSocketClient.events
        .filter { it.type == MESSAGE_USER_DATA_CHANGED }
        .mapNotNull { event -> parseChange(event.data) }
        .filter { change ->
            val currentUserId = engine.currentUser.value?.id
            currentUserId != null && change.userId.equals(currentUserId, ignoreCase = true)
        }
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = UNSUBSCRIBE_GRACE_MS),
            replay = 0,
        )

    private fun parseChange(data: JSONObject): UserDataChange? {
        val userId = data.optString(KEY_USER_ID)
        if (userId.isBlank()) return null
        val list = data.optJSONArray(KEY_USER_DATA_LIST) ?: return null
        val itemIds = buildList {
            for (i in 0 until list.length()) {
                val id = list.optJSONObject(i)?.optString(KEY_ITEM_ID) ?: continue
                if (id.isNotBlank()) add(id)
            }
        }
        return UserDataChange(userId = userId, itemIds = itemIds)
    }

    private companion object {
        private const val MESSAGE_USER_DATA_CHANGED = "UserDataChanged"
        private const val KEY_USER_ID = "UserId"
        private const val KEY_USER_DATA_LIST = "UserDataList"
        private const val KEY_ITEM_ID = "ItemId"
        private const val UNSUBSCRIBE_GRACE_MS = 5_000L
    }
}
