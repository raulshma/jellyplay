package com.raulshma.jellyplay.feature.library

import com.raulshma.jellyplay.feature.library.components.FilterSheetKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Library screen's [LibrarySheet] vocabulary and the exhaustive
 * [libraryBackAction] back fold: fixed precedence reset dialog → full filter
 * sheet → open sheet → clear filters (the DECLARED trailing arm), null when
 * back should leave the screen. The former mixed vocabulary (nullable
 * `FilterSheetKind` + two booleans + a hand-derived `isAnySheetOpen`) could
 * not express any of this; the fold is pure so the ladder is pinned without
 * Compose.
 */
class LibrarySheetTest {

    @Test
    fun `back closes the reset confirmation dialog first`() {
        assertEquals(
            LibraryBackAction.DismissResetDialog,
            libraryBackAction(
                resetDialogVisible = true,
                showFilters = true,
                openSheet = LibrarySheet.Filter(FilterSheetKind.SORT),
                inSectionMode = false,
                hasActiveFilters = true,
            ),
        )
    }

    @Test
    fun `back closes the full filter sheet above any open sheet`() {
        assertEquals(
            LibraryBackAction.CloseFilters,
            libraryBackAction(
                resetDialogVisible = false,
                showFilters = true,
                openSheet = LibrarySheet.PosterSize,
                inSectionMode = false,
                hasActiveFilters = true,
            ),
        )
    }

    @Test
    fun `back closes whichever sheet is open`() {
        listOf(
            LibrarySheet.Filter(FilterSheetKind.GENRES),
            LibrarySheet.PosterSize,
            LibrarySheet.GroupBy,
        ).forEach { sheet ->
            assertEquals(
                LibraryBackAction.CloseSheet,
                libraryBackAction(
                    resetDialogVisible = false,
                    showFilters = false,
                    openSheet = sheet,
                    inSectionMode = false,
                    hasActiveFilters = true,
                ),
            )
        }
    }

    @Test
    fun `back clears filters only outside section mode and only with active filters`() {
        assertEquals(
            LibraryBackAction.ClearFilters,
            libraryBackAction(
                resetDialogVisible = false,
                showFilters = false,
                openSheet = null,
                inSectionMode = false,
                hasActiveFilters = true,
            ),
        )
        assertEquals(
            null,
            libraryBackAction(
                resetDialogVisible = false,
                showFilters = false,
                openSheet = null,
                inSectionMode = true,
                hasActiveFilters = true,
            ),
        )
        assertEquals(
            null,
            libraryBackAction(
                resetDialogVisible = false,
                showFilters = false,
                openSheet = null,
                inSectionMode = false,
                hasActiveFilters = false,
            ),
        )
    }
}
