package com.raulshma.jellyplay.feature.player.video.engine.mpv

/**
 * Which mpv host is dispatching — the per-engine column of the
 * [MpvPropertyIntake] table. The hosts observe slightly different property
 * sets and keep a few declared behavioral divergences (the buffered-math
 * pair, `speed` caching, the desktop-only channel-count row); the column is
 * how those stay visible, test-pinned table rows instead of silent
 * per-engine `when` arms.
 */
enum class MpvIntakeHost { ANDROID, DESKTOP }

/**
 * The binding-agnostic union of raw mpv property-change payloads — what the
 * per-host value readers translate their native shapes into before calling
 * [MpvPropertyIntake.dispatch] (Android's typed `MPV.EventObserver`
 * overloads, the desktop's `MpvEventProperty` memory reads).
 */
sealed interface MpvIntakeValue {
    /** `MPV_FORMAT_FLAG`. The desktop's null-payload → `false` coercion is its reader's, not a rule here. */
    data class Flag(val value: Boolean) : MpvIntakeValue

    /** `MPV_FORMAT_INT64`. */
    data class Whole(val value: Long) : MpvIntakeValue

    /** `MPV_FORMAT_DOUBLE`. */
    data class Decimal(val value: Double) : MpvIntakeValue

    /** `MPV_FORMAT_STRING`. */
    data class Text(val value: String) : MpvIntakeValue

    /**
     * A node payload the DECISION never inspects: `track-list` (the host
     * re-enumerates through its own catalog seam) and `demuxer-cache-state`
     * (the host decodes with its own binding's node API — Android's
     * event-carried `MPVNode`, the desktop's deliberate `MpvLib.readNode`
     * re-read instead of decoding the event's raw node pointer).
     */
    data object Node : MpvIntakeValue

    /**
     * A payload the shipped host never decoded — `sid`/`aid` on the desktop
     * (the fold keys on the property name alone, so its reader hands the
     * payload over unread rather than inventing a decode for the table).
     */
    data object Unread : MpvIntakeValue
}

/**
 * One intake decision — what a property change MEANS, stripped of the hosts'
 * plumbing. [Event] results fold through [MpvFoldApplier]; the cache sinks
 * are raw numeric writes each host lands in its own fields/flows (no
 * playback-state decision — the [MpvPlaybackEvent] KDoc's split), and the
 * two `data object` sinks only NAME host seams the reader executes.
 */
sealed interface MpvPropertyIntakeResult {
    /** Fold through the host's [MpvFoldApplier]. */
    data class Event(val event: MpvPlaybackEvent) : MpvPropertyIntakeResult

    /** `time-pos` — the observer-cached position the host's ticker/gutter reads. */
    data class CachedPositionMs(val ms: Long) : MpvPropertyIntakeResult

    /** `duration` — the observer-cached demuxer duration (the server-runtime fallback ladder is host plumbing). */
    data class CachedDurationMs(val ms: Long) : MpvPropertyIntakeResult

    /** The buffered scalar — per-host formula, see the [MpvPropertyIntake] divergence list. */
    data class CachedBufferedMs(val ms: Long) : MpvPropertyIntakeResult

    /** `sub-start` — the media-time seconds stamping the next `sub-text` cue (Android's event-cached pairing). */
    data class CachedSubStartSec(val seconds: Double) : MpvPropertyIntakeResult

    /** `speed` — the desktop's cached rate (its getter reads the cache; Android's live-reads). */
    data class CachedSpeed(val speed: Float) : MpvPropertyIntakeResult

    /** `audio-params/channel-count` MOVED — rebuild the audio-effects chain against the new output layout (desktop). */
    data class ChannelLayoutChanged(val count: Int) : MpvPropertyIntakeResult

    /** `track-list` changed — re-enumerate through the host's coalescer. */
    data object RefreshTracks : MpvPropertyIntakeResult

    /** `demuxer-cache-state` changed — decode into the buffered-ranges flow (host extraction seam). */
    data object DecodeBufferedRanges : MpvPropertyIntakeResult

    /** `sub-visibility` changed — Android's debug-log-only observation (the toggle WRITE is engine-side). */
    data class SubVisibilityObserved(val visible: Boolean) : MpvPropertyIntakeResult
}

