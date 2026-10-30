package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.MutableState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the [preferencePathsAreExclusive] truth table — the predicate behind
 * PreferenceScreenScaffold's content-path exclusivity `check`. Pure-JVM at
 * unit speed because the failure it guards crashed every declaration-path
 * settings screen in the v0.11.6 build (the branches were
 * inverted, `content !== NoInlineContent`, so the LEGAL groups-plus-default
 * state threw `IllegalStateException` at first composition — Home,
 * Appearance, Playback, Security, Language, Storage — while the illegal
 * both-paths state the check exists to catch was waved through).
 *
 * The identity leg matters: the "no inline content" fact is carried by the
 * SENTINEL REFERENCE (the parameter default), never by a re-created empty
 * lambda — the third case below is the regression's exact shape.
 */
class PreferenceScreenPathsExclusiveTest {

    private val inlineContent: LazyListScope.(MutableState<PickerState<*>?>) -> Unit = { _ -> }
    private val declaration = listOf(PreferenceScreenGroup(idSet = setOf("probe-row")))

    @Test
    fun declarationPath_withSentinelDefault_isExclusive() {
        // The declaration path as every screen passes it: groups, no content.
        assertTrue(preferencePathsAreExclusive(declaration, NoInlineContent))
    }

    @Test
    fun declarationPath_withRecreatedEmptyLambda_isStillExclusive() {
        // An empty lambda is NOT the sentinel: identity, not shape, decides.
        // (Groups + re-created lambda would be the illegal both-paths state.)
        assertFalse(preferencePathsAreExclusive(declaration, inlineContent))
    }

    @Test
    fun inlineContentPath_isExclusive() {
        assertTrue(preferencePathsAreExclusive(null, inlineContent))
        // The inline path with the default left in place is degenerate but legal.
        assertTrue(preferencePathsAreExclusive(null, NoInlineContent))
    }
}
