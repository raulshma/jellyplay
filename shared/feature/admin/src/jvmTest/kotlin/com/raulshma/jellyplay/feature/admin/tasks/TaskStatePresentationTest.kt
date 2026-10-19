package com.raulshma.jellyplay.feature.admin.tasks

import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_daily
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_every_days
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_every_hours
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_on_startup
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_weekly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the scheduled-task presentation tables ([TaskStatePresentation] state
 * rows and [triggerLabel] trigger chips) — the `when`s the tasks screen and
 * the dashboard's RunningTasksCard used to carry inline (the
 * ArrQueuePresentationTest rule: every arm pinned, resource objects compared
 * by identity). A table edit must update this file in lockstep.
 */
class TaskStatePresentationTest {

    // ── the state table ─────────────────────────────────────────────────

    @Test
    fun `state rows pin label role and action flags per state`() {
        val table = mapOf(
            TaskState.RUNNING to TaskStateRow(
                label = "Running",
                colorRole = TaskColorRole.PRIMARY,
                cancellable = true,
                startable = false,
            ),
            TaskState.CANCELLING to TaskStateRow(
                label = "Cancelling",
                colorRole = TaskColorRole.WARNING,
                cancellable = false,
                startable = false,
            ),
            TaskState.IDLE to TaskStateRow(
                label = "Idle",
                colorRole = TaskColorRole.MUTED,
                cancellable = false,
                startable = true,
            ),
        )
        table.forEach { (state, expected) ->
            assertEquals(expected, TaskStatePresentation[state], "at $state")
        }
    }

    @Test
    fun `exactly one state is cancellable and exactly one is startable`() {
        // Stop shows only on RUNNING, Run only on IDLE — the disjointness the
        // screen's when relies on (CANCELLING gets the static text arm).
        assertEquals(TaskState.RUNNING, TaskState.entries.single { TaskStatePresentation[it].cancellable })
        assertEquals(TaskState.IDLE, TaskState.entries.single { TaskStatePresentation[it].startable })
        TaskState.entries.forEach { state ->
            val row = TaskStatePresentation[state]
            assertTrue(!(row.cancellable && row.startable), "$state offers both actions")
        }
    }

    // ── the trigger chip table ──────────────────────────────────────────

    @Test
    fun `interval trigger under or at 24h renders every-hours with the hour count`() {
        // 3 hours of 100ns ticks.
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_every_hours, 3),
            triggerLabel("IntervalTrigger", intervalTicks = 3 * TRIGGER_TICKS_PER_HOUR, dayOfWeek = null),
        )
        // Boundary: exactly 24h is NOT ">24" — hours, not days.
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_every_hours, 24),
            triggerLabel("IntervalTrigger", intervalTicks = 24 * TRIGGER_TICKS_PER_HOUR, dayOfWeek = null),
        )
    }

    @Test
    fun `interval trigger over 24h renders every-days with the day count`() {
        // 48h → 2 days; 90h → 3 days (floor division, not rounding).
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_every_days, 2),
            triggerLabel("IntervalTrigger", intervalTicks = 48 * TRIGGER_TICKS_PER_HOUR, dayOfWeek = null),
        )
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_every_days, 3),
            triggerLabel("IntervalTrigger", intervalTicks = 90 * TRIGGER_TICKS_PER_HOUR, dayOfWeek = null),
        )
    }

    @Test
    fun `interval trigger without ticks falls through to the unknown arm`() {
        // The `intervalTicks != null` guard fails → the raw type minus suffix.
        assertEquals(
            TriggerLabel.Literal("Interval"),
            triggerLabel("IntervalTrigger", intervalTicks = null, dayOfWeek = null),
        )
    }

    @Test
    fun `daily and startup triggers pin their resources`() {
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_daily, null),
            triggerLabel("DailyTrigger", intervalTicks = null, dayOfWeek = null),
        )
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_on_startup, null),
            triggerLabel("StartupTrigger", intervalTicks = null, dayOfWeek = null),
        )
    }

    @Test
    fun `weekly trigger renders the capitalized weekday or falls back to weekly`() {
        assertEquals(
            TriggerLabel.Literal("Monday"),
            triggerLabel("WeeklyTrigger", intervalTicks = null, dayOfWeek = "monday"),
        )
        assertEquals(
            TriggerLabel.Literal("Sunday"),
            triggerLabel("WeeklyTrigger", intervalTicks = null, dayOfWeek = "SUNDAY"),
        )
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_weekly, null),
            triggerLabel("WeeklyTrigger", intervalTicks = null, dayOfWeek = null),
        )
        assertEquals(
            TriggerLabel.Resource(Res.string.admin_trigger_weekly, null),
            triggerLabel("WeeklyTrigger", intervalTicks = null, dayOfWeek = ""),
        )
    }

    @Test
    fun `unknown trigger types pass through minus their suffix`() {
        assertEquals(
            TriggerLabel.Literal("Maintenance"),
            triggerLabel("MaintenanceTrigger", intervalTicks = null, dayOfWeek = null),
        )
        // No suffix → unchanged.
        assertEquals(
            TriggerLabel.Literal("Manual"),
            triggerLabel("Manual", intervalTicks = null, dayOfWeek = null),
        )
    }
}
