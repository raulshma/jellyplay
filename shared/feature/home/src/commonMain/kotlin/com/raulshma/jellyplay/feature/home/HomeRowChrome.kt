package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.HomeCardScale
import com.raulshma.jellyplay.core.model.HomeCwProgressStyle

/**
 * The home rows' visual chrome — the display-QoL prefs (card scale, corner
 * radius, See-All visibility, continue-watching progress style) provided once
 * around the home content so every row arm (online, offline-mirrored,
 * downloaded) reads the same values without threading them through
 * [HomeContentState]. Defaults reproduce the shipped look exactly.
 */
internal data class HomeRowChrome(
    /** Hides the per-row "See All" pills. */
    val hideSeeAll: Boolean = false,
    /** The poster-card width multiplier (SMALL 0.85 / DEFAULT 1.0 / LARGE 1.15). */
    val cardScale: Float = 1f,
    /** The cards' corner radius (dp). */
    val cornerRadius: Dp = 12.dp,
    /** How wide (resume) cards render watch progress. */
    val cwProgressStyle: HomeCwProgressStyle = HomeCwProgressStyle.DEFAULT,
    /** The section rows' one-shot entrance fade/translate. Default on. */
    val rowsAnimations: Boolean = true,
)

/** Provided at [HomeScreen] scope; defaults render exactly the shipped chrome. */
internal val LocalHomeRowChrome = staticCompositionLocalOf { HomeRowChrome() }

/** The scale pref's multiplier fold (the clamped setter guarantees membership). */
internal fun HomeCardScale.toScaleFactor(): Float = when (this) {
    HomeCardScale.SMALL -> 0.85f
    HomeCardScale.DEFAULT -> 1f
    HomeCardScale.LARGE -> 1.15f
}

/**
 * The card-radius pref's shape fold — the dp value mapped onto the
 * [ShapeCache] smooth shapes (the repo's corner vocabulary); 12 renders null
 * so the card scaffold's shipped smooth-12 stays (identical output, no
 * allocation). Pure over its input.
 */
internal fun homeCardShapeFor(radiusDp: Int): Shape? = when (radiusDp) {
    12 -> null
    0 -> RectangleShape
    4 -> ShapeCache.smooth4
    8 -> ShapeCache.smooth8
    10 -> ShapeCache.smooth10
    14 -> ShapeCache.smooth14
    16 -> ShapeCache.smooth16
    20 -> ShapeCache.smooth20
    24 -> ShapeCache.smooth24
    else -> null
}
