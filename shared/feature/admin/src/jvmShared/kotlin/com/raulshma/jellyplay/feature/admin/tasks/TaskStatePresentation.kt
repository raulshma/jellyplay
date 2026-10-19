package com.raulshma.jellyplay.feature.admin.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.TaskState
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_daily
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_every_days
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_every_hours
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_on_startup
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_trigger_weekly
import org.jetbrains.compose.resources.StringResource

/**
 * The scheduled-task state presentation table — the [ScheduledTasksScreen]
 * state decisions in one tested home (the module's presentation-table
 * precedent: the requests/arrqueue label+color tables). Three consumers read
 * it: the row's [TaskStateBadge] (label + tint), the row's per-state action
 * (`cancellable` → Stop, `startable` → Run, neither → the static
 * "Cancelling..." text) and the dashboard's [com.raulshma.jellyplay.feature.admin.dashboard.components.RunningTasksCard]
 * state dot (same tint ladder). Labels are single-sited here, not yet string
 * resources.
 *
 * Color rides a [TaskColorRole] rather than a resolved [Color] because the
 * RUNNING/MUTED arms are MaterialTheme roles (composable reads — a pure
 * table cannot name them) while WARNING is a static [StatusColors] value;
 * [toColor] is the trivial composable-side map. The MUTED arm is
 * parameterized: the dashboard's dot renders it `outline`, the screen's text
 * `onSurfaceVariant`.
 *
 * The dashboard scan machine's `state == TaskState.RUNNING`
 * special-case deliberately stays raw-enum code: it reads the domain fact
 * "the server reports the task running", not a presentation row (the table's
 * `cancellable` happens to coincide today but could legitimately drift).
 */
internal enum class TaskColorRole {
    /** The app's primary — an actively running task. */
    PRIMARY,

    /** The static warning tint — cancelling, still winding down. */
    WARNING,

    /** A muted surface role — idle. Resolved per surface (see [toColor]). */
    MUTED,
}

/** One row of the [TaskStatePresentation] state table. */
internal data class TaskStateRow(
    /** The badge label (the former hardcoded literal). */
    val label: String,
    /** The tint role; resolved composable-side via [toColor]. */
    val colorRole: TaskColorRole,
    /** True when the row shows the Stop action (a RUNNING task). */
    val cancellable: Boolean,
    /** True when the row shows the Run action (an IDLE task). */
    val startable: Boolean,
)

internal object TaskStatePresentation {

    /** The state → (label, tint role, Stop?, Run?) row, exhaustive over [TaskState]. */
    operator fun get(state: TaskState): TaskStateRow = when (state) {
        TaskState.RUNNING -> TaskStateRow("Running", TaskColorRole.PRIMARY, cancellable = true, startable = false)
        TaskState.CANCELLING -> TaskStateRow("Cancelling", TaskColorRole.WARNING, cancellable = false, startable = false)
        TaskState.IDLE -> TaskStateRow("Idle", TaskColorRole.MUTED, cancellable = false, startable = true)
    }
}

/**
 * The trivial role → [Color] map (composable-side: the PRIMARY/MUTED arms are
 * theme roles). [muted] parameterizes the MUTED arm per surface — the
 * dashboard dot passes `outline`, the screen's default is `onSurfaceVariant`.
 */
@androidx.compose.runtime.Composable
internal fun TaskColorRole.toColor(muted: Color = MaterialTheme.colorScheme.onSurfaceVariant): Color = when (this) {
    TaskColorRole.PRIMARY -> MaterialTheme.colorScheme.primary
    TaskColorRole.WARNING -> StatusColors.warning
    TaskColorRole.MUTED -> muted
}

/**
 * The trigger-chip label decision — the [TriggerChip] `when` folded out of
 * composition. [intervalTicks]/36_000_000_000 (Jellyfin's 100ns ticks per
 * hour) and the >24h → days escalation are pure math; the resource arms carry
 * the unresolved [StringResource] plus its optional count argument, the
 * literal arms the resolved-elsewhere strings (the weekday name and the
 * unknown-trigger passthrough), so the JVM lane can pin every arm.
 */
internal sealed interface TriggerLabel {
    /** A resource (optionally parameterized with the hours/days count). */
    data class Resource(val resource: StringResource, val arg: Int?) : TriggerLabel

    /** A resolved-here literal: the capitalized weekday, or the raw type minus its "Trigger" suffix. */
    data class Literal(val text: String) : TriggerLabel
}

/** Jellyfin tick math: 100ns ticks per hour. */
internal const val TRIGGER_TICKS_PER_HOUR: Long = 36_000_000_000

internal fun triggerLabel(type: String, intervalTicks: Long?, dayOfWeek: String?): TriggerLabel = when {
    type == "IntervalTrigger" && intervalTicks != null -> {
        val hours = intervalTicks / TRIGGER_TICKS_PER_HOUR
        if (hours > 24) {
            TriggerLabel.Resource(Res.string.admin_trigger_every_days, (hours / 24).toInt())
        } else {
            TriggerLabel.Resource(Res.string.admin_trigger_every_hours, hours.toInt())
        }
    }
    type == "DailyTrigger" -> TriggerLabel.Resource(Res.string.admin_trigger_daily, arg = null)
    type == "WeeklyTrigger" -> dayOfWeek?.trim()?.takeIf { it.isNotEmpty() }
        ?.lowercase()?.replaceFirstChar { it.uppercase() }
        ?.let { TriggerLabel.Literal(it) }
        ?: TriggerLabel.Resource(Res.string.admin_trigger_weekly, arg = null)
    type == "StartupTrigger" -> TriggerLabel.Resource(Res.string.admin_trigger_on_startup, arg = null)
    else -> TriggerLabel.Literal(type.removeSuffix("Trigger"))
}
