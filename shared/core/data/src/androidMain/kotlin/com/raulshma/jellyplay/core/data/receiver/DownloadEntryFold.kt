package com.raulshma.jellyplay.core.data.receiver

/**
 * The decoded intent of a download-notification broadcast — the entry fold of
 * GLOSSARY's "Receiver intake fold" for [DownloadActionReceiver], extracted
 * from its onReceive so the verb decode is plain-Kotlin testable (the
 * Robolectric end-to-end that remains pins the dispatch, not the decode).
 *
 * Rejection semantics are the hand-rolled ones, byte for byte: the
 * `download_id` extra is read before the action match, so a broadcast with a
 * known action but no id is [DownloadEntry.Ignored] — and so is an unknown
 * action with a valid id (the old `when` simply fell through). The id is
 * opaque to the fold: pause/resume/cancel all carry the same single extra.
 */
sealed interface DownloadEntry {
    data class Pause(val downloadId: String) : DownloadEntry
    data class Resume(val downloadId: String) : DownloadEntry
    data class Cancel(val downloadId: String) : DownloadEntry

    /** Unknown action, or no `download_id` extra — a silent no-op. */
    data object Ignored : DownloadEntry
}

/**
 * Folds a download broadcast's [action] + [downloadId] into the verb to
 * dispatch. Action strings are [DownloadActionReceiver]'s companion
 * constants — the pair-local literals the notification PendingIntents carry;
 * they must stay byte-identical.
 */
fun downloadEntry(
    action: String?,
    downloadId: String?,
): DownloadEntry {
    val id = downloadId ?: return DownloadEntry.Ignored
    return when (action) {
        DownloadActionReceiver.ACTION_PAUSE -> DownloadEntry.Pause(id)
        DownloadActionReceiver.ACTION_RESUME -> DownloadEntry.Resume(id)
        DownloadActionReceiver.ACTION_CANCEL -> DownloadEntry.Cancel(id)
        else -> DownloadEntry.Ignored
    }
}
