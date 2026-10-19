package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.ui.components.localDateFromIsoTimestamp
import kotlinx.datetime.LocalDate

/**
 * The "Airing next" strip's pure presentation: the air-date badge decision
 * and the countdown arithmetic. Compose-free and directly unit-testable
 * (the [missingEpisodeBadge] / [EpisodeRowPresentation] pattern); the
 * renderer composes the labels around them, nothing else.
 */

/**
 * The air-date badge for one strip card, REUSING the virtual-episode row's
 * [MissingEpisodeBadge.Airs] vocabulary: an Upcoming episode is by
 * construction unaired, but unlike a seasons-tree placeholder it carries no
 * `MissingEpisodeReason.UNAIRED` marker (the `/Shows/Upcoming` mapper leaves
 * the virtual fields unset), so the badge is derived from the premiere stamp
 * alone. Null when the stamp is absent or unparseable — the card renders no
 * badge rather than a "Missing" one (an upcoming episode is never missing).
 */
internal fun upcomingAirBadge(premiereDate: String?): MissingEpisodeBadge? {
    val date = premiereDate?.let(::localDateFromIsoTimestamp) ?: return null
    return MissingEpisodeBadge.Airs(date)
}

/**
 * Whole days from [today] until the episode's air date ("in 3d"), or null
 * when the stamp doesn't parse or names a past date (a strip card never
 * counts down to yesterday). Pure — the caller supplies `today`, so the
 * fold is clock-free and the "0 = airs today" case is directly assertable.
 */
internal fun airingCountdownDays(premiereDate: String?, today: LocalDate): Long? {
    val date = premiereDate?.let(::localDateFromIsoTimestamp) ?: return null
    val days = date.toEpochDays() - today.toEpochDays()
    return days.takeIf { it >= 0L }
}
