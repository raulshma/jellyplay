package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActivityLogSeverity
import com.raulshma.jellyplay.core.model.TaskState
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the org.json WebSocket-payload parsers of WsDtoMappers.kt against the
 * Jellyfin server's PascalCase push JSON contract (`ScheduledTasksInfo` and
 * `ActivityLogEntry` messages). The payloads are authoritative server shapes —
 * these specimens also pin the tolerance contract: absent, null, blank, and
 * unknown fields must degrade to model defaults instead of throwing, because
 * the socket delivers every server version's payload.
 */
class WsDtoMappersTest {

    // ----- toScheduledTaskInfo -----

    @Test
    fun `full task payload maps every admin-UI field`() {
        val task = JSONObject(
            """
            {
              "Id": "task-1",
              "Key": "ScanLibrary",
              "Name": "Scan media library",
              "State": "Running",
              "Hidden": true,
              "CurrentProgressPercentage": 42.5,
              "Category": "Library",
              "Description": "Scanning folders",
              "Triggers": [
                {
                  "Type": "DailyTrigger",
                  "TimeOfDayTicks": 432000000000,
                  "DayOfWeek": "Sunday",
                  "MaxRuntimeMs": 3600000
                },
                { "Type": "IntervalTrigger", "IntervalTicks": 18000000000 }
              ],
              "LastExecutionResult": {
                "Name": "Scan media library",
                "Key": "ScanLibrary",
                "StartTimeUtc": "2026-09-01T10:00:00Z",
                "EndTimeUtc": "2026-09-01T10:05:00Z",
                "Status": "Completed",
                "ErrorMessage": ""
              }
            }
            """.trimIndent(),
        ).toScheduledTaskInfo()

        assertEquals("task-1", task.id)
        assertEquals("ScanLibrary", task.key)
        assertEquals("Scan media library", task.name)
        assertEquals(TaskState.RUNNING, task.state)
        assertEquals(true, task.isHidden)
        // The parser hardcodes isEnabled=true — the WS contract never carries
        // a per-push enable flag.
        assertEquals(true, task.isEnabled)
        assertEquals(42.5, task.currentProgressPercentage!!, 0.0)
        assertEquals("Library", task.category)
        assertEquals("Scanning folders", task.description)

        val daily = task.triggers[0]
        assertEquals("DailyTrigger", daily.type)
        assertEquals(432_000_000_000L, daily.timeOfDayTicks)
        assertEquals("Sunday", daily.dayOfWeek)
        // Quirk pinned: the wire field is MaxRuntimeMs but the model field is
        // named maxRuntimeTicks — the value passes through unmapped.
        assertEquals(3_600_000L, daily.maxRuntimeTicks)
        assertEquals(18_000_000_000L, task.triggers[1].intervalTicks)

        val last = task.lastExecutionResult!!
        assertEquals("Scan media library", last.name)
        assertEquals("2026-09-01T10:00:00Z", last.startTimeUtc)
        assertEquals("2026-09-01T10:05:00Z", last.endTimeUtc)
        assertEquals("Completed", last.status)
        // Blank ErrorMessage degrades to null, not the empty string.
        assertNull(last.errorMessage)
    }

    @Test
    fun `task state strings map onto the TaskState enum`() {
        fun stateOf(stateStr: String) = JSONObject("""{"State": "$stateStr"}""").toScheduledTaskInfo().state
        assertEquals(TaskState.RUNNING, stateOf("Running"))
        assertEquals(TaskState.CANCELLING, stateOf("Cancelling"))
        // Unmapped/absent strings (server may send Idle, Paused, …) degrade
        // to IDLE.
        assertEquals(TaskState.IDLE, stateOf("Idle"))
        assertEquals(TaskState.IDLE, stateOf("SomeFutureState"))
        assertEquals(TaskState.IDLE, JSONObject("{}").toScheduledTaskInfo().state)
    }

    @Test
    fun `absent and null progress degrade to null`() {
        // Most of a library scan runs without a concrete percentage.
        assertNull(JSONObject("{}").toScheduledTaskInfo().currentProgressPercentage)
        assertNull(
            JSONObject("""{"CurrentProgressPercentage": null}""").toScheduledTaskInfo()
                .currentProgressPercentage,
        )
        // A garbage value fails optDouble into the NaN default, which the
        // isFinite filter drops.
        assertNull(
            JSONObject("""{"CurrentProgressPercentage": "not-a-number"}""").toScheduledTaskInfo()
                .currentProgressPercentage,
        )
    }

    @Test
    fun `blank category and description degrade to null`() {
        val task = JSONObject("""{"Category": "  ", "Description": ""}""").toScheduledTaskInfo()
        assertNull(task.category)
        assertNull(task.description)
    }

