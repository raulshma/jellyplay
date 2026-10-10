package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.TvOverscan

/**
 * The TV overscan safe-area policy as one pure fold: each edge
 * insets by the calibration's [TvOverscan.percent] of ITS screen dimension —
 * the horizontal pair by that fraction of [widthDp], the vertical pair by the
 * same fraction of [heightDp] (the Android TV design guidance of padding the
 * picture inside the 5% safe area so panel edges can't clip content —
 * developer.android.com/design/tv).
 *
 * Pure so the shell [TvScaffold] consumes it declaratively and the math is
 * JVM-pinned (TvOverscanTest in this package's jvmTest set): `OFF`
 * collapses to zero padding regardless of the supplied size.
 */
fun TvOverscan.overscanSafeAreaPadding(widthDp: Dp, heightDp: Dp): PaddingValues {
    if (percent <= 0) return PaddingValues(0.dp)
    val horizontal = widthDp * (percent / 100f)
    val vertical = heightDp * (percent / 100f)
    return PaddingValues(horizontal = horizontal, vertical = vertical)
}
