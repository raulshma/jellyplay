package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource

/**
 * One-shot action feedback emitted by [ArrQueueViewModel] and rendered by
 * [ArrQueueScreen] through the ArrQueueMessenger seam — the commonMain-safe
 * replacement for the legacy `context.getString(...)` values the ViewModel
 * used to post through the Android-only UserMessageBus (LiveTvUserMessage
 * screen-forward pattern, livetv conveyor).
 *
 * This seal EXTENDS the prior message seals (livetv / newsletter / admin /
 * settings were args-free `data object` + `Raw` shapes): the *arr acks carry
 * a format argument — `arrqueue_grab_sent` / `arrqueue_import_sent` interpolate
 * the release title — so the resource variants keep their [args] unresolved
 * until render time (locale resolves at display, music MixErrorMessage
 * semantics). Severity is carried by the variant: [Info] maps to the legacy
 * bus's `info(...)`, [Error] and [Raw] to `error(...)`.
 */
@Immutable
sealed interface ArrQueueMessage {
    /** Acknowledgement for an accepted action — `arrqueue_grab_sent` / `arrqueue_import_sent`. */
    data class Info(val res: StringResource, val args: List<String> = emptyList()) : ArrQueueMessage
    /** Localized failure whose resource needs no exception message — `arrqueue_unknown_error`. */
    data class Error(val res: StringResource, val args: List<String> = emptyList()) : ArrQueueMessage
    /** Raw failure text (exception message — already final, no localized form). */
    data class Raw(val text: String) : ArrQueueMessage
}
