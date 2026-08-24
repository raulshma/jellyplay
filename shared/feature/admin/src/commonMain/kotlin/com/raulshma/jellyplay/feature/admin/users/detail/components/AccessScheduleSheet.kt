package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.UserAccessSchedule
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_access_schedules_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_access_schedules_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_add
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_add_schedule
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day_fri
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day_mon
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day_sat
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day_sun
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day_thu
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day_tue
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_day_wed
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_delete
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_end_time
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_schedules
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_ok
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_schedule_everyday
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_schedule_weekday
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_schedule_weekend
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_start_time
import java.util.Locale

private val DAYS = listOf(
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Everyday",
    "Weekday",
    "Weekend",
)

/** Localized display label for an access-schedule day serial value. */
@Composable
private fun dayLabel(serial: String): String = when (serial) {
    "Sunday" -> stringResource(Res.string.admin_day_sun)
    "Monday" -> stringResource(Res.string.admin_day_mon)
    "Tuesday" -> stringResource(Res.string.admin_day_tue)
    "Wednesday" -> stringResource(Res.string.admin_day_wed)
    "Thursday" -> stringResource(Res.string.admin_day_thu)
    "Friday" -> stringResource(Res.string.admin_day_fri)
    "Saturday" -> stringResource(Res.string.admin_day_sat)
    "Everyday" -> stringResource(Res.string.admin_schedule_everyday)
    "Weekday" -> stringResource(Res.string.admin_schedule_weekday)
    "Weekend" -> stringResource(Res.string.admin_schedule_weekend)
    else -> serial
}

/** Formats a Double hour (13.5) as "HH:mm". */
private fun Double.toTimeString() =
    "%02d:%02d".format(Locale.ROOT, toInt(), ((this % 1) * 60).toInt())

/**
 * Inline access-schedule list with add/delete. Adding opens a dialog with
 * day-of-week chips + Material [TimePicker]s for start/end. Hidden for
 * administrators (web parity) — controlled by the caller.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccessScheduleSection(
    schedules: List<UserAccessSchedule>,
    onChange: (List<UserAccessSchedule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdder by remember { mutableStateOf(false) }

    UserEditSection(
        title = stringResource(Res.string.admin_access_schedules_title),
        description = stringResource(Res.string.admin_access_schedules_desc),
        modifier = modifier,
    ) {
        if (schedules.isEmpty()) {
            Text(
                stringResource(Res.string.admin_no_schedules),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            schedules.forEachIndexed { index, s ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${dayLabel(s.dayOfWeek)}  " +
                            "${s.startHour.toTimeString()}–${s.endHour.toTimeString()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { onChange(schedules - s) }) { Text(stringResource(Res.string.admin_delete)) }
                }
            }
        }
        OutlinedButton(
            onClick = { showAdder = true },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text(stringResource(Res.string.admin_add_schedule)) }
    }

    if (showAdder) {
        ScheduleAdderDialog(
            onCancel = { showAdder = false },
            onSave = { schedule ->
                onChange(schedules + schedule)
                showAdder = false
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScheduleAdderDialog(
    onCancel: () -> Unit,
    onSave: (UserAccessSchedule) -> Unit,
) {
    var day by remember { mutableStateOf("Everyday") }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var startHour by remember { mutableStateOf(8.0) }
    var endHour by remember { mutableStateOf(22.0) }

    // TV: land initial focus on the primary action (Add).
    val isTv = LocalTvMode.current
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) confirmFocusRequester.tryRequestFocus("schedule_add_confirm")
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.admin_add_schedule)) },
        text = {
            Column {
                Text(stringResource(Res.string.admin_day), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAYS.forEach { serial ->
                        FilterChip(
                            selected = day == serial,
                            onClick = { day = serial },
                            label = { Text(dayLabel(serial)) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { pickingStart = true }) {
                        Text(stringResource(Res.string.admin_start_time, startHour.toTimeString()))
                    }
                    OutlinedButton(onClick = { pickingEnd = true }) {
                        Text(stringResource(Res.string.admin_end_time, endHour.toTimeString()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(UserAccessSchedule(dayOfWeek = day, startHour = startHour, endHour = endHour))
                },
                modifier = Modifier.focusRequester(confirmFocusRequester),
            ) { Text(stringResource(Res.string.admin_add)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.admin_cancel)) } },
    )

    if (pickingStart) {
        TimePickerDialog(
            initialHour = startHour.toInt(),
            initialMinute = ((startHour % 1) * 60).toInt(),
            onCancel = { pickingStart = false },
            onConfirm = { h, m ->
                startHour = h + m / 60.0
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        TimePickerDialog(
            initialHour = endHour.toInt(),
            initialMinute = ((endHour % 1) * 60).toInt(),
            onCancel = { pickingEnd = false },
            onConfirm = { h, m ->
                endHour = h + m / 60.0
                pickingEnd = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onCancel: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    // TV: land initial focus on the primary action (OK).
    val isTv = LocalTvMode.current
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTv) confirmFocusRequester.tryRequestFocus("schedule_time_confirm")
    }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(state.hour, state.minute) },
                modifier = Modifier.focusRequester(confirmFocusRequester),
            ) { Text(stringResource(Res.string.admin_ok)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.admin_cancel)) } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(),
            )
        },
    )
}
