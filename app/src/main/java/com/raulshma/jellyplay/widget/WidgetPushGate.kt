package com.raulshma.jellyplay.widget

import android.graphics.Bitmap

/**
 * The push-decision state behind [NowPlayingWidgetUpdater]: everything the
 * metadata and position collectors used to juggle as loose mutable fields
 * (`lastPushedRender` / `lastItemId` / `lastArtwork` across start/stop),
 * extracted as pure decisions so the choreography stays JVM-testable — the
 * gate makes no Android framework calls (the Full decision merely CARRIES
 * the caller-loaded [android.graphics.Bitmap], so JVM tests exercise the
 * null-art row); the updater keeps flow collection, manager
 * re-reads and RemoteViews submission.
 *
 * [WidgetPushSnapshot] remains the single comparison observable: the gate
 * only compares and records snapshots the caller re-read from the playback
 * manager, never the metadata trigger that preceded them.
 */
internal class WidgetPushGate {

    /** What the updater should do with a freshly re-read snapshot. */
    sealed interface Decision {
        /** Send a full RemoteViews push; [albumArt] is the retained poster. */
        data class Full(val albumArt: Bitmap?) : Decision

        /** Send the position-only partial push (position label + bar). */
        data object Partial : Decision

        /** Nothing to cross the binder for. */
        data object Skip : Decision
    }

    // Read/written from both collectors' coroutines on the Dispatchers.Default
    // pool — volatile so a position-tick thread always sees the metadata push
    // that just landed. Compared via [sameRenderAs], never structural equals.
    @Volatile private var lastPushedRender: WidgetPushSnapshot? = null

    // Only the metadata collector touches these (reset aside) — same
    // (non-volatile) discipline as the updater fields this gate replaced.
    private var lastItemId: String? = null
    private var lastArtwork: Bitmap? = null

    /**
     * The metadata collector's decision point. [triggerItemId] is the changed
     * metadata's item (a different item drops the retained poster — it belongs
     * to the previous episode); [loadedArt] the poster loaded for the trigger
     * (a failed load is ignored — the previous poster rides along); [pushed]
     * is the snapshot RE-READ from the manager after the artwork load.
     *
     * [pushed], not the trigger, is what gets recorded and returned: the
     * pushed render wins over the metadata that started the push. Always a
     * full push — the metadata collector fires only on distinct metadata
     * changes and must be able to re-render anything (including the poster
     * the position push cannot draw).
     */
    fun decideOnMetadata(
        triggerItemId: String?,
        loadedArt: Bitmap?,
        pushed: WidgetPushSnapshot,
    ): Decision {
        if (triggerItemId != lastItemId) {
            lastItemId = triggerItemId
            lastArtwork = null
        }
        if (loadedArt != null) {
            lastArtwork = loadedArt
        }
        lastPushedRender = pushed
        return Decision.Full(lastArtwork)
    }

    /**
     * The 1 Hz position ticker's decision point: a partial push only when
     * [shouldPushPartialPosition] says the last full render can be safely
     * ticked over (and the render actually moved) — otherwise skip; the
     * metadata collector owns anything a partial cannot re-render.
     */
    fun decideOnPositionTick(pushed: WidgetPushSnapshot): Decision {
        if (!shouldPushPartialPosition(lastPushedRender, pushed)) return Decision.Skip
        lastPushedRender = pushed
        return Decision.Partial
    }

    /**
     * Presence off / `stop()`: nothing survives into the next active cycle —
     * the next metadata decision is a first push (full) again, and position
     * pushes defer until it lands.
     */
    fun reset() {
        lastPushedRender = null
        lastItemId = null
        lastArtwork = null
    }
}
