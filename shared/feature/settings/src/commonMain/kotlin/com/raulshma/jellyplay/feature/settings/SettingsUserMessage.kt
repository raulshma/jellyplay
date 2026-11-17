package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable

/**
 * One-shot user-facing feedback emitted by [SettingsViewModel] and posted to
 * the app-wide UserMessageBus by the consuming screens ([SettingsScreen] for
 * the session broadcast ack, [BackupSettingsScreen] for the export outcome) —
 * the settings module's one-shot message idiom (DetailMessage /
 * ArrQueueMessage / the codified StateManagementConventions §2: buffered
 * Channel, exposed as `receiveAsFlow()`, consumed exactly once).
 *
 * Replaces the former pair of nullable `composeState` fields
 * (`messageSentEvent`, `backupRestoreStatus`) + their clear-it-after-handling
 * calls (`clearMessageEvent` / `clearBackupRestoreStatus`): a StateFlow
 * replays its current value to every new collector, so the parked fields
 * re-fired the snackbar on recomposition / re-subscription for an action that
 * happened once. The Channel never replays.
 *
 * Success and failure are DISTINCT arms — the fork lives in the type, not in
 * string prefix matching over a parked String:
 *
 * - [MessageSent] / [MessageSendFailed] carry no payload — the session
 *   broadcast's outcome is fully described by the variant.
 * - [BackupExportFailed] keeps the raw exception message ([reason], already
 *   final, may be null) so the screen interpolates the same
 *   "Export failed: <reason>" text the former field carried.
 *
 * The bus texts are preserved byte-for-byte by [busText] — the single
 * arm-to-text fold both consuming screens render through.
 */
@Immutable
sealed interface SettingsUserMessage {
    /** The admin session broadcast reached the session. */
    data object MessageSent : SettingsUserMessage

    /** The admin session broadcast failed. */
    data object MessageSendFailed : SettingsUserMessage

    /** The settings backup envelope wrote successfully. */
    data object BackupExported : SettingsUserMessage

    /** The settings backup export failed; [reason] is the exception's own message (null when it carried none). */
    data class BackupExportFailed(val reason: String?) : SettingsUserMessage
}

/**
 * The exact bus text each [SettingsUserMessage] arm renders — the same
 * strings the former `messageSentEvent` / `backupRestoreStatus` parked fields
 * carried ("Message sent successfully" / "Failed to send message" /
 * "Settings exported successfully" / "Export failed: <reason>"). One fold so
 * both consuming screens resolve identical text for every arm; a new message
 * shape must extend this `when`, keeping the mapping exhaustive.
 */
fun SettingsUserMessage.busText(): String = when (this) {
    SettingsUserMessage.MessageSent -> "Message sent successfully"
    SettingsUserMessage.MessageSendFailed -> "Failed to send message"
    SettingsUserMessage.BackupExported -> "Settings exported successfully"
    is SettingsUserMessage.BackupExportFailed -> "Export failed: $reason"
}
