package com.raulshma.jellyplay.feature.subtitle.tester

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Invariants pinned for the tester's two commonMain constant surfaces that no
 * other suite reads directly:
 *  - [SubtitleStyleMode] has exactly the two style channels (SDR, HDR) — the
 *    reducer's mode routing and the preview tiles both iterate exhaustively.
 *  - [SampleSubtitlePresetIds] are unique, non-blank literals, and the
 *    built-in registry (androidMain) shares them as the single source of
 *    truth with [SubtitleTesterUiState]'s default `samplePresetId`.
 */
class SubtitleTesterCommonTypesTest {

    @Test
    fun styleMode_hasExactlySdrAndHdr() {
        assertEquals(listOf(SubtitleStyleMode.SDR, SubtitleStyleMode.HDR), SubtitleStyleMode.entries)
    }

    @Test
    fun styleMode_isExhaustivelyRoutableByTheReducer() {
        // Every mode must route into one of the two working copies — pin the
        // exhaustiveness contract the reducer's `when` relies on.
        SubtitleStyleMode.entries.forEach { mode ->
            val state = SubtitleTesterUiState(mode = mode)
            val routed = state.activeWorkingStyle
            assertTrue(
                routed == state.workingSdrStyle || routed == state.workingHdrStyle,
                "$mode must route to one of the two working copies",
            )
        }
    }

    @Test
    fun samplePresetIds_areUniqueAndNonBlank() {
        val all = listOf(
            SampleSubtitlePresetIds.DIALOGUE,
            SampleSubtitlePresetIds.LYRICS,
            SampleSubtitlePresetIds.SIGNS,
            SampleSubtitlePresetIds.ACTION,
        )

        assertEquals(4, all.size)
        assertEquals(all.size, all.toSet().size, "preset ids must be unique — the registry keys on them")
        all.forEach { id ->
            assertTrue(id.isNotBlank(), "blank preset ids would silently break preset lookups")
        }
    }

    @Test
    fun uiStateDefaultPreset_isTheSharedDialogueConstant() {
        assertEquals(SampleSubtitlePresetIds.DIALOGUE, SubtitleTesterUiState().samplePresetId)
    }
}
