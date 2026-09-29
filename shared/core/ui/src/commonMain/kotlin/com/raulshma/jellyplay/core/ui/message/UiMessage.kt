package com.raulshma.jellyplay.core.ui.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A state-carried, user-facing message: the two-variant seal a ViewModel
 * parks in its UI state (or emits on a feature flow) and the screen resolves
 * to text where it renders. Deliberately NOT the [UserMessageBus] — that bus
 * is fire-and-forget (one-shot delivery to the root host, never replayed),
 * while a [UiMessage] lives IN state until the consumer clears it, so it
 * survives recomposition and renders inline (error banners, dialog bodies)
 * as well as feeding a snackbar.
 *
 * The app-wide home for the per-feature message seals' shared shape
 * ([UiMessage.Resource] carries a compose-resources [StringResource] plus
 * optional format args and stays unresolved until render — the commonMain VM
 * seam has no Context; [UiMessage.Raw] wraps an already-final [String] for
 * genuinely dynamic content such as a server-supplied or exception message).
 * Feature seams whose variants are exactly this shape typealias to it
 * (AuthMessage, SyncPlayMessage, MixErrorMessage, AdminUserMessage,
 * LivePlayerMessage); seals carrying extra variants or per-variant payloads
 * (severity, data objects) keep their own classes and use [UiMessage] only
 * where the shape fits.
 *
 * ViewModels and other non-Composable producers build a [UiMessage] without
 * a platform context — including the message-or-fallback fold [Companion.of]
 * — and resolution happens at the edges via [asText] in a Composable.
 */
@Immutable
sealed interface UiMessage {

    /**
     * A string resource with optional printf-style format args, resolved
     * lazily by the UI layer so it stays correct under locale changes.
     */
    @Immutable
    data class Resource(val res: StringResource, val args: List<Any> = emptyList()) : UiMessage

    /** Already-resolved, non-localizable text (exception/server-supplied). */
    @Immutable
    data class Raw(val text: String) : UiMessage

    companion object {

        /**
         * The message-or-fallback fold for a [Throwable]: the throwable's own
         * message wins as [Raw]; a null message (or null throwable) falls
         * back to [Resource]. The one home for the
         * `it.message?.let { Raw(it) } ?: Resource(...)` copy that used to
         * live per failure site — the [UiMessage]-shaped counterpart of
         * core:data's `UserErrorMessages.resolve` String fold, implemented
         * here (not there) so core:ui stays repository-free.
         */
        fun of(throwable: Throwable?, fallbackRes: StringResource): UiMessage =
            of(throwable?.message, fallbackRes)

        /**
         * [of] for a [Result] — folds the failure, maps successes to the
         * fallback (there is no message on a success). Mirrors
         * `UserErrorMessages.rawOrNull(result)`-based folds.
         */
        fun of(result: Result<*>, fallbackRes: StringResource): UiMessage =
            of(result.exceptionOrNull(), fallbackRes)

        /**
         * [of] for an already-extracted message [String] (an engine error
         * string, or a throwable message a site pre-filtered with its own
         * guard): null falls back to [Resource].
         */
        fun of(text: String?, fallbackRes: StringResource): UiMessage =
            text?.let(::Raw) ?: Resource(fallbackRes)
    }
}

/** Resolve this [UiMessage] to concrete text in composition (locale resolves here). */
@Composable
fun UiMessage.asText(): String = when (this) {
    is UiMessage.Resource -> stringResource(res, *args.toTypedArray())
    is UiMessage.Raw -> text
}
