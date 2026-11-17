package com.raulshma.jellyplay.core.ui.message

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.ui.components.logUiWarning
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A one-shot, user-facing message emitted through [UserMessageBus] and
 * rendered by the root host (Snackbar on phone/desktop, Toast on TV).
 *
 * Carries a [UiText] so messages are fully localizable: ViewModels emit a
 * [UiText.Resource] referencing a string resource and the host resolves
 * it at render time. Dynamic/server-supplied text uses [UiText.Raw].
 */
@Immutable
sealed interface UserMessage {
    val text: UiText

    /** Informational, non-blocking feedback (e.g. "Download started"). */
    data class Info(override val text: UiText) : UserMessage

    /** Recoverable error feedback (e.g. "Couldn't start the download"). */
    data class Error(override val text: UiText) : UserMessage

    /** Severity used by the host to pick duration / styling. */
    val severity: Severity
        get() = when (this) {
            is Info -> Severity.Info
            is Error -> Severity.Error
        }

    enum class Severity { Info, Error }
}

/**
 * App-wide bus for one-shot user-facing messages (v0.10.6 `core.ui.feedback`
 * port, Koin-single provided). ViewModels and screens post [UserMessage]s
 * here; a single root collector renders them.
 *
 * Implemented as a buffered [Channel] exposed as a cold [Flow]: each message
 * is delivered to a single collector and never replayed (one-shot semantics).
 */
class UserMessageBus {
    private val channel = Channel<UserMessage>(Channel.BUFFERED)

    val messages: Flow<UserMessage> = channel.receiveAsFlow()

    /**
     * Post a message; safe to call from any thread (non-suspending).
     *
     * DECLARED FIX (drop observability): [Channel.trySend] into a full buffer
     * (64 in-flight messages with no live root collector) fails WITHOUT
     * throwing, and the failure result used to be ignored — a burst of
     * failure messages posted while no host was collecting silently vanished.
     * The drop is now reported through the module's [logUiWarning] seam (no
     * new logging dependency, no API change): the channel shape, capacity,
     * one-shot semantics and the fire-and-forget signature are all unchanged,
     * and a successful post stays completely silent.
     */
    fun emit(message: UserMessage) {
        val result = channel.trySend(message)
        if (result.isFailure) {
            logUiWarning(
                tag = "UserMessageBus",
                message = "dropped user message (${message.severity}, no live collector or buffer full)",
                error = result.exceptionOrNull(),
            )
        }
    }

    /** Post an info message built from a localizable [UiText]. */
    fun info(text: UiText) = emit(UserMessage.Info(text))

    /** Post an error message built from a localizable [UiText]. */
    fun error(text: UiText) = emit(UserMessage.Error(text))

    /**
     * Post an info message from an already-resolved [String] (dynamic/server
     * content). Prefer the [UiText] overload for localizable text.
     */
    fun info(text: String) = emit(UserMessage.Info(UiText.Raw(text)))

    /**
     * Post an error message from an already-resolved [String] (dynamic/server
     * content). Prefer the [UiText] overload for localizable text.
     */
    fun error(text: String) = emit(UserMessage.Error(UiText.Raw(text)))
}

/**
 * CompositionLocal giving any Composable access to the app-wide
 * [UserMessageBus] without threading it through every parameter list.
 * Provided once at the root from the Koin single. The default is a throwaway
 * instance so that previews / tests that never wire the host simply drop the
 * message instead of crashing.
 */
val LocalUserMessageBus = staticCompositionLocalOf<UserMessageBus> {
    UserMessageBus()
}
