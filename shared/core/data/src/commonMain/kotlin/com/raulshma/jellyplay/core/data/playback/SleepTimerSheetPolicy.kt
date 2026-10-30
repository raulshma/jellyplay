package com.raulshma.jellyplay.core.data.playback

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The sleep-timer sheet's presentation truth table — the ONE home beside
 * [SleepCountdown] / [SleepTimerArming], consumed by every host sheet (video,
 * audio; the labels are each module's own string resources, the decisions are
 * shared). Formerly one verbatim copy per host module, each confessing it was
 * the twin of the other. Two pure policies, JVM-testable without composition:
 *  - the cancel-confirmation threshold — cancelling with MORE than five
 *    minutes left asks for confirmation (a fat-finger cancel of a long timer
 *    is the expensive accident); five minutes or less cancels immediately,
 *    and the end-of-episode arm (no countdown) always does;
 *  - the projected stop wall-clock time — `now + remaining` rendered with
 *    the host's 12/24-hour short-time convention ("HH:mm" / "h:mm a", the
 *    pattern pair core:ui's wall-clock labels ride; the 24-hour fact itself
 *    is core:ui's `rememberIs24HourFormat` seam (core:data doesn't depend on
 *    core:ui, so it's passed in here and this policy stays a pure function).
 *
 * This commonMain legitimately carries `java.*` — every `shared/` module
 * compiles for the JVM-shaped targets only (android + desktop).
 */
public object SleepTimerSheetPolicy {

    /** More than this much remaining and a cancel press asks for confirmation. */
    public const val CANCEL_CONFIRM_THRESHOLD_MS: Long = 5 * 60_000L

    /**
     * True when a cancel press needs confirmation: strictly more than
     * [CANCEL_CONFIRM_THRESHOLD_MS] left. The boundary itself (exactly
     * 5:00) cancels immediately — the user is this close to expiry anyway.
     */
    public fun requiresCancelConfirmation(remainingMs: Long): Boolean =
        remainingMs > CANCEL_CONFIRM_THRESHOLD_MS

    /**
     * The projected stop instant: `now + remaining` (remaining clamped at 0
     * so a straggling late tick projects "now", never the past).
     */
    public fun projectedStopEpochMs(nowEpochMs: Long, remainingMs: Long): Long =
        nowEpochMs + remainingMs.coerceAtLeast(0L)

    /**
     * Short wall-clock label ("23:45" / "11:45 PM") for a stop instant, in
     * the host locale. [locale] is a parameter for locale-pinned tests;
     * production calls rely on the default.
     */
    public fun formatStopTime(stopEpochMs: Long, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, locale).format(Date(stopEpochMs))
    }
}

/**
 * The ONE preset duration ladder behind the player hosts' sleep-timer chips
 * (15m → 90m): every host sheet renders these rungs in order and resolves
 * its own labels (the rows own their wording), so a new rung or re-order
 * lands once and every player's picker moves with it. The end-of-episode arm
 * is not a rung — it is [SleepCountdown]'s boundary mode, armed separately.
 */
public val SLEEP_TIMER_PRESET_MS: List<Long> = listOf(
    15 * 60 * 1000L,
    30 * 60 * 1000L,
    45 * 60 * 1000L,
    60 * 60 * 1000L,
    90 * 60 * 1000L,
)
