package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.concurrency.Settlement
import com.raulshma.jellyplay.core.concurrency.settleDebounce
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.ui.components.TrickplayPeekTile
import com.raulshma.jellyplay.core.ui.components.LocalPerformanceMode
import com.raulshma.jellyplay.core.ui.image.decodeImageBytes
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * The `home_trickplay_preview` pref mirror (H13), provided by the home screen
 * next to the other screen-scoped locals (the quick-action controller). The
 * default is the feature-off posture: only a host that read the pref provides
 * `true`, so a wide row hosted outside home renders no peek.
 */
val LocalHomeTrickplayPreview: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/** How long the peek holds above the progress bar before fading out. */
private const val PEEK_HOLD_MS = 2_500L

/**
 * Longest sprite-sheet edge allowed through the bounded decoder. Jellyfin
 * generates sheets at 320–640 px tile widths on ≤10-column grids, so 2048 px
 * keeps every real sheet at native resolution while a hostile payload cannot
 * force a multi-hundred-megabyte decode.
 */
private const val PEEK_SHEET_MAX_EDGE_PX = 2_048

/**
 * H13's gate, in its pure form: the TV focus peek loads only when every
 * suppression leg holds — non-TV never (touch: v1 none), the pref off never,
 * performance mode never, and an item without the projected `Trickplay`
 * element or without a resume position has no tile to show (and no position
 * to resolve one at). Null `trickplayInfo` means "not fetched / offline row",
 * which degrades to nothing rather than a guess — never a server fetch on a
 * maybe.
 */
internal fun shouldLoadTrickplayPeek(
    isTv: Boolean,
    prefEnabled: Boolean,
    performanceMode: Boolean,
    trickplayInfo: TrickplayInfo?,
    playbackPositionTicks: Long?,
): Boolean =
    isTv &&
        prefEnabled &&
        !performanceMode &&
        trickplayInfo != null &&
        playbackPositionTicks != null &&
        playbackPositionTicks > 0L

/** Where the resume position's preview tile lives: the thumbnail index inside the whole strip, and the sprite sheet holding it. */
internal data class TrickplayTileLocation(
    val thumbnailIndex: Int,
    val sheetIndex: Int,
)

/**
 * The tile to preview for a playback position — the player's
 * [com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager]
 * index math hoisted pure (same formula, no bitmap dependency) so the home
 * peek resolves the SAME frame the player's scrub bar would:
 * `index = position / interval`, clamped to the strip; the sheet is
 * `index / (tileWidth × tileHeight)`. A degenerate interval (0, from a stale
 * server payload) coerces to 1 ms like the player's guard does.
 */
internal fun trickplayTileAt(positionMs: Long, info: TrickplayInfo): TrickplayTileLocation {
    val interval = info.interval.coerceAtLeast(1).toLong()
    val thumbnailIndex = (positionMs / interval).toInt()
        .coerceIn(0, (info.thumbnailCount - 1).coerceAtLeast(0))
    val tilesPerSheet = (info.tileWidth * info.tileHeight).coerceAtLeast(1)
    return TrickplayTileLocation(
        thumbnailIndex = thumbnailIndex,
        sheetIndex = thumbnailIndex / tilesPerSheet,
    )
}

/**
 * H13's per-card peek loader for the TV focus: resolves the focused wide
 * card's resume-position tile into a [TrickplayPeekTile], or null while
 * nothing should show. The choreography reuses the H10 settle debounce
 * ([HOME_FOCUS_SETTLE_MS]) so the backdrop repaint and the peek move in
 * lockstep:
 *  - the gate (pref ∧ TV ∧ ¬performance ∧ projected Trickplay ∧ resume
 *    position) failing short-circuits to null — no flow, no fetch;
 *  - on SETTLED focus (400 ms quiet), the sprite sheet holding the tile is
 *    fetched via [PlaybackRepository.getTrickplayTileImage] and decoded on a
 *    background dispatcher, then the peek holds for [PEEK_HOLD_MS] before
 *    clearing (the overlay fades the flip out);
 *  - a refocus mid-flight cancels the settle, the fetch and the hold via
 *    `collectLatest`; the peek drops immediately when it belonged to another
 *    item or focus left the card.
 *
 * @param item the card's (lifted) [MediaItem]; the effect re-keys on it so a
 *  row refetch with a new resume position re-resolves the tile.
 * @param isFocused state-reading provider for the card's TV focus; read inside
 *  [snapshotFlow] so focus changes re-emit.
 */
@Composable
internal fun rememberWideCardTrickplayPeek(
    item: MediaItem,
    isFocused: () -> Boolean,
): TrickplayPeekTile? {
    val isTv = LocalTvMode.current
    val prefEnabled = LocalHomeTrickplayPreview.current
    val performanceMode = LocalPerformanceMode.current
    val enabled = shouldLoadTrickplayPeek(
        isTv = isTv,
        prefEnabled = prefEnabled,
        performanceMode = performanceMode,
        trickplayInfo = item.trickplayInfo,
        playbackPositionTicks = item.playbackPositionTicks,
    )
    // The gate failing short-circuits the whole body — no repository lookup,
    // no effect, no state (the touch/desktop composition pays for nothing but
    // the gate's booleans).
    if (!enabled) return null

    val repository = org.koin.compose.koinInject<PlaybackRepository>()
    val currentRepository by rememberUpdatedState(repository)
    var loaded by remember { mutableStateOf<TrickplayPeekTile?>(null) }

    LaunchedEffect(item) {
        snapshotFlow(isFocused)
            .settleDebounce(settleMillis = HOME_FOCUS_SETTLE_MS)
            .collectLatest { settlement ->
                val focused = settlement.value
                if (!focused) {
                    loaded = null
                    return@collectLatest
                }
                if (settlement is Settlement.Arrived) {
                    // Arrival does the zero-cost half: drop a peek that
                    // belongs to an earlier item; the loaded tile for THIS
                    // item (a refocus back inside the hold window) keeps
                    // showing.
                    val current = loaded
                    if (current != null && current.itemId != item.id) loaded = null
                    return@collectLatest
                }
                val info = item.trickplayInfo ?: return@collectLatest
                val positionMs = (item.playbackPositionTicks ?: 0L) / 10_000L
                if (positionMs <= 0L) return@collectLatest
                val location = trickplayTileAt(positionMs, info)
                val sheetBytes = currentRepository.getTrickplayTileImage(
                    itemId = item.id,
                    width = info.width,
                    index = location.sheetIndex,
                ) ?: return@collectLatest
                val sheet = withContext(Dispatchers.Default) {
                    decodeImageBytes(sheetBytes, PEEK_SHEET_MAX_EDGE_PX)
                } ?: return@collectLatest
                loaded = TrickplayPeekTile(
                    sheet = sheet,
                    info = info,
                    thumbnailIndex = location.thumbnailIndex,
                    itemId = item.id,
                )
                // Hold above the progress bar, then fade (the overlay animates
                // the null flip out). A refocus during the hold cancels here.
                delay(PEEK_HOLD_MS)
                loaded = null
            }
    }
    // The slot must never show a tile from a previous item the row reused.
    return loaded?.takeIf { it.itemId == item.id }
}
