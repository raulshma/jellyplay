package com.raulshma.jellyplay.feature.player.video.session

import kotlin.math.abs

/**
 * The handled-offer marker for the cold-start restore prompt — the feature's
 * one piece of local storage (per device, never backed up; the resume data
 * itself stays server-authoritative). Two arms write it:
 *
 *  - the prompt's DISMISS arm (both shells' dialog hosts, through
 *    [SessionRestorationController.onAskDismissed]) — the exact offer was
 *    surfaced and refused. This used to clear only in-memory state, so the
 *    same fresh (≤ 1 h) offer re-asked on every cold start.
 *  - the player's TEARDOWN stop report ([playbackHandledFingerprint], wired
 *    through the `SessionPositionLedger`'s `onTeardown` bracket) — the row's
 *    state was played through and deliberately closed. Without this arm the
 *    confirm-then-close cycle (resume → watch → close player → close app →
 *    relaunch) re-asked too: the confirm never wrote a marker, and the
 *    playback itself ADVANCED the row's stamp past any marker the dismiss
 *    arm could have left.
 *
 * The marker stores the FINGERPRINT of the row state that was handled; the
 * next launch's evaluation ([SessionRestorationPolicy] rule 6) recognizes it
 * and stays silent. Format `itemId|stamp|position` (stamp empty when the
 * writer doesn't know the server's date), matched by [isDismissedOffer]: the
 * item must be the same, and then a stored stamp decides — a stamp is the
 * DISMISS arm's exact-offer identity and is self-invalidating (any new
 * playback advances the row's stamp, the fingerprints diverge, genuinely new
 * sessions prompt again); a stamp-less stored marker matches the POSITION
 * WITHIN [HANDLED_POSITION_TOLERANCE_MS] instead. The tolerance is not
 * sloppiness, it is the point: the row position after a deliberate close is
 * NOT reliably the stop report's position — the process can die before the
 * best-effort stop lands (close player → close app in one motion), and even
 * a delivered stop can be overwritten by the periodic progress loop's last
 * in-flight cycle. The row also carries the last ~10 s progress report at
 * best. Matching the HANDLED state within a minute keeps the close cycle
 * silent while any REAL further playback (here or on another device) moves
 * the row beyond the window and re-arms the prompt. This branch is also the
 * stamp-less-server fallback for the dismiss arm, in the same spirit as the
 * policy's freshness rule.
 *
 * The legacy two-part form `itemId|stampOrPosition` (the dismiss arm's format
 * before the teardown arm existed) keeps its original exact-offer meaning.
 */

/**
 * How far the row's position may sit from a stamp-less handled marker's
 * position and still count as the same handled state — the stop-vs-progress
 * race window (see [isDismissedOffer]).
 */
const val HANDLED_POSITION_TOLERANCE_MS = 60_000L

/** The identity of the offered restore the dialog's dismiss arm handled. */
fun dismissalFingerprint(snapshot: SessionRestorationSnapshot): String =
    handledOfferFingerprint(snapshot.itemId, snapshot.timestampMs, snapshot.positionMs)

/**
 * The identity of the resume state a deliberate player teardown just handled:
 * item + the final reported position. The next launch's row may not carry
 * EXACTLY this value (the process can die before the stop report lands, and
 * a delivered stop can be overwritten by the progress loop's last cycle), so
 * the matcher allows [HANDLED_POSITION_TOLERANCE_MS] of drift. The server's
 * stop date is deliberately not approximated here.
 */
fun playbackHandledFingerprint(itemId: String, finalPositionMs: Long): String =
    handledOfferFingerprint(itemId, timestampMs = null, positionMs = finalPositionMs)

private fun handledOfferFingerprint(itemId: String, timestampMs: Long?, positionMs: Long): String {
    val stamp = timestampMs?.takeIf { it > 0L }?.toString().orEmpty()
    return "$itemId|$stamp|$positionMs"
}

/** Whether [stored] (the persisted marker; `null`/empty = none) is this offer. */
fun isDismissedOffer(stored: String?, snapshot: SessionRestorationSnapshot): Boolean {
    if (stored.isNullOrEmpty()) return false
    val parts = stored.split('|')
    if (parts.first() != snapshot.itemId) return false
    return when (parts.size) {
        // Legacy pre-teardown form: itemId|stamp — the row's stamp when it was
        // dated, else the position.
        2 -> parts[1].toLongOrNull()?.let { legacy ->
            legacy == (snapshot.timestampMs?.takeIf { it > 0L } ?: snapshot.positionMs)
        } == true
        3 -> {
            val stamp = parts[1].toLongOrNull()
            if (stamp != null) {
                stamp == snapshot.timestampMs
            } else {
                val storedPosition = parts[2].toLongOrNull()
                storedPosition != null &&
                    abs(storedPosition - snapshot.positionMs) <= HANDLED_POSITION_TOLERANCE_MS
            }
        }
        else -> false
    }
}
