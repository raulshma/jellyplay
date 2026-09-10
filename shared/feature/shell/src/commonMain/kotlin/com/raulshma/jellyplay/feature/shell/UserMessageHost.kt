package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.core.ui.message.UserMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import org.jetbrains.compose.resources.getString

/**
 * How long a presented message stays up — the shared severity policy's whole
 * vocabulary, plain data so no platform presentation type leaks past this
 * module. Shells map it onto their own constants (SnackbarDuration.Short/
 * Long on phone and desktop, Toast LENGTH_SHORT/LENGTH_LONG on TV); the
 * values coincide on every surface, so the mapping is total and trivial.
 */
enum class UserMessageDuration { Short, Long }

/**
 * The one message-presentation seam behind both shells: the severity→duration
 * policy plus the merge→resolve→present choreography that the Android shell
 * used to hand-copy into two parallel `LaunchedEffect` collectors (one per
 * bus) and that desktop never had for the shared bus at all (its messages
 * were silently dropped). The shell owns only the final surface — snackbar on
 * phone/desktop, Toast on TV — through [present].
 *
 * Choreography (pinned by UserMessageHostTest, mirrored from the Android
 * collectors this replaces): all [host]ed sources are merged and drained by
 * ONE collector, so presentation is serial — a message arriving while another
 * is showing QUEUES until [present] returns, it is never dropped and never
 * displaces the message on screen (the snackbar host's own serialization made
 * that implicit before; TV Toasts, whose [present] returns immediately, keep
 * their fire-and-forget cadence). Each message is presented exactly once and
 * per-source order is preserved; order across sources is arrival order, not
 * source priority.
 *
 * commonMain stays pure: no Android types here. The TV-vs-touch fork is the
 * [present] adapter's decision (it reads [UserMessageDuration] as data), and
 * the legacy Android `:core:ui` feedback bus — whose payload type this module
 * cannot see — feeds [hostAdapted] with the shell's own severity view and
 * resolver instead.
 *
 * The host runs where the shell launches it (an `LaunchedEffect` body, so the
 * collector dies with the composition that keyed it, exactly as the inlined
 * collectors did); it holds no scope and no state of its own.
 *
 * @param resolveText resolves a message's [UiText] payload off-composition —
 *   compose-resources' suspend `getString` for [UiText.Resource], the value
 *   for [UiText.Raw] — the seam Android's collector inlines and `asString()`
 *   cannot serve (@Composable-only).
 * @param present the shell's rendering adapter: shows already-resolved [text]
 *   for the policy's [duration] and returns when the surface is ready for the
 *   next message (suspend on snackbar hosts, immediate on Toast).
 */
class UserMessageHost(
    private val resolveText: suspend (UiText) -> String,
    private val present: suspend (text: String, duration: UserMessageDuration) -> Unit,
) {

    /**
     * Presents every message arriving on [sources] — the shared
     * [UserMessageBus] flow first, plus any shell-local flows of the same
     * type (desktop merges its music-error relay in) — until cancellation.
     */
    suspend fun host(vararg sources: Flow<UserMessage>) {
        hostAdapted(
            sources = sources.toList(),
            severityOf = { it.severity },
            resolveText = { message -> resolveText(message.text) },
        )
    }

    /**
     * The same choreography for a shell-OWNED message payload this module
     * cannot name (the Android shell's legacy `:core:ui` feedback bus): the
     * shell supplies its own [severityOf] projection onto the shared severity
     * enum and its own [resolveText] (`UiText.resolve(context)` there),
     * keeping Android types out of commonMain. Desktop-style shells whose
     * sources are already [UserMessage]s use [host].
     */
    suspend fun <M> hostAdapted(
        sources: List<Flow<M>>,
        severityOf: (M) -> UserMessage.Severity,
        resolveText: suspend (M) -> String,
    ) {
        merge(*sources.toTypedArray()).collect { message ->
            present(resolveText(message), durationFor(severityOf(message)))
        }
    }

    companion object {
        /**
         * The severity→duration policy both Android collectors hand-copied:
         * errors linger, info does not.
         */
        fun durationFor(severity: UserMessage.Severity): UserMessageDuration = when (severity) {
            UserMessage.Severity.Error -> UserMessageDuration.Long
            UserMessage.Severity.Info -> UserMessageDuration.Short
        }
    }
}

/**
 * Resolves a [UiText] payload to a concrete [String] outside composition —
 * the shared counterpart of `asString()` (@Composable-only, unavailable
 * inside the host's collect): compose-resources' suspend `getString` for
 * [UiText.Resource], pass-through for [UiText.Raw]. This is the resolver the
 * shells' present adapters inject; tests inject fakes instead.
 */
suspend fun resolveUiText(text: UiText): String = when (text) {
    is UiText.Raw -> text.value
    is UiText.Resource -> getString(text.res, *text.args.toTypedArray())
}
