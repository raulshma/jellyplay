package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the shared sub-add side-load plan: the load-time batch gate (label
 * dedupe, same-title uniquify, isDefault force-select, registry pre-seed) and
 * the runtime add gate (id/label dedupe, user-pick select flag).
 */
class MpvSubtitleSideLoadPlanTest {

    private fun source(label: String, id: String = "", isDefault: Boolean = false) = SubtitleSource(
        url = "file:///subs/$label.srt",
        label = label,
        language = "eng",
        mimeType = null,
        isDefault = isDefault,
        id = id,
    )

    // ─── planBatch ────────────────────────────────────────────────────────────

    @Test
    fun batch_preSeedsRegistryWithRawLabels() {
        val plan = MpvSubtitleSideLoadPlan.planBatch(
            sources = listOf(source("A", id = "offline:0"), source("B", id = "")),
            existingLabels = emptySet(),
            registry = emptyMap(),
        )
        assertEquals("offline:0", plan.registry["A"])
        assertTrue(plan.adds.size == 2)
    }

    @Test
    fun batch_skipsTrueLabelDuplicates_butRegistersTheRest() {
        val plan = MpvSubtitleSideLoadPlan.planBatch(
            sources = listOf(source("A", id = "offline:0"), source("A", id = "offline:1")),
            existingLabels = setOf("A"),
            registry = emptyMap(),
        )
        assertTrue(plan.adds.isEmpty())
        // The pre-seed still covers both raw labels (the load() contract).
        assertEquals("offline:1", plan.registry["A"])
    }

    @Test
    fun batch_sameTitledPair_secondIsSkipped() {
        // The batch gate dedupes by raw label as each entry is planned, so the
        // second of a same-titled pair is a true duplicate re-add (the
        // uniquify-instead-of-skip rule is the RUNTIME add gate's — see
        // runtime_idCarryingLabelCollision_uniquifiesAndAdds).
        val plan = MpvSubtitleSideLoadPlan.planBatch(
            sources = listOf(source("Track", id = "offline:0"), source("Track", id = "offline:1")),
            existingLabels = emptySet(),
            registry = emptyMap(),
        )
        assertEquals(listOf("Track"), plan.adds.map { it.label })
        assertEquals("offline:0", plan.registry["Track"])
    }

    @Test
    fun batch_blankLabelCollisions_uniquifyWithinTheBatch() {
        val plan = MpvSubtitleSideLoadPlan.planBatch(
            sources = listOf(source(label = " ", id = "offline:0"), source(label = " ", id = "offline:1")),
            existingLabels = emptySet(),
            registry = emptyMap(),
        )
        // Raw blank labels never collide, but the fallback label uniquifies.
        assertEquals(listOf("External subtitle", "External subtitle (2)"), plan.adds.map { it.label })
    }

    @Test
    fun batch_isDefaultForceSelects_otherwiseAuto() {
        val plan = MpvSubtitleSideLoadPlan.planBatch(
            sources = listOf(source("Def", isDefault = true), source("Plain")),
            existingLabels = emptySet(),
            registry = emptyMap(),
        )
        assertEquals(MpvSubtitleSideLoadPlan.FLAG_SELECT, plan.adds[0].flags)
        assertEquals(MpvSubtitleSideLoadPlan.FLAG_AUTO, plan.adds[1].flags)
    }

    @Test
    fun batch_blankLabel_getsFallbackLabel() {
        val plan = MpvSubtitleSideLoadPlan.planBatch(
            sources = listOf(source(label = "  ", id = "offline:3")),
            existingLabels = emptySet(),
            registry = emptyMap(),
        )
        assertEquals("External subtitle", plan.adds.single().label)
        assertEquals("offline:3", plan.registry["External subtitle"])
    }

    // ─── planRuntimeAdd ───────────────────────────────────────────────────────

    @Test
    fun runtime_idAlreadyRegistered_skips() {
        val result = MpvSubtitleSideLoadPlan.planRuntimeAdd(
            source = source("A", id = "offline:0"),
            existingLabels = emptySet(),
            registry = mapOf("A" to "offline:0"),
        )
        assertTrue(result is MpvSubtitleSideLoadPlan.RuntimeAdd.Skip)
    }

    @Test
    fun runtime_idlessLabelCollision_skips() {
        val result = MpvSubtitleSideLoadPlan.planRuntimeAdd(
            source = source("A"),
            existingLabels = setOf("A"),
            registry = emptyMap(),
        )
        assertTrue(result is MpvSubtitleSideLoadPlan.RuntimeAdd.Skip)
    }

    @Test
    fun runtime_idCarryingLabelCollision_uniquifiesAndAdds() {
        // Same label + different id = a DIFFERENT subtitle — skipping it would
        // strand the second sidecar permanently.
        val result = MpvSubtitleSideLoadPlan.planRuntimeAdd(
            source = source("A", id = "offline:9"),
            existingLabels = setOf("A"),
            registry = mapOf("A" to "offline:0"),
        )
        val add = result as MpvSubtitleSideLoadPlan.RuntimeAdd.Add
        assertEquals("A (2)", add.add.label)
        assertEquals(MpvSubtitleSideLoadPlan.FLAG_SELECT, add.add.flags)
        assertEquals("offline:0", add.registry["A"])
        assertEquals("offline:9", add.registry["A (2)"])
    }

    @Test
    fun runtime_userPick_alwaysSelects() {
        val result = MpvSubtitleSideLoadPlan.planRuntimeAdd(
            source = source("Fresh", id = "provider:1"),
            existingLabels = emptySet(),
            registry = emptyMap(),
        )
        val add = result as MpvSubtitleSideLoadPlan.RuntimeAdd.Add
        assertEquals("Fresh", add.add.label)
        assertEquals(MpvSubtitleSideLoadPlan.FLAG_SELECT, add.add.flags)
    }
}