/**
 * The ONE mpv property-change → intake-decision table both mpv engines funnel
 * their observer callbacks through — the property-INTAKE half of the
 * `MpvEventFold`/`MpvFoldApplier` unification (that pair owns the fold and
 * its application; this owns the raw-surface translation whose hand-rolled
 * per-engine `when` blocks were the last mpv twin). Pure:
 * (host, property, value, context) → decision; no engine instance, no
 * Android or desktop type.
 *
 * DOCTRINE — "add an observed property":
 *  1. ONE row here: property name + wire format → [MpvPropertyIntakeResult]
 *     (an [MpvPlaybackEvent] when the change carries a playback-state
 *     decision, a cache sink when it does not), host-gated
 *     ([MpvIntakeHost]) only where the hosts genuinely diverge.
 *  2. ONE reader per engine: the `observe_property` registration at the
 *     host, then extract the raw value in the host's native callback and
 *     funnel it through [dispatch]. The reader owns extraction quirks only —
 *     pointer shapes, null-payload coercions, released guards — never
 *     decisions.
 * A divergence is a DECLARED row (`ANDROID -> ...; DESKTOP -> null`), never
 * a silent arm: grep-able, documented, pinned by `MpvPropertyIntakeTest` —
 * exactly what the former per-engine blocks could not guarantee.
 *
 * THE DECLARED DIVERGENCES (kept per-platform as shipped; converging any of
 * them is a separate product decision):
 *  - **Buffered math** (the headline pair). BOTH hosts compute the buffered
 *    scalar from `demuxer-cache-time` (absolute cache-ahead seconds, INT64)
 *    × 1000. Android ADDITIONALLY observes `demuxer-cache-duration`
 *    (DOUBLE, relative to the playhead, per-frame granularity) and computes
 *    `cached position + demuxer-cache-duration` — the finer signal its
 *    ticker publishes; the desktop does not observe that property at all.
 *    Each platform's formula is its own row below; unifying is NOT done
 *    here.
 *  - **`speed`.** The desktop caches the observed value (its `playbackSpeed`
 *    getter reads the cache); Android registers the same observation and
 *    DROPS it (its getter live-reads the property). Registering an observer
 *    and not consuming it is Android's shipped shape.
 *  - **`sub-visibility`.** Android observes it for a debug log (the toggle
 *    write lives on the engines); the desktop does not observe it at all.
 *  - **`audio-params/channel-count`.** Desktop-only: the balance `pan` af
 *    stage must rebuild when the output layout changes.
 *  - **`sub-start`.** Android-only: the event-cached cue-start pairing (the
 *    desktop reads `sub-start` LIVE at line time — its declared
 *    observed-staleness divergence, see the desktop engine's `accumulateCue`
 *    KDoc).
 *
 * `null` — no decision — deliberately covers all three "nothing to do"
 * shapes: a property this host does not observe, a property whose row drops
 * the payload for this host (Android `speed`), and a genuinely unknown
 * property name. All three were silent fall-throughs in the former blocks;
 * they are now one documented outcome.
 *
 * What deliberately stays READER-side (native quirks, listed so the table's
 * scope is explicit): the desktop's null-payload → `false` flag coercion and
 * the `FORMAT_STRING` char** dereference; Android vs desktop
 * `demuxer-cache-state` decode shapes; every host's released/teardown
 * guards; and the non-property mpv EVENTS (START_FILE / FILE_LOADED /
 * END_FILE / IDLE) — they carry engine choreography (pending-subtitle adds,
 * config apply, delayed track re-polls) and already fold through
 * [MpvEventFold] at the engine's event callback.
 *
 * Pinned by `MpvPropertyIntakeTest` (every row with representative values,
 * BOTH buffered formulas, host gating, unknown-property null, wrong-shape
 * null, and round-trips through [MpvEventFold]).
 */
object MpvPropertyIntake {

