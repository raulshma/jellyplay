package com.raulshma.jellyplay.whatsnew

import com.raulshma.jellyplay.core.model.WhatsNewCategory
import com.raulshma.jellyplay.core.model.WhatsNewEntry
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the pure show-once policy: fresh installs stay silent, pre-feature
 * installs and real upgrades present, already-seen (or newer-seen) versions
 * never re-present, and the content lookup is an exact-version match.
 */
class WhatsNewDecisionTest {

    private fun release(version: String) = WhatsNewRelease(
        version = version,
        entries = listOf(
            WhatsNewEntry(id = "e", category = WhatsNewCategory.NEW, title = "T", summary = "S"),
        ),
    )

    @Test
    fun `fresh install without onboarding stamps silently`() {
        assertEquals(
            WhatsNewDecision.Phase.STAMP_SILENTLY,
            WhatsNewDecision.launchPhase("0.11.2", seenVersion = null, onboardingCompleted = false),
        )
    }

    @Test
    fun `never-seen but onboarded install presents - the pre-feature user`() {
        assertEquals(
            WhatsNewDecision.Phase.SHOW_IF_CONTENT,
            WhatsNewDecision.launchPhase("0.11.2", seenVersion = null, onboardingCompleted = true),
        )
    }

    @Test
    fun `upgrade since last seen version presents`() {
        assertEquals(
            WhatsNewDecision.Phase.SHOW_IF_CONTENT,
            WhatsNewDecision.launchPhase("0.11.2", seenVersion = "0.11.0", onboardingCompleted = true),
        )
        // Onboarding state is irrelevant once a stamp exists.
        assertEquals(
            WhatsNewDecision.Phase.SHOW_IF_CONTENT,
            WhatsNewDecision.launchPhase("0.11.2", seenVersion = "0.11.0", onboardingCompleted = false),
        )
    }

    @Test
    fun `same version never re-presents`() {
        assertEquals(
            WhatsNewDecision.Phase.NONE,
            WhatsNewDecision.launchPhase("0.11.2", seenVersion = "0.11.2", onboardingCompleted = true),
        )
    }

    @Test
    fun `seen version newer than installed never presents - downgrade safety`() {
        assertEquals(
            WhatsNewDecision.Phase.NONE,
            WhatsNewDecision.launchPhase("0.11.0", seenVersion = "0.11.2", onboardingCompleted = true),
        )
    }

    @Test
    fun `releaseToShow is an exact-version match`() {
        val releases = listOf(release("0.11.2"), release("0.11.0"))

        assertEquals("0.11.2", WhatsNewDecision.releaseToShow("0.11.2", releases)?.version)
        assertNull(WhatsNewDecision.releaseToShow("0.11.1", releases))
        assertNull(
            "tag form does not match — caller normalizes",
            WhatsNewDecision.releaseToShow("v0.11.2", releases),
        )
    }
}
