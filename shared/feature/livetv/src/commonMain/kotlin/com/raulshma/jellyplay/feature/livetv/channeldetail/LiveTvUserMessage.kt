package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.ui.message.UiMessage

/**
 * One-shot record/cancel feedback emitted by [ChannelDetailViewModel] and
 * rendered by [ChannelDetailScreen], which posts the resolved text to the
 * shared UserMessageBus — the commonMain-safe replacement for the legacy
 * `UiText.Resource(...)` values the
 * ViewModel used to post through the Android-only UserMessageBus. The screen
 * resolves the message text (compose-resources), so no R class or UiText
 * machinery leaks into shared code.
 *
 * The success arms stay feature data objects (their text is fixed); the
 * failure arm is the shared two-variant [UiMessage] — the server text when
 * the exception carried one, the action's localized fallback otherwise (the
 * former baked English literals are gone).
 */
@Immutable
sealed interface LiveTvUserMessage {
    /** A timer (single or series) was scheduled — `livetv_record_success`. */
    data object RecordSuccess : LiveTvUserMessage
    /** A timer (single or series) was canceled — `livetv_record_canceled`. */
    data object RecordCanceled : LiveTvUserMessage
    /** A record/cancel failure: server text or the action's resource fallback. */
    data class Failure(val message: UiMessage) : LiveTvUserMessage
}
