package com.raulshma.jellyplay.feature.player.video.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The sleep-timer sheet's two pure policies, extracted so the truth
 * table is JVM-testable without composition:
 *  - the cancel-confirmation threshold — cancelling with MORE than five
 *    minutes left asks for confirmation (a fat-finger cancel of a long
 *    timer is the expensive accident); five minutes or less cancels
 *    immediately, and the end-of-episode arm (no countdown) always does;
 *  - the projected stop wall-clock time — `now + remaining` rendered with
 *    the host's 12/24-hour short-time convention (the same
 *    "HH:mm" / "h:mm a" pattern pair the player's "Ends at" label rides,
 *    `rememberEndsAtTime`). Formatting is the module's own
 *    `SimpleDateFormat` idiom — this commonMain legitimately carries
 *    `java.*` (android+jvm-only targets), so no new datetime dependency.
 */
internal object SleepTimerSheetPolicy {

    /** More than this much remaining and a cancel press asks for confirmation. */
    const val CANCEL_CONFIRM_THRESHOLD_MS: Long = 5 * 60_000L

    /**
     * True when a cancel press needs confirmation: strictly more than
     * [CANCEL_CONFIRM_THRESHOLD_MS] left. The boundary itself (exactly
     * 5:00) cancels immediately — the user is this close to expiry anyway.
     */
    fun requiresCancelConfirmation(remainingMs: Long): Boolean =
        remainingMs > CANCEL_CONFIRM_THRESHOLD_MS

    /**
     * The projected stop instant: `now + remaining` (remaining clamped at 0
     * so a straggling late tick projects "now", never the past).
     */
    fun projectedStopEpochMs(nowEpochMs: Long, remainingMs: Long): Long =
        nowEpochMs + remainingMs.coerceAtLeast(0L)

    /**
     * Short wall-clock label ("23:45" / "11:45 PM") for a stop instant, in
     * the host locale. [locale] is a parameter for locale-pinned tests;
     * production calls rely on the default.
     */
    fun formatStopTime(stopEpochMs: Long, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, locale).format(Date(stopEpochMs))
    }
}
