package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.ExternalPlayerApp

/**
 * The external-player ActivityResult folded into one typed outcome. The
 * per-contract parse replaces the former single position-alias fold: the
 * resolved player app (recorded on the stashed
 * `ExternalPlayerLaunch.resolvedApp` at launch) picks the contract, and
 * the caller (ExternalPlayerHost.onResult) maps the outcome onto the
 * report-stop surface.
 *
 * No Android types cross this file — the shell drains the result
 * [android.os.Bundle] into a plain map (`toExtrasMap` in playbackhost).
 *
 * Contracts (reference: jellyfin-android `ExternalPlayer.kt`):
 *  - **MPV / mpvKt** — numeric `position` in ms: `0` (or junk) = the user
 *    cancelled, `> 0` = stopped at that position, **absent = playback
 *    completed**;
 *  - **MX Player (free/pro)** — string `end_by`: `"playback_completion"` =
 *    completed (with `position`/`duration` ms), `"user"` = stopped at
 *    `position`; anything else falls through to the generic parse;
 *  - **VLC** — `extra_position`/`extra_duration` in ms (Long): equal (or
 *    position past duration) = completed, otherwise stopped at;
 *  - **chooser arm** (unknown player) — the historical `position` /
 *    `positionMs` alias parse: a positive value = stopped at, anything else =
 *    cancelled at the start position. A foreign player reporting nothing must
 *    NOT credit completion, so the "absent = completed" MPV reading applies
 *    only when the launch actually targeted MPV/mpvKt.
 * Public: it rides the public `ExternalPlayerReports.reportExternalPlaybackStopped`
 * member (the internal class keeps consumption gated to the shell's
 * external-player launcher wiring).
 */
sealed interface ExternalPlaybackOutcome {
    /**
     * Natural end-of-media. [positionTicks] is the completion position the
     * contract reported (0 when it reports completion without a position —
     * the MPV/mpvKt shape); the reporter marks the item played explicitly.
     */
    data class Completed(val positionTicks: Long) : ExternalPlaybackOutcome

    /** The user stopped mid-stream; [positionTicks] is the stopped-at spot. */
    data class StoppedAt(val positionTicks: Long) : ExternalPlaybackOutcome

    /** The user backed out without meaningful progress — credit the start. */
    data class Cancelled(val startPositionTicks: Long) : ExternalPlaybackOutcome
}

/**
 * Parses one ActivityResult's [extras] into the [ExternalPlaybackOutcome]
 * for the launch that targeted [resolvedApp] (`null` on the chooser
 * arm — unset preference or uninstalled app). [startPositionTicks] is the
 * launch's start, credited unchanged by the cancelled arms.
 */
internal fun externalPlaybackOutcome(
    resolvedApp: ExternalPlayerApp?,
    startPositionTicks: Long,
    extras: Map<String, Any?>,
): ExternalPlaybackOutcome = when (resolvedApp) {
    ExternalPlayerApp.MPV, ExternalPlayerApp.MPV_KT -> mpvOutcome(startPositionTicks, extras)
    ExternalPlayerApp.MX_PLAYER_FREE, ExternalPlayerApp.MX_PLAYER_PRO -> mxOutcome(startPositionTicks, extras)
    ExternalPlayerApp.VLC -> vlcOutcome(startPositionTicks, extras)
    // Chooser arm (unset preference, uninstalled app): the historical
    // alias parse — never credits completion.
    else -> aliasOutcome(startPositionTicks, extras)
}

/** MPV / mpvKt: `position` 0 = cancelled, >0 = stopped-at, absent = completed. */
private fun mpvOutcome(startPositionTicks: Long, extras: Map<String, Any?>): ExternalPlaybackOutcome {
    val positionMs = extras.number("position")?.toLong() ?: return ExternalPlaybackOutcome.Completed(0L)
    return stoppedAtOrCancelled(positionMs, startPositionTicks)
}

/** MX Player: `end_by` = "playback_completion" | "user" + `position`/`duration` (ms). */
private fun mxOutcome(startPositionTicks: Long, extras: Map<String, Any?>): ExternalPlaybackOutcome =
    when (extras.string("end_by")) {
        "playback_completion" -> ExternalPlaybackOutcome.Completed(extras.number("position")?.toLong()?.toTicks() ?: 0L)
        "user" -> stoppedAtOrCancelled(extras.number("position")?.toLong(), startPositionTicks)
        // Unrecognized end_by: the generic parse is the only safe fold.
        else -> aliasOutcome(startPositionTicks, extras)
    }

/** VLC: `extra_position`/`extra_duration` (ms); equal ⇒ completed. */
private fun vlcOutcome(startPositionTicks: Long, extras: Map<String, Any?>): ExternalPlaybackOutcome {
    val positionMs = extras.number("extra_position")?.toLong()
        ?: return ExternalPlaybackOutcome.Cancelled(startPositionTicks)
    val durationMs = extras.number("extra_duration")?.toLong()
    if (durationMs != null && durationMs > 0 && positionMs >= durationMs) {
        return ExternalPlaybackOutcome.Completed(positionMs.toTicks())
    }
    return stoppedAtOrCancelled(positionMs, startPositionTicks)
}

/**
 * The chooser arm's alias parse — the former single fold ("position" wins,
 * "positionMs" aliases): a positive ms value = stopped-at, anything else
 * (absent, junk, zero, negative) = cancelled at the start position.
 */
private fun aliasOutcome(startPositionTicks: Long, extras: Map<String, Any?>): ExternalPlaybackOutcome =
    stoppedAtOrCancelled(((extras["position"] ?: extras["positionMs"]) as? Number)?.toLong(), startPositionTicks)

/**
 * The per-contract arms' shared tail: a positive reported position (ms) =
 * stopped-at that spot, anything else (absent, junk, zero, negative) =
 * cancelled at the start position.
 */
private fun stoppedAtOrCancelled(positionMs: Long?, startPositionTicks: Long): ExternalPlaybackOutcome =
    if (positionMs != null && positionMs > 0) {
        ExternalPlaybackOutcome.StoppedAt(positionMs.toTicks())
    } else {
        ExternalPlaybackOutcome.Cancelled(startPositionTicks)
    }

private fun Map<String, Any?>.number(key: String): Number? = this[key] as? Number

private fun Map<String, Any?>.string(key: String): String? = this[key] as? String

/** The file's one ms→ticks conversion (100-ns Jellyfin ticks, ×10 000). */
private fun Long.toTicks(): Long = this * 10_000
