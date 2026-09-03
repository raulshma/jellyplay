package com.raulshma.jellyplay.core.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Pins the desktop actual of the sheet scroll-expansion seam: mouse-wheel
 * leftovers reach a Material3 sheet's nested-scroll connection as SideEffect
 * (which it ignores), so a partially-expanded sheet can NEVER grow from
 * content scrolling on desktop — `sheetExpandsFromContentScroll()` must be
 * false so [TvSafeSheet] substitutes `skipPartiallyExpanded = true`.
 */
class SheetExpansionJvmTest {

    @Test
    fun desktop_sheetsCannotExpandFromContentScroll() {
        assertFalse(sheetExpandsFromContentScroll())
    }
}
