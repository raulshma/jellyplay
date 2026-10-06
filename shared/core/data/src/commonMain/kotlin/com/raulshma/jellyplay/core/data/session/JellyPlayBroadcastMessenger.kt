package com.raulshma.jellyplay.core.data.session

/**
 * The one-shot user-message sink the companion-plugin's live `broadcast`
 * events surface through (ADR 0010). Core:data cannot see core/ui's
 * UserMessageBus (the dependency direction that produced the
 * DownloadOutcomeMessenger seam), so the controller talks to THIS narrow
 * seam and each shell bridges it app-side: Android and desktop both forward
 * to the shared UserMessageBus (snackbar); a shell that registers nothing
 * degrades the event to the log-only path (the controller resolves the seam
 * with Koin `getOrNull`).
 *
 * [text] is the fully composed one-shot message (the controller folds the
 * broadcast's title/body) — server-supplied text, so the bus's
 * `UiText.Raw` path applies.
 */
fun interface JellyPlayBroadcastMessenger {

    /** Post [text] as a one-shot informational user message. */
    fun showBroadcast(text: String)
}
