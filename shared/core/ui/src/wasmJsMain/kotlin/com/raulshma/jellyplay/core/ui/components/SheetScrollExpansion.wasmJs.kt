package com.raulshma.jellyplay.core.ui.components

/**
 * Wheel/trackpad leftovers reach the sheet connection as SideEffect, which it
 * ignores — partial sheets cannot scroll-expand on web. See
 * [sheetExpandsFromContentScroll].
 */
internal actual fun sheetExpandsFromContentScroll(): Boolean = false