    /**
     * Dispatches one property change to its table row.
     *
     * @param positionMs the reader's cached playhead at event time — the
     *   ANDROID `demuxer-cache-duration` row's buffered formula input
     *   (ignored by every other row). No default: a forgotten argument would
     *   silently compute `0 + cache-duration`.
     * @param livePaused the LIVE `pause` property read the `eof-reached`
     *   row folds into [MpvPlaybackEvent.EofReachedChanged.pausedNow] — the
     *   eof=false arm re-derives isPlaying from it because a replay
     *   seek-back does not fire `pause`'s observer (same class as the
     *   FILE_LOADED seed). Readers default the read to `true` (paused) when
     *   no live handle exists, exactly as their shipped bodies did; only
     *   that property's arm paid the read. No default: the reader passes
     *   its defaulted read explicitly, so the table never invents one.
     * @param previousChannelCount the reader's cached last-seen
     *   `audio-params/channel-count` — the desktop row's change guard (an
     *   unchanged layout must not re-init the audio chain).
     */
    fun dispatch(
        host: MpvIntakeHost,
        property: String,
        value: MpvIntakeValue,
        positionMs: Long,
        livePaused: Boolean,
        previousChannelCount: Int? = null,
    ): MpvPropertyIntakeResult? = when (property) {
        "pause" -> (value as? MpvIntakeValue.Flag)?.let {
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.PauseChanged(it.value))
        }
        "paused-for-cache" -> (value as? MpvIntakeValue.Flag)?.let {
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.PausedForCacheChanged(it.value))
        }
        "eof-reached" -> (value as? MpvIntakeValue.Flag)?.let {
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.EofReachedChanged(it.value, livePaused))
        }
        "sid" -> trackSwitch(value, MpvPlaybackEvent.TrackKind.SUBTITLE)
        "aid" -> trackSwitch(value, MpvPlaybackEvent.TrackKind.AUDIO)
        "sub-text" -> (value as? MpvIntakeValue.Text)?.let {
            // May be the blank clear-line — the FOLD owns blank-vs-line.
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.SubTextChanged(it.value))
        }
        "time-pos" -> (value as? MpvIntakeValue.Decimal)?.let {
            // Observed as DOUBLE on both hosts: as INT64 mpv emits only
            // whole-second changes, quantizing the position to 1000 ms steps
            // (the SyncPlay drift false-positive class).
            MpvPropertyIntakeResult.CachedPositionMs((it.value * 1000L).toLong().coerceAtLeast(0L))
        }
        "duration" -> (value as? MpvIntakeValue.Decimal)?.let {
            val ms = (it.value * 1000L).toLong()
            // Declared per-host landing: Android clamps (its shipped form),
            // the desktop passes through. Practically unreachable (mpv does
            // not emit negative durations) — pinned anyway so the shapes
            // cannot drift silently.
            MpvPropertyIntakeResult.CachedDurationMs(
                if (host == MpvIntakeHost.ANDROID) ms.coerceAtLeast(0L) else ms,
            )
        }
        "demuxer-cache-time" -> (value as? MpvIntakeValue.Whole)?.let {
            // The SHARED buffered row: absolute cache-ahead seconds, both hosts.
            MpvPropertyIntakeResult.CachedBufferedMs(it.value * 1000L)
        }
        "demuxer-cache-duration" -> when {
            // DECLARED DIVERGENCE (buffered math, Android side): relative to
            // the playhead — [positionMs] is the reader's cached position at
            // event time. The desktop does not observe this property; a
            // desktop-host dispatch of it is "unobserved" (null), never a
            // silently-adopted formula.
            host != MpvIntakeHost.ANDROID -> null
            else -> (value as? MpvIntakeValue.Decimal)?.let {
                MpvPropertyIntakeResult.CachedBufferedMs(positionMs + (it.value * 1000L).toLong())
            }
        }
        "sub-start" -> if (host == MpvIntakeHost.ANDROID) {
            (value as? MpvIntakeValue.Decimal)?.let { MpvPropertyIntakeResult.CachedSubStartSec(it.value) }
        } else {
            null
        }
        "speed" -> when (host) {
            // DECLARED DIVERGENCE: Android registers the observation and
            // drops the event (its getter live-reads); only the desktop
            // caches.
            MpvIntakeHost.ANDROID -> null
            MpvIntakeHost.DESKTOP -> (value as? MpvIntakeValue.Decimal)?.let {
                MpvPropertyIntakeResult.CachedSpeed(it.value.toFloat())
            }
        }
        "sub-visibility" -> if (host == MpvIntakeHost.ANDROID) {
            (value as? MpvIntakeValue.Flag)?.let { MpvPropertyIntakeResult.SubVisibilityObserved(it.value) }
        } else {
            null
        }
        "audio-params/channel-count" -> if (host == MpvIntakeHost.DESKTOP) {
            (value as? MpvIntakeValue.Whole)?.let {
                val count = it.value.toInt()
                if (count != previousChannelCount) MpvPropertyIntakeResult.ChannelLayoutChanged(count) else null
            }
        } else {
            null
        }
        "track-list" -> if (value is MpvIntakeValue.Node) MpvPropertyIntakeResult.RefreshTracks else null
        "demuxer-cache-state" -> if (value is MpvIntakeValue.Node) {
            MpvPropertyIntakeResult.DecodeBufferedRanges
        } else {
            null
        }
        // Unknown / unobserved / dropped-for-host — one documented no-op.
        else -> null
    }

    /**
     * The `sid`/`aid` rows: the fold keys on the property name alone, so the
     * payload is either the Android reader's [MpvIntakeValue.Text] (it logs
     * the raw value) or the desktop's shipped [MpvIntakeValue.Unread].
     */
    private fun trackSwitch(value: MpvIntakeValue, kind: MpvPlaybackEvent.TrackKind): MpvPropertyIntakeResult? =
        if (value is MpvIntakeValue.Text || value is MpvIntakeValue.Unread) {
            MpvPropertyIntakeResult.Event(MpvPlaybackEvent.TrackSwitch(kind))
        } else {
            null
        }
}
