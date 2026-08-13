package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.PersonInfo

/**
 * Result of splitting a flat [PersonInfo] list into on-screen cast vs. crew.
 *
 * Jellyfin's `people` payload is a flat mix of every person kind (Actor,
 * Director, Writer, Producer, Composer, GuestStar, …) distinguished only by the
 * free-form [PersonInfo.type] string. The detail screen's single cast row only
 * surfaces a handful; the dedicated Cast & Crew screen partitions the full list
 * so each group gets its own tab. Pure + top-level so the split is unit-testable
 * without a ViewModel (mirrors `SmartPlayResolver` / `MediaInfoFormat`).
 */
internal data class CastCrewPartition(
    val cast: List<PersonInfo>,
    val crew: List<PersonInfo>,
)

/** Person kinds treated as on-screen cast. */
private val CAST_TYPES = setOf("Actor", "GuestStar")

/**
 * Splits [people] into cast (Actor/GuestStar) and crew (everyone else with a
 * non-blank type — Director/Writer/Producer/Composer/…). De-duplicates by id
 * within each group so a person credited twice (e.g. Actor + Director) does not
 * appear twice in the same tab; a person in both groups appears in both tabs,
 * which is the expected crediting. Order is preserved (server order).
 */
internal fun partitionCastAndCrew(people: List<PersonInfo>): CastCrewPartition {
    val cast = people.filter { it.type in CAST_TYPES }.distinctBy { it.id }
    val crew = people
        .filter { it.type !in CAST_TYPES && it.type.isNotBlank() }
        .distinctBy { it.id }
    return CastCrewPartition(cast = cast, crew = crew)
}
