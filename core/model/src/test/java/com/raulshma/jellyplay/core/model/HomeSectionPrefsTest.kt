package com.raulshma.jellyplay.core.model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [HomeSectionPrefs]' three write policies directly — the behaviour the
 * inline section-config sheet's toggle/move actions route through. Previously
 * these were reachable only through the full VM harness (33 collaborators +
 * Robolectric); the VM-level tests now pin only the event → store-command routing.
 */
class HomeSectionPrefsTest {

    private val order = listOf(
        HomeSectionType.CONTINUE_WATCHING,
        HomeSectionType.NEXT_UP,
        HomeSectionType.LATEST_MEDIA,
    )

    // ── withSectionVisible ──────────────────────────────────────────────────

    @Test
    fun withSectionVisible_true_addsToEnabledSet() {
        val prefs = HomeSectionPrefs(query = HomeSectionQuery(enabledSections = emptySet()))

        val updated = prefs.withSectionVisible(HomeSectionType.NEXT_UP, visible = true)

        assertEquals(setOf(HomeSectionType.NEXT_UP), updated.query.enabledSections)
    }

    @Test
    fun withSectionVisible_false_removesFromEnabledSet() {
        val prefs = HomeSectionPrefs(
            query = HomeSectionQuery(enabledSections = HomeSectionType.CONFIGURABLE.toSet()),
        )

        val updated = prefs.withSectionVisible(HomeSectionType.NEXT_UP, visible = false)

        assertEquals(HomeSectionType.CONFIGURABLE.toSet() - HomeSectionType.NEXT_UP, updated.query.enabledSections)
    }

    @Test
    fun withSectionVisible_idempotent_whenAlreadyInTargetState() {
        val enabled = setOf(HomeSectionType.NEXT_UP)
        val prefs = HomeSectionPrefs(query = HomeSectionQuery(enabledSections = enabled))

        assertEquals(prefs, prefs.withSectionVisible(HomeSectionType.NEXT_UP, visible = true))
    }

    @Test
    fun withSectionVisible_leavesOtherQueryInputsUntouched() {
        val prefs = HomeSectionPrefs(
            query = HomeSectionQuery(
                enabledSections = setOf(HomeSectionType.NEXT_UP),
                nextUpMaxDays = 14,
                hiddenCwItemIds = setOf("cw-1"),
            ),
        )

        val updated = prefs.withSectionVisible(HomeSectionType.RECENTLY_ADDED, visible = true)

        assertEquals(14, updated.query.nextUpMaxDays)
        assertEquals(setOf("cw-1"), updated.query.hiddenCwItemIds)
    }

    // ── withSectionMoved ────────────────────────────────────────────────────

    @Test
    fun withSectionMoved_up_swapsWithPredecessor() {
        val prefs = HomeSectionPrefs(homeSectionOrder = order)

        val updated = prefs.withSectionMoved(HomeSectionType.NEXT_UP, up = true)

        assertEquals(
            listOf(HomeSectionType.NEXT_UP, HomeSectionType.CONTINUE_WATCHING, HomeSectionType.LATEST_MEDIA),
            updated!!.homeSectionOrder,
        )
    }

    @Test
    fun withSectionMoved_down_swapsWithSuccessor() {
        val prefs = HomeSectionPrefs(homeSectionOrder = order)

        val updated = prefs.withSectionMoved(HomeSectionType.CONTINUE_WATCHING, up = false)

        assertEquals(
            listOf(HomeSectionType.NEXT_UP, HomeSectionType.CONTINUE_WATCHING, HomeSectionType.LATEST_MEDIA),
            updated!!.homeSectionOrder,
        )
    }

    @Test
    fun withSectionMoved_atEitherEdge_returnsNull() {
        val prefs = HomeSectionPrefs(homeSectionOrder = order)

        assertNull(prefs.withSectionMoved(HomeSectionType.CONTINUE_WATCHING, up = true))
        assertNull(prefs.withSectionMoved(HomeSectionType.LATEST_MEDIA, up = false))
    }

    @Test
    fun withSectionMoved_absentType_returnsNull() {
        val prefs = HomeSectionPrefs(
            homeSectionOrder = listOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP),
        )

        assertNull(prefs.withSectionMoved(HomeSectionType.RECOMMENDATIONS, up = true))
    }

    // ── withLibrarySectionVisible ───────────────────────────────────────────

    @Test
    fun withLibrarySectionVisible_disable_addsToThatLibrarysOverrideSet() {
        val prefs = HomeSectionPrefs(
            query = HomeSectionQuery(
                libraryHomeSectionOverrides = mapOf("movies" to setOf(HomeSectionType.RECENTLY_ADDED)),
            ),
        )

        val updated = prefs.withLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, visible = false)

        assertEquals(
            mapOf("movies" to setOf(HomeSectionType.RECENTLY_ADDED, HomeSectionType.LATEST_MEDIA)),
            updated.query.libraryHomeSectionOverrides,
        )
    }

    @Test
    fun withLibrarySectionVisible_reenable_dropsEmptyKey() {
        val prefs = HomeSectionPrefs(
            query = HomeSectionQuery(
                libraryHomeSectionOverrides = mapOf("movies" to setOf(HomeSectionType.LATEST_MEDIA)),
            ),
        )

        val updated = prefs.withLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, visible = true)

        assertEquals(emptyMap<String, Set<HomeSectionType>>(), updated.query.libraryHomeSectionOverrides)
    }

    @Test
    fun withLibrarySectionVisible_leavesOtherLibrariesUntouched() {
        val prefs = HomeSectionPrefs(
            query = HomeSectionQuery(
                libraryHomeSectionOverrides = mapOf("tv" to setOf(HomeSectionType.LATEST_MEDIA)),
            ),
        )

        val updated = prefs.withLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, visible = false)

        assertEquals(
            mapOf(
                "tv" to setOf(HomeSectionType.LATEST_MEDIA),
                "movies" to setOf(HomeSectionType.LATEST_MEDIA),
            ),
            updated.query.libraryHomeSectionOverrides,
        )
    }

    @Test
    fun withLibrarySectionVisible_absentLibrary_startsANewOverrideSet() {
        val prefs = HomeSectionPrefs()

        val updated = prefs.withLibrarySectionVisible("music", HomeSectionType.LATEST_MEDIA, visible = false)

        assertEquals(mapOf("music" to setOf(HomeSectionType.LATEST_MEDIA)), updated.query.libraryHomeSectionOverrides)
    }
}
