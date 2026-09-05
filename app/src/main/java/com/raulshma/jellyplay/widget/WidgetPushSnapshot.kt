package com.raulshma.jellyplay.widget

/**
 * Everything a full Now Playing widget push renders, read from the playback
 * manager in one pass. Deliberately pure — no Android framework types — so
 * the render-equality guards below (and the partial-vs-full push race
 * decision) are unit-testable on the JVM. NowPlayingWidgetUpdater is the
 * only holder of the "last pushed" snapshot; because the guards and the
 * pushes read the same value, they can never disagree about which values
 * were observed.
 */
internal data class WidgetPushSnapshot(
    val title: String,
    val subtitle: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val artUrl: String?,
    val isEmptyState: Boolean,
)

/** Widget progress granularity — the partial push ticks at 1 Hz anyway. */
internal const val WIDGET_POSITION_BUCKET_MS = 1_000L

/**
 * The whole-second bucket for a position/duration: the smallest position
 * delta the widget can visibly render, and the resolution at which
 * [sameRenderAs] compares progress values.
 */
internal fun positionSecondBucket(ms: Long): Long = ms / WIDGET_POSITION_BUCKET_MS

/**
 * Equality key for the last pushed widget render — see
 * NowPlayingWidgetUpdater.pushPositionUpdate. Position and duration are
 * bucketed to whole seconds because the partial push ticks at 1 Hz anyway.
 */
internal fun WidgetPushSnapshot.sameRenderAs(other: WidgetPushSnapshot): Boolean =
    title == other.title &&
        subtitle == other.subtitle &&
        isPlaying == other.isPlaying &&
        positionSecondBucket(positionMs) == positionSecondBucket(other.positionMs) &&
        positionSecondBucket(durationMs) == positionSecondBucket(other.durationMs) &&
        artUrl == other.artUrl &&
        isEmptyState == other.isEmptyState

/**
 * Equality on everything the position-only partial push cannot render.
 * [WidgetPushSnapshot.isPlaying] stays out of it: the partial push renders it
 * into the position label ("Paused ·"), and the transport icon correction
 * rides the metadata collector's full push.
 */
internal fun WidgetPushSnapshot.sameNonPositionRenderAs(other: WidgetPushSnapshot): Boolean =
    title == other.title &&
        subtitle == other.subtitle &&
        artUrl == other.artUrl &&
        isEmptyState == other.isEmptyState

/**
 * The partial-vs-full push race guard, as a pure decision: may a 1 Hz
 * position-only push go out for [snapshot] given the last fully rendered
 * [lastPushed]?
 *
 * The partial RemoteViews cannot re-render title/subtitle/artwork/
 * empty-state — if any of those moved (or no full push happened yet), the
 * answer is no: defer to the metadata collector's full push instead of
 * ticking the progress bar under stale metadata (the ticker can win the race
 * while that collector is still loading the new artwork). Otherwise the
 * answer is render equality, so no redundant partial push crosses the binder.
 */
internal fun shouldPushPartialPosition(
    lastPushed: WidgetPushSnapshot?,
    snapshot: WidgetPushSnapshot,
): Boolean {
    if (lastPushed?.sameNonPositionRenderAs(snapshot) != true) return false
    return !lastPushed.sameRenderAs(snapshot)
}
