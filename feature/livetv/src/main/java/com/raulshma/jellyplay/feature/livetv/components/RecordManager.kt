package com.raulshma.jellyplay.feature.livetv.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.feature.livetv.R

/**
 * State machine for the unified record/cancel flow shared by the Programs,
 * Guide and Schedule tabs. Mirrors jellyfin-web's `recordinghelper`
 * `toggleRecording`: if no timer exists offer Record / Record Series; if a
 * timer exists offer Cancel / Cancel Series.
 */
sealed interface RecordDialogState {
    /** Idle — no dialog shown. */
    data object Idle : RecordDialogState
    /** Awaiting the user's record/cancel decision for [program]. */
    data class AwaitingChoice(val program: LiveTvProgram) : RecordDialogState
    /** Record/cancel request is in flight. */
    data object Requesting : RecordDialogState
    /** The action completed successfully. */
    data object Success : RecordDialogState
    /** The action failed. */
    data class Error(val message: String) : RecordDialogState
}

/**
 * Renders the appropriate record/cancel dialog for [state]. The caller wires
 * the action callbacks to its viewmodel (which in turn hits the repository).
 */
@Composable
fun RecordDialog(
    state: RecordDialogState,
    onRecordOnce: (LiveTvProgram) -> Unit,
    onRecordSeries: (LiveTvProgram) -> Unit,
    onCancelTimer: (LiveTvProgram) -> Unit,
    onCancelSeries: (LiveTvProgram) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        RecordDialogState.Idle -> Unit
        is RecordDialogState.AwaitingChoice -> {
            val program = state.program
            val hasTimer = !program.timerId.isNullOrEmpty()
            val hasSeriesTimer = !program.seriesTimerId.isNullOrEmpty()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(program.name) },
                text = {
                    val subtitle = buildString {
                        program.channelName?.takeIf { it.isNotBlank() }?.let { append(it) }
                        program.episodeTitle?.takeIf { it.isNotBlank() }?.let {
                            if (isNotEmpty()) append("\n"); append(it)
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    androidx.compose.foundation.layout.Column {
                        if (!hasTimer && !hasSeriesTimer) {
                            TextButton(onClick = { onRecordOnce(program) }) {
                                Text(stringResource(R.string.livetv_record_once))
                            }
                            TextButton(onClick = { onRecordSeries(program) }) {
                                Text(stringResource(R.string.livetv_record_series))
                            }
                        } else {
                            if (hasTimer) {
                                TextButton(onClick = { onCancelTimer(program) }) {
                                    Text(stringResource(R.string.livetv_cancel_recording))
                                }
                            }
                            if (hasSeriesTimer) {
                                TextButton(onClick = { onCancelSeries(program) }) {
                                    Text(stringResource(R.string.livetv_cancel_series))
                                }
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Close") }
                },
            )
        }
        RecordDialogState.Requesting -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Recording…") },
            text = { Text("Scheduling timer") },
            confirmButton = {},
            dismissButton = {},
        )
        RecordDialogState.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.livetv_record_success)) },
            text = { Text(stringResource(R.string.livetv_record_success)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
            dismissButton = {},
        )
        is RecordDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.livetv_record_failed)) },
            text = { Text(state.message, modifier = Modifier) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            dismissButton = {},
        )
    }
}
