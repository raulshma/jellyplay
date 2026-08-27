package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEvent

/**
 * Desktop deterministic media-key bridge (wave 14E): the missing half of the
 * wave-14A/14D focus story. The 14D re-assert makes the player Box WIN focus
 * back after every loss, but the live merged-tree pass proved the AWT/Compose
 * focus flaps in continuous focus-less gaps even while the fix worked
 * per-loss — and a key injected (or typed by a user) inside a gap died at the
 * shell's null-focus fallback chain. Focus-based delivery alone can therefore
 * never be deterministic.
 *
 * The deterministic path: the commonMain [VideoPlayerScreen] installs its
 * media-key handler here while the desktop keyboard layer is composed (same
 * jvm-gated seam family as every other platform actual — the Android actual
 * is a no-op and the install call site is behind
 * [grabsKeyboardFocusWithControlsVisible], so Android behavior is untouched),
 * and the desktop shell's `DesktopNavScaffold.onPreviewKeyEvent` — which
 * receives EVERY key with or without any Compose focus owner (the null-focus
 * fallback dispatch reaches the topmost key-input chain; ESC has worked there
 * since wave 13B) — calls [deliver] when Route.VideoPlayer is the current
 * route. The screen's own handler stays the SINGLE interpreter of media-key
 * semantics: the shell forwards raw events and nothing else.
 *
 * Double-interpretation guard lives in the screen's sink lambda: while the
 * keyboard Box subtree holds Compose focus, the sink declines (`false`) and
 * the shell returns false too, so the key flows through the normal focused
 * dispatch chain exactly once; while NO focus is held, the sink interprets
 * and the shell consumes the key — again exactly once. Either way one key
 * press produces one interpretation.
 *
 * [deliveryCount] is the wave-14E harness's "did the key provably reach the
 * player" probe: [DesktopSessionHarness] snapshots it around each SPACE
 * injection and retries when the count did not move (a flap gap swallowed the
 * key), while a moved count with no playback toggle remains a genuine FAIL.
 */
object DesktopPlayerKeyBridge {

    @Volatile
    private var sink: ((KeyEvent) -> Boolean)? = null

    @Volatile
    private var deliveryCount: Int = 0

    /** Whether a player screen's handler is installed right now. */
    val isArmed: Boolean get() = sink != null

    /** Total sink invocations since process start (harness reach-probe). */
    fun deliveryCount(): Int = deliveryCount

    /**
     * Install/remove the composing player screen's handler. Internal: only
     * the commonMain screen's jvm seam actual writes this.
     */
    internal fun install(sink: ((KeyEvent) -> Boolean)?) {
        this.sink = sink
    }

    /**
     * Offer one raw key event to the installed player handler. False when no
     * screen is composed (sheet open, route not up, non-desktop), or when the
     * handler declines (the focused dispatch chain owns the key, or the key is
     * not in the player's vocabulary) — the shell then returns false and the
     * event continues through Compose's normal dispatch unchanged.
     */
    fun deliver(event: KeyEvent): Boolean {
        val current = sink ?: return false
        deliveryCount += 1
        return runCatching { current(event) }.getOrDefault(false)
    }
}
