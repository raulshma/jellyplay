package com.raulshma.jellyplay.core.ui.components

/**
 * Mouse-wheel leftovers reach the sheet connection as SideEffect, which it
 * ignores — partial sheets cannot scroll-expand on desktop. See
 * [sheetExpandsFromContentScroll].
 */
internal actual fun sheetExpandsFromContentScroll(): Boolean = false
