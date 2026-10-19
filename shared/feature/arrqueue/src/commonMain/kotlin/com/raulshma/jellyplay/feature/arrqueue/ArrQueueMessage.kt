package com.raulshma.jellyplay.feature.arrqueue

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource

/**
 * One-shot action feedback emitted by [ArrQueueViewModel] and rendered by
 * [ArrQueueScreen], which posts the resolved text to the shared UserMessageBus.
 *
 * The resource variants keep their [args] unresolved until render time
 * (locale resolves at display, music MixErrorMessage semantics) — e.g.
 * `arrqueue_grab_sent` / `arrqueue_import_sent` interpolate the release
 * title. Severity is carried by the variant: [Info] maps to an informational
 * message, [Error] and [Raw] to an error.
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
