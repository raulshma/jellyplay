package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DownloadQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [DownloadPickerState] and its [SubtitleSelection] vocabulary — the
 * user's pre-download choices that travel as a single unit through
 * [DownloadLifecycleState], [DetailUiState], and [DetailContentState].
 *
 * Pins the null-vs-empty contract at the VM→data-layer boundary:
 * [SubtitleSelection.toIndexSet] projects the sealed intent onto the legacy
 * `Set<Int>?` shape [com.raulshma.jellyplay.core.data.download.DownloadIntake]
 * still expects, where `null` means "every deliverable subtitle". The
 * distinction the sealed type exists to make: `All` → `null`, `Subset{5,7}` →
 * `{5,7}`, `Subset{}` → an EMPTY set (a valid "no subtitles" choice — never
 * conflated with "all").
 */
class DownloadPickerStateTest {

    // ── SubtitleSelection.toIndexSet (null-vs-empty contract) ──────────

    @Test
    fun `All projects to null meaning every deliverable subtitle`() {
        assertNull(SubtitleSelection.All.toIndexSet())
    }

    @Test
    fun `Subset projects to exactly its indices`() {
        assertEquals(setOf(5, 7), SubtitleSelection.Subset(setOf(5, 7)).toIndexSet())
        // Set semantics: order of declaration is irrelevant to the projection.
        assertEquals(setOf(5, 7), SubtitleSelection.Subset(setOf(7, 5)).toIndexSet())
    }

    @Test
    fun `empty Subset projects to an empty set not null`() {
        // The whole point of the sealed type: the old `Set<Int>?` sentinel
        // could not distinguish "no subtitles" from "all" without a
        // materialize-then-collapse dance. Empty subset must stay empty.
        val indices = SubtitleSelection.Subset(emptySet()).toIndexSet()
        assertNotNull(indices, "empty Subset must project to emptySet, not null")
        assertTrue(indices.isEmpty())
        assertEquals(emptySet(), indices)
        // And it must differ from the All projection value-for-value.
        assertNull(SubtitleSelection.All.toIndexSet())
    }

    @Test
    fun `projection never mutates the stored subset`() {
        val selection = SubtitleSelection.Subset(setOf(5, 7))
        assertEquals(setOf(5, 7), selection.toIndexSet())
        assertEquals(setOf(5, 7), selection.toIndexSet())
        assertEquals(setOf(5, 7), (selection as SubtitleSelection.Subset).indices)
    }

    @Test
    fun `All and Subset are never equal`() {
        assertFalse(SubtitleSelection.All.equals(SubtitleSelection.Subset(emptySet())))
    }

    // ── DownloadPickerState defaults ───────────────────────────────────

    @Test
    fun `picker starts hidden at original quality with all subtitles`() {
        val state = DownloadPickerState()
        assertFalse(state.visible, "the sheet must not be visible by default")
        assertEquals(DownloadQuality.ORIGINAL, state.quality)
        assertEquals(SubtitleSelection.All, state.subtitleSelection)
    }

    @Test
    fun `default picker projects to null index set`() {
        // The out-of-the-box download bundles every deliverable subtitle.
        assertNull(DownloadPickerState().subtitleSelection.toIndexSet())
    }

    // ── single-unit travel (copy keeps the trio coherent) ──────────────

    @Test
    fun `copying one field leaves the other picker fields intact`() {
        val opened = DownloadPickerState().copy(visible = true)
        assertTrue(opened.visible)
        assertEquals(DownloadQuality.ORIGINAL, opened.quality)
        assertEquals(SubtitleSelection.All, opened.subtitleSelection)

        val customized = opened.copy(
            quality = DownloadQuality.HIGH_1080P,
            subtitleSelection = SubtitleSelection.Subset(setOf(2)),
        )
        assertTrue(customized.visible, "visibility must survive unrelated field updates")
        assertEquals(DownloadQuality.HIGH_1080P, customized.quality)
        assertEquals(setOf(2), customized.subtitleSelection.toIndexSet())
    }
}
