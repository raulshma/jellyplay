package com.raulshma.jellyplay.feature.livetv.epg

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.feature.livetv.toInstantOrNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Visual layout constants for the EPG timeline grid. Values chosen so a
 * 30-minute program occupies ~120 dp (readable on both phone and TV). The
 * guide window is a 24h span, so the horizontal scroll range scales with
 * [DP_PER_MINUTE] × window minutes rather than being fixed.
 */
object EpgGridLayout {
    /** Horizontal resolution: 4 dp per minute. 30-min slot = 120 dp. */
    const val DP_PER_MINUTE: Float = 4f
    val CHANNEL_ROW_HEIGHT: Dp = 72.dp
    val TIME_HEADER_HEIGHT: Dp = 36.dp
    val CHANNEL_COLUMN_WIDTH: Dp = 112.dp
    val GRID_DIVIDER_THICKNESS: Dp = 1.dp
    /** Snap resolution for the time ruler. */
    const val TIME_SLOT_MINUTES: Long = 30L
}

/**
 * Immutable snapshot of the EPG grid data after pre-processing. All time
 * values are normalised to [Instant] for arithmetic; UI strings are derived
 * from the wall-clock local date-time in the system zone at render time.
 */
@Immutable
data class EpgGridData(
    val windowStart: Instant,
    val windowEnd: Instant,
    val rows: List<EpgChannelRow>,
) {
    val totalMinutes: Long get() = (windowEnd - windowStart).inWholeMinutes
    val totalWidthDp: Float get() = totalMinutes * EpgGridLayout.DP_PER_MINUTE
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * A program with its timestamps already parsed ([start] is never null —
 * programs with unparseable start times are dropped before this is built).
 */
@Immutable
data class TimedProgram(
    val program: LiveTvProgram,
    val start: Instant,
    val end: Instant,
)

@Immutable
data class EpgChannelRow(
    val channel: LiveTvChannel,
    val timedPrograms: List<TimedProgram>,
)

@Immutable
data class ProgramLayout(
    val program: LiveTvProgram,
    val start: Instant,
    val end: Instant,
    val startOffsetDp: Float,
    val widthDp: Float,
)

/**
 * Result of building the grid layout for a single channel row.
 */
@Immutable
data class ChannelRowLayout(
    val channel: LiveTvChannel,
    val programLayouts: List<ProgramLayout>,
)

/**
 * Build an [EpgGridData] snapshot from the raw channel/program lists.
 *
 * Behaviour:
 *  - Channels with no programs in the window are preserved as empty rows so
 *    the grid stays aligned. This matches user expectation that every live
 *    channel appears in the guide even if the upstream EPG has gaps.
 *  - Programs are filtered to those overlapping the [window] and clamped to
 *    its bounds so adjacent shows never render past the grid edge.
 *  - Rows are ordered to match [channels] (server order). Programs within a
 *    row are sorted by start time ascending.
 */
fun buildEpgGridData(
    channels: List<LiveTvChannel>,
    programs: List<LiveTvProgram>,
    windowStart: Instant,
    windowEnd: Instant,
): EpgGridData {
    val byChannel = programs.groupBy { it.channelId }
    val rows = channels.map { channel ->
        val timed = (byChannel[channel.id] ?: emptyList())
            .mapNotNull { program ->
                val start = program.startInstant() ?: return@mapNotNull null
                TimedProgram(
                    program = program,
                    start = start,
                    end = program.endInstant() ?: start,
                )
            }
            .filter { it.end > windowStart && it.start < windowEnd }
            .sortedBy { it.start.toEpochMilliseconds() }
        EpgChannelRow(
            channel = channel,
            timedPrograms = timed,
        )
    }
    return EpgGridData(
        windowStart = windowStart,
        windowEnd = windowEnd,
        rows = rows,
    )
}

/**
 * Compute the horizontal offset and width (in dp) for every program in the
 * given [row], relative to the start of [gridData.windowStart]. Programs that
 * fall partially outside the window are clamped. Gaps (empty stretches with
 * no program) are not currently emitted; the row simply renders with empty
 * space between cells.
 *
 * Purely geometric — does NOT depend on [Instant.now]. The "is this program
 * currently live" check is intentionally excluded so the layout is stable
 * across the 30s now-tick; [EpgScreen] derives live status separately via a
 * single [androidx.compose.runtime.derivedStateOf], recomposing only the cell
 * whose live state actually flips rather than re-laying-out every row.
 */
fun layoutChannelRow(
    row: EpgChannelRow,
    gridData: EpgGridData,
): ChannelRowLayout {
    val layouts = row.timedPrograms.mapNotNull { timed ->
        val clampedStart = maxOf(timed.start, gridData.windowStart)
        val clampedEnd = minOf(timed.end, gridData.windowEnd)
        if (clampedEnd <= clampedStart) return@mapNotNull null
        val startMinutes = (clampedStart - gridData.windowStart).inWholeMinutes.toFloat()
        val durationMinutes = (clampedEnd - clampedStart).inWholeMinutes.toFloat()
        ProgramLayout(
            program = timed.program,
            start = timed.start,
            end = timed.end,
            startOffsetDp = startMinutes * EpgGridLayout.DP_PER_MINUTE,
            widthDp = durationMinutes * EpgGridLayout.DP_PER_MINUTE,
        )
    }
    return ChannelRowLayout(channel = row.channel, programLayouts = layouts)
}

/**
 * Build a list of time markers for the time-header row. Returns the marker
 * instants aligned to [EpgGridLayout.TIME_SLOT_MINUTES] boundaries inside
 * the window (inclusive of the start, exclusive of the end).
 */
fun buildTimeMarkers(
    windowStart: Instant,
    windowEnd: Instant,
): List<Instant> {
    // The former `truncatedTo(ChronoUnit.HOURS)`: epoch-seconds floored to the
    // hour boundary (floorDiv matches java's floorMod-based truncation).
    val truncated = Instant.fromEpochSeconds(windowStart.epochSeconds.floorDiv(3600) * 3600)
    val alignedStart = if (truncated < windowStart) truncated.plus(1.hours) else truncated
    val markers = mutableListOf<Instant>()
    var cursor = alignedStart
    while (cursor < windowEnd) {
        markers.add(cursor)
        cursor = cursor.plus(EpgGridLayout.TIME_SLOT_MINUTES.minutes)
    }
    return markers
}

/** X offset (in dp) for a timestamp within the current window. */
fun Instant.offsetDp(windowStart: Instant): Float =
    (this - windowStart).inWholeMinutes.toFloat() * EpgGridLayout.DP_PER_MINUTE

// ─────────────────────────────────────────────────────────────────────────────
// Date parsing helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parse the loose ISO-8601 timestamp produced by `BaseItemDto.startDate.toString()`.
 * Returns `null` on parse failure — callers should treat missing timestamps as
 * "skip this program" rather than crash. The lenient parse itself is the
 * feature's one canonical ladder,
 * [com.raulshma.jellyplay.feature.livetv.toInstantOrNull].
 */
fun LiveTvProgram.startInstant(): Instant? = startDate?.toInstantOrNull()

fun LiveTvProgram.endInstant(): Instant? =
    endDate?.toInstantOrNull() ?: startDate?.toInstantOrNull()

/** Format an [Instant] for the time-header (e.g. "14:30"). */
fun Instant.formatTimeHeader(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
