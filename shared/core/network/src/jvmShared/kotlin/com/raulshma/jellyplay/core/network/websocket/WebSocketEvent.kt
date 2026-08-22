package com.raulshma.jellyplay.core.network.websocket

import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry in the inbound Jellyfin WebSocket message stream.
 *
 * C3 note: this type lives in jvmShared (not commonMain) because its payload
 * fields are org.json types consumed AS SUCH by legacy :core:data
 * (RemoteControlReceiver / SyncPlayManager / SyncPlayEventHandler read
 * `event.data` as a JSONObject) — those files must keep compiling unchanged,
 * and org.json cannot appear in a wasm-compiled commonMain source set.
 *
 * @param type raw Jellyfin `MessageType` string (e.g. `"ScheduledTasksInfo"`, `"Sessions"`)
 * @param data the `Data` payload as a [JSONObject] for object-payload message types. Empty
 *   when the payload is not an object — array payloads are exposed via [dataArray], and
 *   primitive payloads (`ForceKeepAlive.Data` is a number) never reach consumers.
 * @param dataArray the `Data` payload as a [JSONArray] for array-payload message types
 *   (`Sessions` = `SessionInfo[]`, `ScheduledTasksInfo` = `TaskInfo[]`), else null.
 *   Consumers of those types must read this field instead of [data].
 */
data class WebSocketEvent(
    val type: String,
    val data: JSONObject,
    val dataArray: JSONArray? = null,
    val rawText: String,
)