    @Test
    fun `missing or malformed triggers degrade to an empty list`() {
        assertTrue(JSONObject("{}").toScheduledTaskInfo().triggers.isEmpty())
        assertTrue(
            JSONObject("""{"Triggers": null}""").toScheduledTaskInfo().triggers.isEmpty(),
        )
        // Non-object trigger entries are skipped rather than crashing the
        // socket dispatch.
        assertTrue(
            JSONObject("""{"Triggers": [42, {"Type": "DailyTrigger"}]}""").toScheduledTaskInfo()
                .triggers.let { it.size == 1 && it[0].type == "DailyTrigger" },
        )
    }

    @Test
    fun `trigger numeric fields tolerate string numbers and garbage`() {
        val triggers = JSONObject(
            """
            { "Triggers": [
                { "Type": "T", "TimeOfDayTicks": "123", "IntervalTicks": "abc" },
                { "Type": "T", "IntervalTicks": 7 }
              ] }
            """.trimIndent(),
        ).toScheduledTaskInfo().triggers
        // String-coercible numbers parse; garbage yields null (distinct from
        // a genuine 0, which must survive).
        assertEquals(123L, triggers[0].timeOfDayTicks)
        assertNull(triggers[0].intervalTicks)
        assertNull(triggers[1].timeOfDayTicks)
        assertEquals(7L, triggers[1].intervalTicks)
    }

    @Test
    fun `missing last execution result degrades to null`() {
        assertNull(JSONObject("{}").toScheduledTaskInfo().lastExecutionResult)
    }

    @Test
    fun `execution result without a status defaults to Success`() {
        val last = JSONObject(
            """{ "LastExecutionResult": { "Name": "task" } }""",
        ).toScheduledTaskInfo().lastExecutionResult!!
        assertEquals("Success", last.status)
    }

    @Test
    fun `entirely empty task payload keeps model defaults`() {
        // Tolerance contract: an unknown server version may strip fields; the
        // parser must still produce a stable model object.
        val task = JSONObject("{}").toScheduledTaskInfo()
        assertEquals("", task.id)
        assertEquals("", task.key)
        assertEquals("", task.name)
        assertEquals(TaskState.IDLE, task.state)
        assertEquals(false, task.isHidden)
        assertEquals(true, task.isEnabled)
        assertNull(task.currentProgressPercentage)
        assertNull(task.category)
        assertNull(task.description)
        assertTrue(task.triggers.isEmpty())
        assertNull(task.lastExecutionResult)
    }

    // ----- toActivityLogEntry -----

    @Test
    fun `full activity log payload maps identity and optional fields`() {
        val entry = JSONObject(
            """
            {
              "Id": 84,
              "Name": "Session ended",
              "Type": "SessionEnded",
              "UserId": "u1",
              "Overview": "Long overview",
              "ShortOverview": "Short overview",
              "ItemId": "item-9",
              "Date": "2026-09-01T12:00:00Z",
              "Severity": "Warn"
            }
            """.trimIndent(),
        ).toActivityLogEntry()

        assertEquals(84L, entry.id)
        assertEquals("Session ended", entry.name)
        assertEquals("SessionEnded", entry.type)
        assertEquals("u1", entry.userId)
        assertEquals("Long overview", entry.overview)
        assertEquals("Short overview", entry.shortOverview)
        assertEquals("item-9", entry.itemId)
        assertEquals("2026-09-01T12:00:00Z", entry.date)
        assertEquals(ActivityLogSeverity.WARNING, entry.severity)
    }

    @Test
    fun `severity strings map onto the ActivityLogSeverity enum`() {
        fun severityOf(severity: String) =
            JSONObject("""{"Severity": "$severity"}""").toActivityLogEntry().severity
        assertEquals(ActivityLogSeverity.TRACE, severityOf("Trace"))
        assertEquals(ActivityLogSeverity.DEBUG, severityOf("Debug"))
        assertEquals(ActivityLogSeverity.WARNING, severityOf("Warn"))
        assertEquals(ActivityLogSeverity.WARNING, severityOf("Warning"))
        assertEquals(ActivityLogSeverity.ERROR, severityOf("Error"))
        assertEquals(ActivityLogSeverity.FATAL, severityOf("Fatal"))
        assertEquals(ActivityLogSeverity.FATAL, severityOf("Critical"))
        // Unknown values degrade to INFORMATION, matching the REST mapper.
        assertEquals(ActivityLogSeverity.INFORMATION, severityOf("Information"))
        assertEquals(ActivityLogSeverity.INFORMATION, severityOf("SomeFutureSeverity"))
        assertEquals(ActivityLogSeverity.INFORMATION, JSONObject("{}").toActivityLogEntry().severity)
    }

    @Test
    fun `blank optional strings degrade to null`() {
        val entry = JSONObject(
            """{"Name": "n", "UserId": "", "Overview": null, "ItemId": " "}""",
        ).toActivityLogEntry()
        assertNull(entry.userId)
        assertNull(entry.overview)
        assertNull(entry.itemId)
        // Non-optional fields keep their empty defaults.
        assertEquals("", entry.type)
        assertEquals("", entry.date)
    }

    @Test
    fun `missing id degrades to zero`() {
        assertEquals(0L, JSONObject("{}").toActivityLogEntry().id)
    }
}
