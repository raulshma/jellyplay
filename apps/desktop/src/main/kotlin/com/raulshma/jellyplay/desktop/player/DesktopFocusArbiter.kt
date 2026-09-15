package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.data.playback.focus.FocusArbiter
import com.raulshma.jellyplay.core.data.playback.focus.FocusAudioAttributes
import com.raulshma.jellyplay.core.data.playback.focus.FocusListener

/**
 * The desktop [FocusArbiter] twin (ADR-0004 slice 2): the in-process
 * "always-arbitrating" seat behind the REAL [com.raulshma.jellyplay.core.data.playback.focus.DefaultPlaybackFocus]
 * module. There is no OS audio-focus authority on the desktop — no other app
 * can suspend this process's audio and nothing re-grants it — so the port's
 * grant semantics are vacuously true (the [com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus]
 * precedent: where exclusivity cannot be challenged, granting is the honest
 * verdict) and the accepted [FocusListener] is NEVER invoked: no event can
 * occur, so no claim can ever land Suspended and resume stays manual by
 * construction, not by filtering.
 *
 * What this twin deliberately does NOT short-circuit is everything AROUND
 * the verdict: the executor still runs the full matrix (victim pauses, the
 * Held/Suspended phase machine, newest-wins eviction) and still publishes
 * claim-state — which is the whole point of slice 2's desktop leg. [abandon]
 * is bookkeeping-free (there is no seat to return); [FocusAudioAttributes]
 * are accepted and ignored (no OS reads them here; the parameter exists so
 * the port shape — and therefore the migration slice — stays platform-flat).
 */
internal class DesktopFocusArbiter : FocusArbiter {
    override fun request(attributes: FocusAudioAttributes, listener: FocusListener): Boolean = true
    override fun abandon() {}
}
