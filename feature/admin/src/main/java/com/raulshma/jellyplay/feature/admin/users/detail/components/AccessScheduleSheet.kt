package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.UserAccessSchedule
import java.util.Locale

private val DAYS = listOf(
    "Sunday" to "Sun",
    "Monday" to "Mon",
    "Tuesday" to "Tue",
    "Wednesday" to "Wed",
    "Thursday" to "Thu",
    "Friday" to "Fri",
    "Saturday" to "Sat",
    "Everyday" to "Daily",
    "Weekday" to "Weekdays",
    "Weekend" to "Weekends",
)

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

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text("Access schedules", style = MaterialTheme.typography.titleSmall)
        if (schedules.isEmpty()) {
            Text(
                "No schedules set",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            schedules.forEachIndexed { index, s ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${DAYS.firstOrNull { it.first == s.dayOfWeek }?.second ?: s.dayOfWeek}  " +
                            "${s.startHour.toTimeString()}–${s.endHour.toTimeString()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { onChange(schedules - s) }) { Text("Delete") }
                }
            }
        }
        OutlinedButton(
            onClick = { showAdder = true },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Add schedule") }
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

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add schedule") },
        text = {
            Column {
                Text("Day", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAYS.forEach { (serial, label) ->
                        FilterChip(
                            selected = day == serial,
                            onClick = { day = serial },
                            label = { Text(label) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { pickingStart = true }) {
                        Text("Start ${startHour.toTimeString()}")
                    }
                    OutlinedButton(onClick = { pickingEnd = true }) {
                        Text("End ${endHour.toTimeString()}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(UserAccessSchedule(dayOfWeek = day, startHour = startHour, endHour = endHour))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
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
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(),
            )
        },
    )
}
