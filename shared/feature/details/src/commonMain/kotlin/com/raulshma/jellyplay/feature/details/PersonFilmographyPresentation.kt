package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.PersonRef

/**
 * The credit-group partition behind the person screen's second chip row
 * (D18). Jellyfin distinguishes the role kind only by the free-form
 * [PersonInfo.type] string (Actor, GuestStar, Director, Writer, Producer,
 * Composer, …) — the same vocabulary [partitionCastAndCrew] splits. The
 * free-text [PersonInfo.role] stays the "as {Role}" card label; this enum is
 * only the bucketing.
 */
internal enum class FilmographyRoleGroup { ACTING, DIRECTING, WRITING, OTHER }

/** Person kinds bucketed as on-screen acting (the cast side of the cast/crew split). */
private val ACTING_TYPES = setOf("Actor", "GuestStar")

/**
 * Buckets a credited person type into the filmography chip groups. Anything
 * outside acting/directing/writing (Producer, Composer, Creator, …, and the
 * blank/unknown types) lands in OTHER — never lost, just un-labeled.
 * Pure + top-level so the partition is unit-testable without a ViewModel
 * (mirrors [partitionCastAndCrew] / `MediaInfoFormat`).
 */
internal fun filmographyRoleGroup(personType: String): FilmographyRoleGroup = when {
    personType in ACTING_TYPES -> FilmographyRoleGroup.ACTING
    personType == "Director" -> FilmographyRoleGroup.DIRECTING
    personType == "Writer" -> FilmographyRoleGroup.WRITING
    else -> FilmographyRoleGroup.OTHER
}

/**
 * The queried person's own credit in a filmography item's People — the FIRST
 * matching entry (a person credited twice, e.g. Actor + Director, reads as
 * its first credit; the plan's multi-role rule). Null when the item carries
 * no entry for this person: the fetch didn't project People, the server
 * omitted the credit, or the id simply doesn't match.
 */
internal fun filmographyCreditFor(item: MediaItem, personId: String): PersonInfo? =
    item.people.firstOrNull { it.id == personId }

/**
 * The filmography card's "as {Role}" subtitle text (D18): the credit's
 * free-text role, trimmed. Null when there is no matching credit or the role
 * text is blank (crew credits usually carry only the typed kind) — those
 * cards render unchanged.
 */
internal fun filmographyRoleLabel(item: MediaItem, personId: String): String? =
    creditRoleLabel(filmographyCreditFor(item, personId))

/** [filmographyRoleLabel] over an already-resolved credit (the screen reads the precomputed credits map). */
internal fun creditRoleLabel(credit: PersonInfo?): String? =
    credit?.role?.trim()?.takeIf { it.isNotEmpty() }

/**
 * itemId → the person's own credited entry, precomputed once per load so the
 * screen's cards read the label/group from a stable map and the group chip
 * row's existence gate is a plain isEmpty. Items without a matching credit
 * are absent (never a placeholder entry).
 */
internal fun filmographyCredits(items: List<MediaItem>, personId: String): Map<String, PersonInfo> =
    buildMap {
        for (item in items) {
            filmographyCreditFor(item, personId)?.let { put(item.id, it) }
        }
    }

/** Default co-star cap for the D20 "frequently appears with" row. */
internal const val COSTAR_LIMIT = 10

/**
 * The D20 co-star selection, pure: drop the person themselves (the appearsIn
 * query returns everyone credited on the anchor item, including them),
 * preserve the server's order, cap at [limit].
 */
internal fun selectCostars(
    people: List<PersonRef>,
    personId: String,
    limit: Int = COSTAR_LIMIT,
): List<PersonRef> = people.filter { it.id != personId }.take(limit)
