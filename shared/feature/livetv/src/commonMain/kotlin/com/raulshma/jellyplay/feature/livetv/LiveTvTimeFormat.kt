package com.raulshma.jellyplay.feature.livetv

import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.model.LiveTvProgram
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toInstant

/**
 * The Live-TV feature's ONE timestamp/airing vocabulary. Every screen and
 * ViewModel in shared/feature/livetv parses Jellyfin program/timer
 * timestamps and derives airing state through this module (pure commonMain,
 * Compose-free) — no per-caller `Instant.parse`/`runCatching` ladders:
 *  - [toInstantOrNull] — the canonical lenient ISO parse (the EPG grid's
 *    former ladder, kept verbatim).
 *  - [formatLiveTvTime] / [formatLiveTvDateLabel] — the wall-clock and
 *    date-label outputs over one shared parse-then-format core.
 *  - [isAiringAt] — the single "is airing now" predicate.
 *  - [liveProgressFraction] — channel detail's live progress fold.
 *  - [LIVE_TV_STALENESS_INTERVAL_MS] + [nowInstant] — the shared clock
 *    reads: the 5-minute staleness cadence and the injected-clock Instant
 *    bridge (no direct `Clock.System.now()` in the ViewModels).
 *
 * This module is pure kotlinx-datetime (no java.time in commonMain).
 * The parse ladder rides
 * `DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET` (the
 * `DateTimeFormatter.ISO_OFFSET_DATE_TIME` counterpart), the EPG grid's
 * `HH:mm` header is manual component derivation, and the two user-facing
 * renderers are expect/actual (below).
 * Locale behavior: the JVM actual formats through java.time with the
 * default FORMAT locale, so desktop/android keep the localized AM/PM and
 * month/day names the former java.time formatters produced.
 *  - [toInstantOrNull]'s offset leg versus the java `ISO_OFFSET_DATE_TIME`
 *    it replaced, honestly: kotlinx requires SECONDS in the offset forms
 *    java accepted without them ("2026-01-02T03:04+02:00" no longer parses
 *    on the offset leg — it falls through to the naive-local UTC leg), and
 *    the bare-hours offset java's lenient extra ("+02") now parses where
 *    java rejected it. Jellyfin always emits ±HH:MM with seconds, so no
 *    real payload moves legs; pinned by [LiveTvTimeFormatTest].
 *  - The naive-local fallback quirk (below) is reproduced exactly.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Canonical timestamp parse
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parse the loose ISO-8601 timestamp produced by `BaseItemDto.startDate.toString()`.
 * Returns `null` on parse failure — callers should treat missing timestamps as
 * "skip this program" rather than crash.
 */
internal fun String.toInstantOrNull(): Instant? {
    // ISO offset parse first (ISO_OFFSET_DATE_TIME's counterpart — requires
    // the offset, so an offset-less input skips this leg exactly like the
    // former `Instant.from(ISO_DATE_TIME)` DateTimeException hop did).
    offsetDateTimeOf(this)?.let { (local, offset) -> return local.toInstant(offset) }
    // Offset-less input: strict ISO local (the 'T' separator is required),
    // read as UTC — the C10 fallback the offset-less fixtures ride.
    return runCatching { LocalDateTime.parse(this).toInstant(UtcOffset.ZERO) }.getOrNull()
}

/**
 * The one ISO-offset parse behind [toInstantOrNull] and the wall-clock
 * formatters: `null` unless the string carries BOTH a valid ISO date-time and
 * an explicit offset ('Z' or ±HH:MM).
 */
private fun offsetDateTimeOf(iso: String): Pair<LocalDateTime, UtcOffset>? {
    val parsed = runCatching { DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET.parse(iso) }.getOrNull()
        ?: return null
    val offset = runCatching { parsed.toUtcOffset() }.getOrNull() ?: return null
    return parsed.toLocalDateTime() to offset
}

// ─────────────────────────────────────────────────────────────────────────────
// Wall-clock / date-label formatters
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lenient start/end-time formatting ("h:mm a"), built once instead of per row
 * per recomposition (the EpgGridLayout top-level-formatter pattern). Both the
 * channel-detail program timeline and the recording schedule render the same
 * ISO-offset timestamps. Null/blank input maps to null so callers choose
 * their own placeholder ("" for timer rows, "--" for program times); input
 * neither parse handles is returned raw so the row still shows something
 * identifiable.
 */
internal fun formatLiveTvTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return formatOffsetOrNaiveLocal(iso, ::formatWallClockTime) ?: iso
}

/**
 * "Mon, Jul 14"-style date label for a timer/program start timestamp (the
 * recording schedule's date groups, jellyfin-web `getTimersHtml`). Null when
 * neither parse handles the input — callers drop the row from its group.
 */
internal fun formatLiveTvDateLabel(iso: String?): String? =
    iso?.let { formatOffsetOrNaiveLocal(it, ::formatDateLabel) }

/**
 * The one parse-then-format core behind the wall-clock/date-label outputs:
 * ISO-offset parse first, then the naive-local fallback, else null —
 * callers map the failure their own way (raw passthrough vs drop).
 */
private fun formatOffsetOrNaiveLocal(iso: String, format: (LocalDateTime) -> String): String? =
    runCatching { format(offsetWallClock(iso)) }
        .recoverCatching { format(naiveLocalDateTime(iso)) }
        .getOrNull()

/** The offset-required wall-clock read behind the primary format leg. */
private fun offsetWallClock(iso: String): LocalDateTime = offsetDateTimeOf(iso)
    ?.first
    ?: error("No UTC offset in $iso")

