package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.TvFocusDefaults

enum class DeviceClass {
    Phone,
    Tablet,
    Tv,
}

enum class InputMode {
    Touch,
    Remote,
}

@Immutable
data class JellyPlayLayoutTokens(
    val gridCellSize: Dp,
    val gridMinSize: Dp,
    val rowCardWidth: Dp,
    val contentPadding: Dp,
    val itemSpacing: Dp,
    val bottomPadding: Dp,
    val detailBodyMaxWidth: Dp,
)

@Immutable
data class JellyPlayFocusTokens(
    val enabled: Boolean,
    val focusedScale: Float,
    val compactFocusedScale: Float,
    val borderWidth: Dp,
    val glowElevation: Dp,
)

@Immutable
data class JellyPlayUiEnvironment(
    val deviceClass: DeviceClass,
    val inputMode: InputMode,
    val layout: JellyPlayLayoutTokens,
    val focus: JellyPlayFocusTokens,
) {
    val isTv: Boolean get() = deviceClass == DeviceClass.Tv
    val usesRemoteInput: Boolean get() = inputMode == InputMode.Remote
}

val LocalJellyPlayUi = compositionLocalOf {
    JellyPlayUiEnvironment(
        deviceClass = DeviceClass.Phone,
        inputMode = InputMode.Touch,
        layout = AdaptiveInfo(WindowSizeClass.Compact, isLandscape = false).toLayoutTokens(isTv = false),
        focus = defaultFocusTokens(isTv = false),
    )
}

@Composable
fun rememberJellyPlayUiEnvironment(
    adaptiveInfo: AdaptiveInfo,
    isTv: Boolean,
): JellyPlayUiEnvironment = remember(adaptiveInfo, isTv) {
    JellyPlayUiEnvironment(
        deviceClass = when {
            isTv -> DeviceClass.Tv
            adaptiveInfo.windowSizeClass == WindowSizeClass.Compact -> DeviceClass.Phone
            else -> DeviceClass.Tablet
        },
        inputMode = if (isTv) InputMode.Remote else InputMode.Touch,
        layout = adaptiveInfo.toLayoutTokens(isTv),
        focus = defaultFocusTokens(isTv),
    )
}

fun AdaptiveInfo.toLayoutTokens(isTv: Boolean): JellyPlayLayoutTokens =
    JellyPlayLayoutTokens(
        gridCellSize = gridCellSize(isTv),
        gridMinSize = gridMinSize(isTv),
        rowCardWidth = rowCardWidth(isTv),
        contentPadding = contentPadding(isTv),
        itemSpacing = itemSpacing(isTv),
        bottomPadding = bottomPadding(isTv),
        detailBodyMaxWidth = detailBodyMaxWidth(isTv),
    )

object PhoneFocusDefaults {
    val BorderWidth = 1.5.dp
}

private fun defaultFocusTokens(isTv: Boolean): JellyPlayFocusTokens =
    JellyPlayFocusTokens(
        enabled = true,
        focusedScale = if (isTv) 1.08f else 1f,
        compactFocusedScale = if (isTv) 1.04f else 1f,
        borderWidth = if (isTv) TvFocusDefaults.BorderWidth else PhoneFocusDefaults.BorderWidth,
        glowElevation = if (isTv) TvFocusDefaults.GlowElevation else 0.dp,
    )
