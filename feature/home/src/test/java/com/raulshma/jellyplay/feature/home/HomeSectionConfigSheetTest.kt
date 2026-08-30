package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the PRODUCTION [sectionConfigCapabilities] derivation — the sheet's
 * toggle state, position and move-enablement. The previous suite recomputed
 * `canMoveUp`/`canMoveDown` inline with a DIFFERENT rule
 * (`position < total - 1`, true for absent types) than production's
 * (`index in 0..(order.lastIndex - 1)`, false for absent types) — a
 * production regression passed it untouched.
 */
class HomeSectionConfigSheetTest {

    private val order = listOf(
        HomeSectionType.CONTINUE_WATCHING,
        HomeSectionType.NEXT_UP,
        HomeSectionType.LATEST_MEDIA,
        HomeSectionType.RECENTLY_ADDED,
        HomeSectionType.RECOMMENDATIONS,
    )

    @Test
    fun capabilities_atFirstPosition_cannotMoveUp() {
        val caps = sectionConfigCapabilities(
            type = HomeSectionType.CONTINUE_WATCHING,
            libraryId = null,
            order = order,
            enabledTypes = HomeSectionType.CONFIGURABLE.toSet(),
            libraryOverrides = emptyMap(),
        )

        assertFalse(caps.canMoveUp)
        assertTrue(caps.canMoveDown)
        assertEquals(0, caps.position)
        assertEquals(5, caps.total)
        assertFalse(caps.perLibrary)
    }

    @Test
    fun capabilities_atMiddlePosition_movesBothWays() {
        val caps = sectionConfigCapabilities(
            type = HomeSectionType.LATEST_MEDIA,
            libraryId = null,
            order = order,
            enabledTypes = HomeSectionType.CONFIGURABLE.toSet(),
            libraryOverrides = emptyMap(),
        )

        assertTrue(caps.canMoveUp)
        assertTrue(caps.canMoveDown)
        assertEquals(2, caps.position)
    }

    @Test
    fun capabilities_atLastPosition_cannotMoveDown() {
        val caps = sectionConfigCapabilities(
            type = HomeSectionType.RECOMMENDATIONS,
            libraryId = null,
            order = order,
            enabledTypes = HomeSectionType.CONFIGURABLE.toSet(),
            libraryOverrides = emptyMap(),
        )

        assertTrue(caps.canMoveUp)
        assertFalse(caps.canMoveDown)
        assertEquals(4, caps.position)
    }

    @Test
    fun capabilities_typeAbsentFromOrder_cannotMoveEitherWay() {
        val caps = sectionConfigCapabilities(
            type = HomeSectionType.RECOMMENDATIONS,
            libraryId = null,
            order = listOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP),
            enabledTypes = HomeSectionType.CONFIGURABLE.toSet(),
            libraryOverrides = emptyMap(),
        )

        assertFalse(caps.canMoveUp)
        assertFalse(caps.canMoveDown)
        assertEquals(-1, caps.position)
    }

    @Test
    fun capabilities_globalEnabled_readsEnabledSet() {
        val caps = sectionConfigCapabilities(
            type = HomeSectionType.NEXT_UP,
            libraryId = null,
            order = order,
            enabledTypes = setOf(HomeSectionType.NEXT_UP),
            libraryOverrides = emptyMap(),
        )

        assertTrue(caps.enabled)
    }

    @Test
    fun capabilities_perLibrary_defaultsToEnabledWhenOverrideAbsent() {
        val caps = sectionConfigCapabilities(
            type = HomeSectionType.LATEST_MEDIA,
            libraryId = "movies",
            order = order,
            enabledTypes = emptySet(),
            libraryOverrides = emptyMap(),
        )

        assertTrue(caps.enabled)
        assertTrue(caps.perLibrary)
    }

    @Test
    fun capabilities_perLibrary_disabledWhenInOverrideSet() {
        val caps = sectionConfigCapabilities(
            type = HomeSectionType.LATEST_MEDIA,
            libraryId = "movies",
            order = order,
            enabledTypes = HomeSectionType.CONFIGURABLE.toSet(),
            libraryOverrides = mapOf("movies" to setOf(HomeSectionType.LATEST_MEDIA)),
        )

        assertFalse(caps.enabled)
        assertTrue(caps.perLibrary)
    }

    @Test
    fun homeSectionType_displayNameAndDescription_verifyAllTypes() {
        assertEquals("Continue Watching", HomeSectionType.CONTINUE_WATCHING.displayName)
        assertEquals("Resume watching in-progress media", HomeSectionType.CONTINUE_WATCHING.description)

        assertEquals("Next Up", HomeSectionType.NEXT_UP.displayName)
        assertEquals("Next unwatched episodes of your shows", HomeSectionType.NEXT_UP.description)

        assertEquals("Recently Added", HomeSectionType.RECENTLY_ADDED.displayName)
        assertEquals("Latest Media", HomeSectionType.LATEST_MEDIA.displayName)
        assertEquals("Recommended For You", HomeSectionType.RECOMMENDATIONS.displayName)
    }
}
