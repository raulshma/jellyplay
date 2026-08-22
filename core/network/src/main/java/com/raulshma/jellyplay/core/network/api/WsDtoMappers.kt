package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogEntry
import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import com.raulshma.jellyplay.core.model.TaskExecutionInfo
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.core.model.TaskTriggerInfo

// C3 note: these org.json WebSocket-payload parsers used to live in
// JellyfinDtoMappers.kt. They stayed in the legacy Android shim because their
// consumers — the realtime channels — inject the Hilt-qualified
// @ApplicationScope CoroutineScope whose annotation + provider live in the
// legacy :core:datastore shim, so the channels (and thus this parsing) could
// not move into :shared:core:network. org.json is an android.jar type here.

/**
 * Parses a PascalCase [org.json.JSONObject] `TaskInfo` from the Jellyfin
 * WebSocket `ScheduledTasksInfo` push payload into the app's
 * [ScheduledTaskInfo] model. The server emits the full task list every push
 * (see ScheduledTasksRealtimeChannel), and the JSON casing differs from the
 * SDK's camelCase DTOs, so a dedicated parser is simpler than coercing the SDK
 * deserializer onto a raw WS string.
 *
 * Reads every field the admin UI displays: identity (Id/Key/Name), runtime
 * state (State/CurrentProgressPercentage), metadata for grouping/display
 * (Category/Description), schedule triggers, and last-execution history.
 * PascalCase field names match the Jellyfin server's `TaskInfo` JSON contract.
 */
internal fun org.json.JSONObject.toScheduledTaskInfo(): ScheduledTaskInfo {
    val stateStr = optString("State")
    val state = when (stateStr) {
        "Running" -> TaskState.RUNNING
        "Cancelling" -> TaskState.CANCELLING
        else -> TaskState.IDLE
    }
    // currentProgressPercentage may be absent or null when the server cannot
    // report a concrete value (most of a library scan).
    val progress = if (has("CurrentProgressPercentage") && !isNull("CurrentProgressPercentage")) {
        optDouble("CurrentProgressPercentage", Double.NaN).takeIf { it.isFinite() }
    } else {
        null
    }
    // Category drives the section grouping on the Scheduled Tasks screen —
    // match jellyfin-web's getCategories(): empty/blank is treated as absent.
    val category = optString("Category").takeIf { it.isNotBlank() }
    val description = optString("Description").takeIf { it.isNotBlank() }
    val triggers = optTriggers()
    val lastExecutionResult = optJSONObject("LastExecutionResult")?.toExecutionModel()
    return ScheduledTaskInfo(
        id = optString("Id"),
        key = optString("Key"),
        name = optString("Name"),
        state = state,
        isHidden = optBoolean("Hidden", false),
        isEnabled = true,
        currentProgressPercentage = progress,
        category = category,
        description = description,
        triggers = triggers,
        lastExecutionResult = lastExecutionResult,
    )
}

private fun org.json.JSONObject.optTriggers(): List<TaskTriggerInfo> {
    val arr = optJSONArray("Triggers") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            add(
                TaskTriggerInfo(
                    type = obj.optString("Type"),
                    timeOfDayTicks = obj.optLongOrNull("TimeOfDayTicks"),
                    intervalTicks = obj.optLongOrNull("IntervalTicks"),
                    dayOfWeek = obj.optString("DayOfWeek").takeIf { it.isNotBlank() },
                    maxRuntimeTicks = obj.optLongOrNull("MaxRuntimeMs"),
                ),
            )
        }
    }
}

private fun org.json.JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    // optLong returns 0 on parse failure; use the Number coercion path so we
    // can distinguish a genuine 0 from a missing/garbage value.
    return when (val num = opt(key)) {
        is Number -> num.toLong()
        is String -> num.toLongOrNull()
        else -> null
    }
}

/**
 * Parses a PascalCase `TaskResult` (the `LastExecutionResult` field of a WS
 * TaskInfo) into [TaskExecutionInfo]. The server sends ISO-8601 timestamps
 * directly as strings (unlike the SDK, which strips the offset), so they are
 * passed through verbatim.
 */
private fun org.json.JSONObject.toExecutionModel(): TaskExecutionInfo = TaskExecutionInfo(
    name = optString("Name"),
    key = optString("Key"),
    startTimeUtc = optString("StartTimeUtc").takeIf { it.isNotBlank() },
    endTimeUtc = optString("EndTimeUtc").takeIf { it.isNotBlank() },
    status = optString("Status").ifBlank { "Success" },
    errorMessage = optString("ErrorMessage").takeIf { it.isNotBlank() },
)

/**
 * Parses the `MessageData` object of a WebSocket `ActivityLogEntry` push.
 * PascalCase field names match the Jellyfin server's activity-log JSON
 * contract; the server's `Severity` strings map onto [ActivityLogSeverity]
 * (unknown values degrade to INFORMATION, matching the REST mapper).
 */
internal fun org.json.JSONObject.toActivityLogEntry(): ActivityLogEntry = ActivityLogEntry(
    id = optLong("Id"),
    name = optString("Name"),
    type = optString("Type"),
    userId = optString("UserId").takeIf { it.isNotBlank() },
    overview = optString("Overview").takeIf { it.isNotBlank() },
    shortOverview = optString("ShortOverview").takeIf { it.isNotBlank() },
    itemId = optString("ItemId").takeIf { it.isNotBlank() },
    date = optString("Date"),
    severity = when (optString("Severity")) {
        "Trace" -> ActivityLogSeverity.TRACE
        "Debug" -> ActivityLogSeverity.DEBUG
        "Warn", "Warning" -> ActivityLogSeverity.WARNING
        "Error" -> ActivityLogSeverity.ERROR
        "Fatal", "Critical" -> ActivityLogSeverity.FATAL
        else -> ActivityLogSeverity.INFORMATION
    },
)
