package com.raulshma.jellyplay.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.raulshma.jellyplay.core.ui.message.UserMessage
import kotlinx.coroutines.flow.Flow

/**
 * The composition half of the [UserMessageHost] seam — the remember-host +
 * present-adapter + collector-effect choreography both shells used to
 * hand-copy (Android: a host remember keyed on isTv plus two parallel
 * `LaunchedEffect` collectors in MainContent, one per bus; desktop: a host
 * remember plus one collector in DesktopNavScaffold). A shell's whole share
 * is the [present] adapter — the TV-Toast vs phone-Snackbar fork on Android,
 * the snackbar mapping on desktop — plus, for a shell-OWNED payload (the
 * [UserMessageHost.hostAdapted] case), the severity/resolver projection. One
 * call per payload shape; the host policy itself (serial merge→resolve→
 * present, severity→duration, queue-not-drop) stays in [UserMessageHost],
 * pinned by UserMessageHostTest.
 *
 * Keys: the host is constructed once per call-site lifetime (it is stateless
 * — policy only) and the collector effect keys on it plus the [sources], so
 * collection lives exactly as long as the composition that hosts it and
 * restarts only when a source's identity changes. [present] (and the adapted
 * overload's projections) ride [rememberUpdatedState] instead of effect keys:
 * a fresh lambda per recomposition — MainContent recomposes often — swaps the
 * surface in place WITHOUT restarting collection. (Deliberate delta vs. the
 * hand-copied Android collectors, which keyed on `(bus, isTv)` so a TV/phone
 * flip cancelled and relaunched collection mid-flight, dropping any message
 * in the cancellation window; the flip now only redirects the NEXT message.)
 *
 * The host is returned for symmetry with the remembers this replaces; no
 * caller needs it — the effects own it.
 *
 * @param present the shell's rendering adapter: shows the already-resolved
 *   text for the policy's [UserMessageDuration] and returns when the surface
 *   is ready for the next message (suspend on snackbar hosts, immediate on
 *   TV Toasts).
 * @param sources every message flow this shell hosts — the shared
 *   [UserMessageBus] flow plus any shell-local flows of the same type.
 */
@Composable
fun rememberShellUserMessages(
    present: suspend (String, UserMessageDuration) -> Unit,
    vararg sources: Flow<UserMessage>,
): UserMessageHost {
    val currentPresent by rememberUpdatedState(present)
    val host = remember { shellUserMessageHost { text, duration -> currentPresent(text, duration) } }
    LaunchedEffect(host, *sources) {
        host.host(*sources)
    }
    return host
}

/**
 * The same seam for a shell-OWNED message payload this module cannot name —
 * the Android shell's legacy `:core:ui` feedback bus (desktop-style shells
 * whose sources are already [UserMessage]s use the plain overload). The
 * shell supplies its [severityOf] projection onto the shared severity enum
 * and its [resolveText] (`UiText.resolve(context)` there), keeping Android
 * types out of commonMain; [vararg sources] sits before them so a call site
 * passes the bus positionally and the projections by name.
 *
 * One call per payload shape: a shell hosting BOTH a [UserMessage] source
 * and a foreign one (Android) makes two calls, preserving its per-bus
 * collector split exactly.
 */
@Composable
fun <M> rememberShellUserMessages(
    present: suspend (String, UserMessageDuration) -> Unit,
    vararg sources: Flow<M>,
    severityOf: (M) -> UserMessage.Severity,
    resolveText: suspend (M) -> String,
): UserMessageHost {
    val currentPresent by rememberUpdatedState(present)
    val currentSeverityOf by rememberUpdatedState(severityOf)
    val currentResolveText by rememberUpdatedState(resolveText)
    val host = remember { shellUserMessageHost { text, duration -> currentPresent(text, duration) } }
    LaunchedEffect(host, *sources) {
        host.hostAdapted(
            sources = sources.toList(),
            severityOf = { currentSeverityOf(it) },
            resolveText = { currentResolveText(it) },
        )
    }
    return host
}

/**
 * The host both shells' remembers build: the shared [resolveUiText] resolver
 * (compose-resources' suspend `getString`) behind the shell's [present]
 * surface. The non-compose kernel of [rememberShellUserMessages] — the one
 * wiring decision testable on the JVM (the composition keys around it are
 * compose-only).
 */
internal fun shellUserMessageHost(
    present: suspend (String, UserMessageDuration) -> Unit,
): UserMessageHost = UserMessageHost(
    resolveText = ::resolveUiText,
    present = present,
)