/**
 * "h:mm a" — unpadded 12-hour clock + space-padded AM/PM marker (java's
 * single-'h'/'a' output, pinned "2:30 PM"/"12:05 AM" by [LiveTvTimeFormatTest]
 * under the US FORMAT locale). Locale resolution is per-platform: the JVM
 * actual formats via java.time with the default FORMAT locale (localized
 * markers).
 */
internal expect fun formatWallClockTime(local: LocalDateTime): String

/** "EEE, MMM d" — unpadded day ("Mon, Jun 22"), locale-resolved per platform
 * exactly like [formatWallClockTime] (JVM localized via java.time). */
internal expect fun formatDateLabel(local: LocalDateTime): String

/**
 * The single copy of the string-munging naive-local fallback the wall-clock
 * and date-label formatters formerly carried as two verbatim duplicates.
 * Kept byte-identical — INCLUDING its JVM quirk: the T→space substitution
 * defeats `LocalDateTime.parse`, which requires the literal 'T' separator
 * (kotlinx-datetime's strict ISO parse does the same), so offset-less
 * timestamps fail BOTH parses. Pinned by [LiveTvTimeFormatTest]; fix
 * deliberately, not in passing.
 */
private fun naiveLocalDateTime(iso: String): LocalDateTime = LocalDateTime.parse(
    iso.replace("Z", "").replace("T", " ").substringBefore('+').trim()
)

// ─────────────────────────────────────────────────────────────────────────────
// Airing checks
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Whether [program] is on air at [now]: `start <= now < end`, with its
 * timestamps parsed through the canonical lenient [toInstantOrNull] ladder.
 *
 * DECLARED BEHAVIOR FIX (the C10 timestamp-vocabulary unification): the
 * channel-detail ViewModel and content used to strict-parse the same strings
 * (`runCatching { Instant.parse(it) }`), which returns null for exactly the
 * offset-less forms this ladder accepts — so an unparseable endDate kept an
 * ended program in the "upcoming" filter while the airing check and the live
 * progress bar disagreed with it. All three now read the same Instants:
 * offset-less strings parse (as UTC), and the filter, airing check and
 * progress fraction stay consistent. Null/blank/garbage timestamps remain
 * LENIENT (null start = "already started", null end = "not yet ended") — the
 * former ChannelDetailViewModel semantics, now written once.
 */
internal fun isAiringAt(program: LiveTvProgram, now: Instant): Boolean = isAiringAt(
    start = program.startDate?.toInstantOrNull(),
    end = program.endDate?.toInstantOrNull(),
    now = now,
)

/**
 * Instant-level core: the half-open `[start, end)` window, start-inclusive
 * and end-exclusive, where a null bound means "no constraint on that side"
 * (missing/unparseable timestamp). The single "is airing now" predicate —
 * the EPG grid (over already-parsed layout Instants) and channel detail
 * (over raw ISO strings) share it.
 */
internal fun isAiringAt(start: Instant?, end: Instant?, now: Instant): Boolean =
    (start == null || start <= now) && (end == null || end > now)

// ─────────────────────────────────────────────────────────────────────────────
// Live progress
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Live progress fraction (0..1) of [program] at [now], or null when either
 * timestamp is missing/unparseable or the span is non-positive (callers
 * render 0f). Folds channel detail's former inline epochSecond math;
 * offset-less timestamps now parse via [toInstantOrNull] (part of the C10
 * unification — they previously forced 0f).
 */
internal fun liveProgressFraction(program: LiveTvProgram, now: Instant): Float? {
    val start = program.startDate?.toInstantOrNull() ?: return null
    val end = program.endDate?.toInstantOrNull() ?: return null
    return liveProgressFraction(start = start, end = end, now = now)
}

/**
 * Instant-level core: second granularity (`epochSeconds`, as the former inline
 * math), before start → 0f, after end → 1f, null when the span is zero or
 * inverted.
 */
internal fun liveProgressFraction(start: Instant, end: Instant, now: Instant): Float? {
    val totalSeconds = end.epochSeconds - start.epochSeconds
    if (totalSeconds <= 0) return null
    return ((now.epochSeconds - start.epochSeconds).toFloat() / totalSeconds).coerceIn(0f, 1f)
}

// ─────────────────────────────────────────────────────────────────────────────
// Clock reads
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The Live-TV staleness cadence: how long fetched data may age before a full
 * re-fetch. One constant for both jellyfin-web-derived throttles that
 * separately re-declared the same 5 minutes — the EPG guide auto-refresh
 * loop and the Programs tab's full-render throttle (a re-entry within the
 * window only refreshes the "On Now" row).
 */
internal const val LIVE_TV_STALENESS_INTERVAL_MS: Long = 5 * 60 * 1000L

/**
 * Wall-clock "now" through the injected [EpochMillisSource] seam (the
 * commonMain clock slice; the JVM graph binds it to the SystemTimeSource
 * single — fake-able in jvmTest) —
 * the Live-TV ViewModels' only Instant read, never a direct clock read.
 * Public (the LiveNowWindow convergence) so player-live's
 * LiveTvPlayerViewModel reads the same bridge instead of a raw
 * `Clock.System.now()`; the livetv VMs consume it internally.
 *
 * the receiver narrowed from the jvmShared `TimeSource` to its
 * commonMain [EpochMillisSource] slice — the ViewModels only ever read
 * `nowEpochMillis` (the `today(zone)`/monotonic surface was unused here), so
 * no jvmShared core:data type crosses into the module's commonMain.
 */
fun EpochMillisSource.nowInstant(): Instant = Instant.fromEpochMilliseconds(nowEpochMillis())
