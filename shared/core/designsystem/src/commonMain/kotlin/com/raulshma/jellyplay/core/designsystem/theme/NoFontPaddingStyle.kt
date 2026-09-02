package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.text.PlatformTextStyle

/**
 * Android-only font-padding opt-out; other targets have nothing to disable, so
 * they pass null (the TextStyle default).
 */
internal expect val noFontPaddingStyle: PlatformTextStyle?
