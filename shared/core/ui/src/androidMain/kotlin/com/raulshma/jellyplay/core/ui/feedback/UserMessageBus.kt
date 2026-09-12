package com.raulshma.jellyplay.core.ui.feedback

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A one-shot, user-facing message emitted through [UserMessageBus] and
 * rendered by the root host (Snackbar on phone, Toast on TV).
 *
 * Carries a [UiText] so messages are fully localizable: ViewModels emit a
 * [UiText.Resource] referencing a `strings.xml` entry and the host resolves
 * it at render time. Dynamic/server-supplied text uses
 * [UiText.Raw].
 */
@Immutable
sealed interface UserMessage {
    val text: UiText

    /** Informational, non-blocking feedback (e.g. "Saved to gallery"). */
    data class Info(override val text: UiText) : UserMessage

    /** Recoverable error feedback (e.g. "No video player found"). */
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
 * App-wide bus for one-shot user-facing messages. ViewModels and screens
 * post [UserMessage]s here; a single root collector renders them. Replaces
 * the ~15 scattered `Toast.makeText` call sites so feedback is consistent,
 * testable, and localizable.
 *
 * Implemented as a buffered [Channel] exposed as a cold [Flow]: each message
 * is delivered to a single collector and never replayed (one-shot semantics),
 * matching the convention documented in `StateManagementConventions`.
 */
class UserMessageBus {
    private val channel = Channel<UserMessage>(Channel.BUFFERED)

    val messages: Flow<UserMessage> = channel.receiveAsFlow()

    /** Post a message; safe to call from any thread (non-suspending). */
    fun emit(message: UserMessage) {
        channel.trySend(message)
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
 * Provided once at the root ([com.raulshma.jellyplay.navigation.JellyPlayApp]).
 *
 * The default is a throwaway instance so that previews / tests that never
 * wire the host simply drop the message instead of crashing.
 */
val LocalUserMessageBus = staticCompositionLocalOf<UserMessageBus> {
    UserMessageBus()
}
