package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.PersonRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The D18 role extraction / group partition + D20 co-star selection, pure
 * (PersonFilmographyPresentation) — no ViewModel involved.
 */
class PersonFilmographyPresentationTest {

    private fun credit(
        id: String = "p1",
        role: String? = null,
        type: String = "Actor",
    ) = PersonInfo(id = id, name = "Person", role = role, type = type)

    private fun item(id: String, vararg people: PersonInfo) =
        MediaItem(id = id, name = "Title $id", mediaType = MediaType.MOVIE, people = people.toList())

    // ── D18: role-group partition ─────────────────────────────────────────

    @Test
    fun `role group partition buckets the typed kinds`() {
        assertEquals(FilmographyRoleGroup.ACTING, filmographyRoleGroup("Actor"))
        assertEquals(FilmographyRoleGroup.ACTING, filmographyRoleGroup("GuestStar"))
        assertEquals(FilmographyRoleGroup.DIRECTING, filmographyRoleGroup("Director"))
        assertEquals(FilmographyRoleGroup.WRITING, filmographyRoleGroup("Writer"))
    }

    @Test
    fun `role group partition lands every other kind in OTHER`() {
        assertEquals(FilmographyRoleGroup.OTHER, filmographyRoleGroup("Producer"))
        assertEquals(FilmographyRoleGroup.OTHER, filmographyRoleGroup("Composer"))
        assertEquals(FilmographyRoleGroup.OTHER, filmographyRoleGroup("Creator"))
        // blank / unrecognized types degrade to OTHER, never lost.
        assertEquals(FilmographyRoleGroup.OTHER, filmographyRoleGroup(""))
        assertEquals(FilmographyRoleGroup.OTHER, filmographyRoleGroup("MysteryKind"))
    }

    // ── D18: credit extraction + "as {Role}" label ────────────────────────

    @Test
    fun `credit extraction matches the person id on the item's People`() {
        val person = credit(id = "p1", role = "John Smith")
        val entry = item("m1", credit(id = "other"), person)

        assertSame(person, filmographyCreditFor(entry, "p1"))
        assertEquals("John Smith", filmographyRoleLabel(entry, "p1"))
    }

    @Test
    fun `multi-role people resolve to the first matching entry`() {
        val acting = credit(id = "p1", role = "John Smith", type = "Actor")
        val directing = credit(id = "p1", role = null, type = "Director")
        val entry = item("m1", acting, directing)

        assertSame(acting, filmographyCreditFor(entry, "p1"))
        assertEquals(FilmographyRoleGroup.ACTING, filmographyRoleGroup(filmographyCreditFor(entry, "p1")!!.type))
    }

    @Test
    fun `missing entry falls back to null role`() {
        val entry = item("m1", credit(id = "someone-else", role = "Not Them"))

        assertNull(filmographyCreditFor(entry, "p1"))
        assertNull(filmographyRoleLabel(entry, "p1"))
    }

    @Test
    fun `no projected People at all means no label`() {
        val entry = MediaItem(id = "m1", name = "Bare", mediaType = MediaType.MOVIE)

        assertNull(filmographyRoleLabel(entry, "p1"))
    }

    @Test
    fun `blank or whitespace-only role text yields no label`() {
        assertNull(creditRoleLabel(credit(role = null)))
        assertNull(creditRoleLabel(credit(role = "")))
        assertNull(creditRoleLabel(credit(role = "   ")))
        assertEquals("Narrator", creditRoleLabel(credit(role = " Narrator ")))
    }

    @Test
    fun `credits map keys by item id and omits uncredited items`() {
        val credited = item("m1", credit(id = "p1", role = "Lead"))
        val uncredited = item("m2", credit(id = "someone-else"))
        val bare = MediaItem(id = "m3", name = "Bare", mediaType = MediaType.SERIES)

        val credits = filmographyCredits(listOf(credited, uncredited, bare), "p1")

        assertEquals(setOf("m1"), credits.keys)
        assertEquals("Lead", creditRoleLabel(credits["m1"]))
    }

    // ── D20: co-star selection ────────────────────────────────────────────

    @Test
    fun `co-star selection excludes the person themselves and preserves order`() {
        val people = listOf(
            PersonRef(id = "p1", name = "The Person"),
            PersonRef(id = "c1", name = "Co Star One"),
            PersonRef(id = "c2", name = "Co Star Two"),
        )

        val costars = selectCostars(people, personId = "p1")

        assertEquals(listOf(PersonRef(id = "c1", name = "Co Star One"), PersonRef(id = "c2", name = "Co Star Two")), costars)
    }

    @Test
    fun `co-star selection caps at the limit after exclusion`() {
        val people = (1..15).map { PersonRef(id = "c$it", name = "Co $it") }

        val costars = selectCostars(people, personId = "p1")

        assertEquals(COSTAR_LIMIT, costars.size)
        assertEquals("c1", costars.first().id)
    }

    @Test
    fun `co-star selection default cap is ten`() {
        assertEquals(10, COSTAR_LIMIT)
    }

    @Test
    fun `co-star selection passes a list without the person through`() {
        val people = listOf(PersonRef(id = "c1", name = "Only Co"))

        assertEquals(people, selectCostars(people, personId = "p1"))
        assertTrue(selectCostars(emptyList(), personId = "p1").isEmpty())
        assertFalse(selectCostars(people, personId = "p1", limit = 0).isNotEmpty())
    }
}
