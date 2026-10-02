package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.data.remote.DisplayMessagePayload

/**
 * The ONE DisplayMessage→surface-text fold both shells run: `header\ntext`
 * when the header is present, the bare text otherwise, and NULL when the
 * result is blank (an all-blank push surfaces nothing rather than an empty
 * snackbar/toast).
 *
 * The twin of both shells' collectors — Android's MainViewModel
 * `displayMessages` collector (bus.info) and the desktop shell's receiver
 * message source (UserMessageHost Info via desktopUserMessageSources) —
 * which carried byte-identical hand copies until this moved here, so one
 * server push reads identically on both shells by construction, not by
 * review. Lives in jvmShared beside the receiver payload's own source set
 * ([DisplayMessagePayload] is jvmShared in :shared:core:data — the
 * Android+desktop transport surface, never an iOS payload).
 */
fun displayMessageText(payload: DisplayMessagePayload): String? {
    val text =
        if (payload.header.isNotBlank()) "${payload.header}\n${payload.text}" else payload.text
    return text.ifBlank { null }
}
