package com.raulshma.jellyplay.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SheetTopShape = ShapeCache.smoothTop28
private const val MAX_SCRIM_ALPHA = 0.5f
private const val DISMISS_FRACTION = 0.18f
/**
 * The sheet panel never spans the whole screen: it keeps ~5% clear of each edge
 * (5% left + 5% right via a centered 90% width, and 5% at the top via a 95%
 * max height). The full-screen scrim behind it is unaffected, so taps on the
 * margins still dismiss.
 */
private const val SHEET_WIDTH_FRACTION = 0.90f
private const val SHEET_HEIGHT_FRACTION = 0.95f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val isTv = LocalJellyPlayUi.current.isTv

    if (isTv) {
        TvSafeSheet(
            onDismissRequest = onDismissRequest,
        ) {
            content()
        }
    } else {
        InWindowPlayerSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * A bottom sheet rendered inside the player's own composition/window rather than in a
 * separate [androidx.compose.material3.ModalBottomSheet] Dialog window.
 *
 * A Material3 ModalBottomSheet opens a brand-new top-level window that does NOT inherit the
 * activity's immersive mode, so the status/navigation bars briefly flash on every open. By
 * keeping the sheet in-window, it inherits the player's immersive (edge-to-edge) window and the
 * system bars never appear — exactly the desired behavior (bars only show on an edge swipe,
 * handled by the activity's BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
 */
@Composable
private fun InWindowPlayerSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    // The sheet chrome follows the ambient theme: it is a full UI panel (not an overlay on the
    // video), so light/dynamic-light sheets render with light surfaces in light mode. The
    // container/handle colors below are read from MaterialTheme.colorScheme so they flip correctly.
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val sheetOffset = remember { Animatable(fullHeightPx) }
        var liveDrag by remember { mutableFloatStateOf(0f) }
        val sheetSlideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val sheetSnapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

        LaunchedEffect(Unit) {
            sheetOffset.animateTo(0f, sheetSlideSpec)
        }

        val dismiss: () -> Unit = remember(onDismissRequest) {
            {
                scope.launch {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    sheetOffset.animateTo(fullHeightPx, sheetSlideSpec)
                    onDismissRequest()
                }
            }
        }

        BackHandler(enabled = true, onBack = dismiss)

        val translationY = (sheetOffset.value + liveDrag).coerceAtLeast(0f)
        val progress = (translationY / fullHeightPx).coerceIn(0f, 1f)
        val scrimAlpha = (1f - progress) * MAX_SCRIM_ALPHA

        Box(
            Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = scrimAlpha))
                .pointerInput(Unit) { detectTapGestures(onTap = { dismiss() }) },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(SHEET_WIDTH_FRACTION)
                .heightIn(max = maxHeight * SHEET_HEIGHT_FRACTION)
                .imePadding()
                .offset { IntOffset(0, translationY.roundToInt()) }
                .clip(SheetTopShape)
                .background(colorScheme.surfaceContainer)
                // Consume taps on the sheet panel itself so a touch on non-interactive
                // areas (titles, labels, spacers, the handle on a plain tap) does not
                // fall through to the dismiss scrim behind it. Interactive children
                // (sliders, buttons) still receive and consume their own events first.
                .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(fullHeightPx) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                liveDrag = (liveDrag + dragAmount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                scope.launch {
                                    val current = (sheetOffset.value + liveDrag)
                                        .coerceIn(0f, fullHeightPx)
                                    sheetOffset.snapTo(current)
                                    liveDrag = 0f
                                    if (current > fullHeightPx * DISMISS_FRACTION) {
                                        dismiss()
                                    } else {
                                        sheetOffset.animateTo(0f, sheetSnapSpec)
                                    }
                                }
                            },
                            onDragCancel = {
                                liveDrag = 0f
                            },
                        )
                    }
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
            ) {
                // MaterialTheme overrides the color scheme but does NOT set LocalContentColor, so a
                // Text/Icon without an explicit color would fall back to the default (black) and
                // render dark-on-dark in dark mode. Mirror what a Surface does and pin the content
                // color to the sheet's onSurface so uncolored titles/labels stay legible.
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides colorScheme.onSurface,
                ) {
                    content()
                }
            }
        }
    }
}
