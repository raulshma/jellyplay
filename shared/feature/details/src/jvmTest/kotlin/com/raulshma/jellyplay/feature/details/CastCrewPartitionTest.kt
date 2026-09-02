package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.PersonInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [partitionCastAndCrew] — the pure + top-level split of Jellyfin's flat
 * `people` payload into the Cast & Crew screen's two tabs. Pins the partition
 * invariants without a ViewModel (mirrors [MediaInfoFormatTest] /
 * [SmartPlayResolverTest]):
 *
 *  - exactly "Actor"/"GuestStar" (case-sensitive) land in cast;
 *  - every OTHER non-blank type (Director/Writer/Producer/Composer/…) lands in
 *    crew — the crew side is a catch-all, not a closed list;
 *  - blank types are dropped from both groups;
 *  - server order is preserved within each group;
 *  - de-duplication by id happens per group, so a double credit (Actor +
 *    Director) shows once per tab, never twice in one tab.
 */
class CastCrewPartitionTest {

    private fun person(
        id: String,
        name: String,
        type: String,
        role: String? = null,
    ) = PersonInfo(id = id, name = name, type = type, role = role)

    // ── type routing ───────────────────────────────────────────────────

    @Test
    fun `Actor and GuestStar land in cast`() {
        val people = listOf(
            person("a1", "Jane Doe", "Actor", role = "Hero"),
            person("g1", "Surprise Face", "GuestStar", role = "Cameo"),
        )
        val partition = partitionCastAndCrew(people)
        assertEquals(listOf("a1", "g1"), partition.cast.map { it.id })
        assertTrue(partition.crew.isEmpty())
    }

    @Test
    fun `Director Writer Producer Composer land in crew`() {
        val people = listOf(
            person("d1", "Eye Auteur", "Director"),
            person("w1", "Pen Smith", "Writer"),
            person("p1", "Money Bags", "Producer"),
            person("c1", "Note Weaver", "Composer"),
        )
        val partition = partitionCastAndCrew(people)
        assertEquals(listOf("d1", "w1", "p1", "c1"), partition.crew.map { it.id })
        assertTrue(partition.cast.isEmpty())
    }

    @Test
    fun `unknown non-blank type falls to crew`() {
        // Crew is a catch-all ("everyone else with a non-blank type"), so a
        // future server person kind still renders in the crew tab.
        val partition = partitionCastAndCrew(listOf(person("x1", "Odd Job", "FightChoreographer")))
        assertEquals(listOf("x1"), partition.crew.map { it.id })
        assertTrue(partition.cast.isEmpty())
    }

    @Test
    fun `type matching is case-sensitive exact`() {
        // CAST_TYPES holds the literal server tokens; any other casing is not
        // cast and therefore routes to the crew catch-all (never dropped).
        val partition = partitionCastAndCrew(
            listOf(
                person("lo", "Lower", "actor"),
                person("up", "Upper", "ACTOR"),
                person("gs", "Guest", "gueststar"),
                person("pad", "Padded", " Actor"),
            ),
        )
        assertTrue(partition.cast.isEmpty())
        assertEquals(listOf("lo", "up", "gs", "pad"), partition.crew.map { it.id })
    }

    // ── blank-type filtering ───────────────────────────────────────────

    @Test
    fun `blank type is dropped from both groups`() {
        val partition = partitionCastAndCrew(
            listOf(
                person("b1", "No Type", ""),
                person("b2", "Blank Type", "   "),
            ),
        )
        assertTrue(partition.cast.isEmpty())
        assertTrue(partition.crew.isEmpty())
    }

    @Test
    fun `mixed payload keeps only typed persons`() {
        val partition = partitionCastAndCrew(
            listOf(
                person("a1", "Jane Doe", "Actor"),
                person("b1", "No Type", ""),
                person("d1", "Eye Auteur", "Director"),
                person("b2", "Blank Type", "  "),
            ),
        )
        assertEquals(listOf("a1"), partition.cast.map { it.id })
        assertEquals(listOf("d1"), partition.crew.map { it.id })
    }

    // ── ordering ───────────────────────────────────────────────────────

    @Test
    fun `server order is preserved within each group`() {
        // Interleaved payload: each tab must replay the server's relative
        // order, not the partition's iteration order.
        val people = listOf(
            person("a3", "Third Cast", "Actor"),
            person("d1", "First Crew", "Director"),
            person("a1", "First Cast", "Actor"),
            person("w1", "Second Crew", "Writer"),
            person("a2", "Second Cast", "Actor"),
            person("p1", "Third Crew", "Producer"),
        )
        val partition = partitionCastAndCrew(people)
        assertEquals(listOf("a3", "a1", "a2"), partition.cast.map { it.id })
        assertEquals(listOf("d1", "w1", "p1"), partition.crew.map { it.id })
    }

    // ── de-duplication ─────────────────────────────────────────────────

    @Test
    fun `same id credited twice in one group appears once`() {
        val people = listOf(
            person("a1", "Jane Doe", "Actor", role = "Hero"),
            person("a1", "Jane Doe", "Actor", role = "Hero (archive)"),
        )
        val partition = partitionCastAndCrew(people)
        assertEquals(listOf("a1"), partition.cast.map { it.id })
        // First occurrence wins (distinctBy keeps the earliest credit).
        assertEquals("Hero", partition.cast.single().role)
    }

    @Test
    fun `person credited as Actor and Director appears in both tabs exactly once`() {
        val people = listOf(
            person("hy1", "Double Threat", "Actor"),
            person("hy1", "Double Threat", "Director"),
        )
        val partition = partitionCastAndCrew(people)
        assertEquals(listOf("hy1"), partition.cast.map { it.id })
        assertEquals(listOf("hy1"), partition.crew.map { it.id })
    }

    @Test
    fun `same id in different groups does not cross-deduplicate`() {
        // The blank-typed duplicate below must not remove the crew credit of
        // an id that also exists in cast (groups dedupe independently).
        val people = listOf(
            person("d1", "Eye Auteur", "Director"),
            person("d1", "Eye Auteur", "Director"),
            person("d1", "Eye Auteur", ""),
        )
        val partition = partitionCastAndCrew(people)
        assertTrue(partition.cast.isEmpty())
        assertEquals(listOf("d1"), partition.crew.map { it.id })
    }

    // ── degenerate input ───────────────────────────────────────────────

    @Test
    fun `empty input yields empty cast and crew`() {
        val partition = partitionCastAndCrew(emptyList())
        assertTrue(partition.cast.isEmpty())
        assertTrue(partition.crew.isEmpty())
        assertEquals(CastCrewPartition(cast = emptyList(), crew = emptyList()), partition)
    }

    // ── result shape ───────────────────────────────────────────────────

    @Test
    fun `partition carries the full PersonInfo entries not just ids`() {
        val jane = person("a1", "Jane Doe", "Actor", role = "Hero")
        val eye = person("d1", "Eye Auteur", "Director")
        val partition = partitionCastAndCrew(listOf(jane, eye))
        assertEquals(listOf(jane), partition.cast)
        assertEquals(listOf(eye), partition.crew)
    }
}
