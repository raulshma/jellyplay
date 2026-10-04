package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.model.TvOverscan

/**
 * TV-mode composition locals. `LocalTvMode` is provided by the TV app shell
 * (feature-detect stays platform-side: the legacy `Context.isTv()`/`isTvDevice()`
 * halves live in the Android shim until cutover).
 */
val LocalTvMode = compositionLocalOf { false }

val LocalTvTypography = compositionLocalOf<Typography?> { null }

/**
 * Expands the TV navigation drawer from within screen content. Provided by the
 * TV app scaffold ([com.raulshma.jellyplay.navigation.TvNavigationDrawer]) around its
 * content slot; a no-op default everywhere else (including phone).
 *
 * Screens attach this to leftward focus exits at their left edge so D-pad Left
 * reliably expands the drawer even when the geometric focus search comes back
 * without a target (e.g. the selected rail entry is recycled out of the lazy
 * drawer column and can't take focus).
 */
val LocalTvDrawerOpener = compositionLocalOf<() -> Unit> { {} }

/**
 * The TV shell scaffold: a full-bleed background Box hosting every piece of
 * TV chrome (drawer, rails, screens) inside the overscan safe area.
 *
 * [overscan] is the user's "Screen fit" calibration (default the guideline
 * 5%): the content layer is padded by [TvOverscan.overscanSafeAreaPadding]
 * measured against THIS box, so the background stays full-bleed while every
 * child — the drawer included — stays inside the safe area and nothing clips
 * at panels that cut into the picture. Plain layout padding: focus/DPad
 * propagation is unaffected (the full-screen player never composes through
 * this scaffold — the shell routes full-screen destinations to a bare Box —
 * so playback surfaces stay edge-to-edge by construction).
 */
@Composable
fun TvScaffold(
    modifier: Modifier = Modifier,
    overscan: TvOverscan = TvOverscan.OFF,
    topBar: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(modifier = Modifier.padding(overscan.overscanSafeAreaPadding(maxWidth, maxHeight))) {
            if (topBar != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    topBar()
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                }
            } else {
                content()
            }
        }
    }
}
