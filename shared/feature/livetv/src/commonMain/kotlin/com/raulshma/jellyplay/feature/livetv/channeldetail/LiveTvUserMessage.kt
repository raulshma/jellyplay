package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.compose.runtime.Immutable

/**
 * One-shot record/cancel feedback emitted by [ChannelDetailViewModel] and
 * rendered by [ChannelDetailScreen] through the LiveTvMessenger seam — the
 * commonMain-safe replacement for the legacy `UiText.Resource(...)` values the
 * ViewModel used to post through the Android-only UserMessageBus. The screen
 * resolves the resource text (compose-resources), so no R class or UiText
 * machinery leaks into shared code.
 */
@Immutable
sealed interface LiveTvUserMessage {
    /** A timer (single or series) was scheduled — `livetv_record_success`. */
    data object RecordSuccess : LiveTvUserMessage
    /** A timer (single or series) was canceled — `livetv_record_canceled`. */
    data object RecordCanceled : LiveTvUserMessage
    /** Raw failure text (exception message or fixed fallback). */
    data class Raw(val text: String) : LiveTvUserMessage
}
