package com.raulshma.jellyplay.feature.admin

import com.raulshma.jellyplay.core.model.MediaItemStub
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Unit tests for the pure [filterSelectedForDeletion] helper shared by the
 * `stalemedia` and `watchedremoval` delete-confirmation sheets. Plain JUnit4,
 * no MockK — matches the `HomeUiStateTest` style.
 */
class MediaSelectionTest {

    private fun stub(id: String) = MediaItemStub(itemId = id, name = id)

    @Test
    fun emptyInput_returnsEmpty() {
        val result = emptyList<MediaItemStub>().filterSelectedForDeletion(setOf("a"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun emptySelection_returnsEmpty() {
        val result = listOf(stub("a"), stub("b")).filterSelectedForDeletion(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun onlySelectedItems_retained() {
        val input = listOf(stub("a"), stub("b"), stub("c"))
        val result = input.filterSelectedForDeletion(setOf("a", "c"))
        assertEquals(listOf("a", "c"), result.map { it.itemId })
    }

    @Test
    fun retainsOriginalOrder_ofInput() {
        val input = listOf(stub("c"), stub("a"), stub("b"))
        val result = input.filterSelectedForDeletion(setOf("a", "b", "c"))
        // Selection must not reorder — input order is preserved.
        assertEquals(listOf("c", "a", "b"), result.map { it.itemId })
    }

    @Test
    fun idsNotInList_ignored() {
        val result = listOf(stub("a")).filterSelectedForDeletion(setOf("a", "z", "missing"))
        assertEquals(listOf("a"), result.map { it.itemId })
    }

    @Test
    fun duplicateItemIds_allMatched() {
        val input = listOf(stub("a"), stub("a"))
        val result = input.filterSelectedForDeletion(setOf("a"))
        assertEquals(2, result.size)
    }
}
