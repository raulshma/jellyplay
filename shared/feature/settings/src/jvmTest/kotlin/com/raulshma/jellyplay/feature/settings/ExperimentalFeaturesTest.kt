package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.ExperimentalFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the experimental-feature presentation registry: every
 * [ExperimentalFeature] enum constant (the persisted identifier) has exactly
 * one [ExperimentalFeatureInfo] row — a new enum value without a registry
 * entry would silently render no toggle on the Experimental screen — and
 * [ExperimentalFeatures.infoFor] resolves search deep-link ids by enum name,
 * returning null for unknown ids so the screen degrades instead of crashing.
 */
class ExperimentalFeaturesTest {

    @Test
    fun `every experimental feature is registered exactly once`() {
        assertEquals(
            ExperimentalFeature.entries.toList(),
            ExperimentalFeatures.all.map { it.feature },
            "registry must cover every enum value exactly once, in declaration order",
        )
    }

    @Test
    fun `infoFor resolves by the persisted enum name`() {
        ExperimentalFeature.entries.forEach { feature ->
            assertSame(
                ExperimentalFeatures.all.first { it.feature == feature },
                ExperimentalFeatures.infoFor(feature.name),
                "deep-link id '${feature.name}' must resolve to its registry row",
            )
        }
    }

    @Test
    fun `infoFor returns null for unknown ids instead of crashing`() {
        assertNull(ExperimentalFeatures.infoFor("NOT_A_FEATURE"))
        assertNull(ExperimentalFeatures.infoFor(""))
    }

    @Test
    fun `every registry row carries its own title and subtitle resource`() {
        ExperimentalFeatures.all.forEach { info ->
            assertTrue(info.titleRes != info.subtitleRes, "row ${info.feature} reuses one resource for title and subtitle")
        }
        // Icons are non-null by type; titles/subtitles differ per row.
        assertEquals(
            ExperimentalFeatures.all.map { it.titleRes }.toSet().size,
            ExperimentalFeatures.all.size,
            "two rows must not share a title resource",
        )
    }
}
