package com.raulshma.jellyplay.core.data.worker

/**
 * Port for the Ask-mode suggestion notification (gh-181): after an
 * [AutoDownloadCheck] pass records [count] fresh pending prompts, the Android
 * adapter posts a shade summary (tap-through to the Downloads screen where the
 * suggestion card lives); desktop has no system-notification path for this —
 * its Koin binding is a no-op and the Downloads screen's suggestion card is
 * the only surface. Best-effort by contract: a failed post must never fail the
 * pass (callers wrap in runCatching, mirroring [DownloadProgressNotifier]).
 */
fun interface AutoDownloadSuggestionNotifier {
    fun notifyNewSuggestions(count: Int)
}
