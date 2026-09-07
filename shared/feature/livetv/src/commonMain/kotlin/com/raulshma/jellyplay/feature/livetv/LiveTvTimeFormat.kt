package com.raulshma.jellyplay.feature.livetv

import com.raulshma.jellyplay.core.model.LiveTvProgram
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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
 */
internal val LIVE_TV_TIME_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
internal val LIVE_TV_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** "Mon, Jul 14"-style label the recording schedule groups its timers by. */
internal val LIVE_TV_DATE_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Formatter behind [toInstantOrNull] (ISO_DATE_TIME: offsets optional). */
private val ISO_DATE_TIME_PARSER: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

// ─────────────────────────────────────────────────────────────────────────────
// Canonical timestamp parse
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parse the loose ISO-8601 timestamp produced by `BaseItemDto.startDate.toString()`.
 * Returns `null` on parse failure — callers should treat missing timestamps as
 * "skip this program" rather than crash.
 */
internal fun String.toInstantOrNull(): Instant? = try {
    // ISO_DATE_TIME handles offsets and, when absent, falls back to UTC via
    // LocalDateTime parsing. The Jellyfin SDK emits both forms depending on
    // server version, so we try ISO first then fall back to LocalDateTime.
    Instant.from(ISO_DATE_TIME_PARSER.parse(this))
} catch (_: DateTimeParseException) {
    null
} catch (_: java.time.DateTimeException) {
    // Instant.from() throws DateTimeException (not DateTimeParseException)
    // when the parsed TemporalAccessor lacks zone/offset info — e.g. a bare
    // LocalDateTime string. Fall back to assuming UTC.
    try {
        LocalDateTime.parse(this).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        null
    }
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
    return formatOffsetOrNaiveLocal(iso, LIVE_TV_TIME_FORMATTER) ?: iso
}

/**
 * "Mon, Jul 14"-style date label for a timer/program start timestamp (the
 * recording schedule's date groups, jellyfin-web `getTimersHtml`). Null when
 * neither parse handles the input — callers drop the row from its group.
 */
internal fun formatLiveTvDateLabel(iso: String?): String? =
    iso?.let { formatOffsetOrNaiveLocal(it, LIVE_TV_DATE_LABEL_FORMATTER) }

/**
 * The one parse-then-format core behind the wall-clock/date-label outputs:
 * ISO-offset parse first, then the naive-local fallback, else null —
 * callers map the failure their own way (raw passthrough vs drop).
 */
private fun formatOffsetOrNaiveLocal(iso: String, formatter: DateTimeFormatter): String? =
    runCatching {
        OffsetDateTime.parse(iso, LIVE_TV_TIME_PARSER).format(formatter)
    }.recoverCatching {
        naiveLocalDateTime(iso).format(formatter)
    }.getOrNull()

/**
 * The single copy of the string-munging naive-local fallback the wall-clock
 * and date-label formatters formerly carried as two verbatim duplicates.
 * Kept byte-identical — INCLUDING its JVM quirk: the T→space substitution
 * defeats `LocalDateTime.parse`, which requires the literal 'T' separator,
 * so offset-less timestamps fail BOTH parses. Pinned by
 * [LiveTvTimeFormatTest]; fix deliberately, not in passing.
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
    (start == null || !start.isAfter(now)) && (end == null || end.isAfter(now))

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
 * Instant-level core: second granularity (`epochSecond`, as the former inline
 * math), before start → 0f, after end → 1f, null when the span is zero or
 * inverted.
 */
internal fun liveProgressFraction(start: Instant, end: Instant, now: Instant): Float? {
    val totalSeconds = end.epochSecond - start.epochSecond
    if (totalSeconds <= 0) return null
    return ((now.epochSecond - start.epochSecond).toFloat() / totalSeconds).coerceIn(0f, 1f)
}
