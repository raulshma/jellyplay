package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

internal val _isSynthwaveActive = kotlinx.coroutines.flow.MutableStateFlow(false)

class SynthwaveDynamicShape(val defaultShape: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return if (_isSynthwaveActive.value) {
            RoundedCornerShape(0.dp).createOutline(size, layoutDirection, density)
        } else {
            defaultShape.createOutline(size, layoutDirection, density)
        }
    }
}

/**
 * Cached instances of frequently-used [AbsoluteSmoothCornerShape].
 *
 * [AbsoluteSmoothCornerShape] is significantly more expensive than [RoundedCornerShape][androidx.compose.foundation.shape.RoundedCornerShape]
 * because it computes cubic Bézier curves analytically. In LazyColumn items (cards, list items, etc.)
 * each item pays this cost on first composition. By reusing singleton instances we avoid
 * repeated Path construction for the most common radii.
 */
object ShapeCache {
    /** 4dp smooth corners — tiny badges, micro surfaces */
    val smooth4 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 4.dp, smoothnessAsPercent = 60))

    /** 8dp smooth corners — compact chips, small surfaces */
    val smooth8 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60))

    /** 10dp smooth corners */
    val smooth10 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 60))

    /** 12dp smooth corners — song list items, small cards */
    val smooth12 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 60))

    /** 14dp smooth corners */
    val smooth14 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 14.dp, smoothnessAsPercent = 60))

    /** 16dp smooth corners — album cards, playlist items */
    val smooth16 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 16.dp, smoothnessAsPercent = 60))

    /** 20dp smooth corners — larger cards, buttons */
    val smooth20 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60))

    /** 24dp smooth corners — dialog surfaces, settings items */
    val smooth24 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 24.dp, smoothnessAsPercent = 60))

    /** 28dp smooth corners — bottom sheets, floating panels */
    val smooth28 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60))

    /** 28dp top-only smooth corners — bottom sheets that sit flush on the screen base */
    val smoothTop28 = SynthwaveDynamicShape(
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 28.dp,
            cornerRadiusTR = 28.dp,
            cornerRadiusBL = 0.dp,
            cornerRadiusBR = 0.dp,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        ),
    )

    /** 32dp smooth corners — full-width cards, hero surfaces */
    val smooth32 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 32.dp, smoothnessAsPercent = 60))

    /** 36dp smooth corners — extra-large surfaces */
    val smooth36 = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 36.dp, smoothnessAsPercent = 60))

    /** Fully smooth (pill) — 50dp, used for buttons and chips */
    val smoothPill = SynthwaveDynamicShape(AbsoluteSmoothCornerShape(cornerRadius = 50.dp, smoothnessAsPercent = 60))
}

/**
 * Returns a context-aware smooth corner shape for grouped list items.
 * Outer corners (first/last items) get large radii, inner corners get small radii,
 * producing a cohesive grouped appearance.
 */
fun expressiveListShape(
    index: Int,
    count: Int,
    outerRadius: androidx.compose.ui.unit.Dp = 22.dp,
    innerRadius: androidx.compose.ui.unit.Dp = 8.dp,
): Shape {
    val key = "${index}_${count}_${outerRadius.value}_${innerRadius.value}"
    val cached = expressiveListShapeCache[key]
    if (cached != null) {
        return cached
    }
    val outer = outerRadius
    val inner = innerRadius
    val shape = SynthwaveDynamicShape(
        when {
            count <= 1 -> AbsoluteSmoothCornerShape(outer, 60)
            index == 0 -> AbsoluteSmoothCornerShape(
                cornerRadiusTL = outer,
                cornerRadiusTR = outer,
                cornerRadiusBL = inner,
                cornerRadiusBR = inner,
                smoothnessAsPercentTL = 60,
                smoothnessAsPercentTR = 60,
                smoothnessAsPercentBL = 60,
                smoothnessAsPercentBR = 60,
            )
            index == count - 1 -> AbsoluteSmoothCornerShape(
                cornerRadiusTL = inner,
                cornerRadiusTR = inner,
                cornerRadiusBL = outer,
                cornerRadiusBR = outer,
                smoothnessAsPercentTL = 60,
                smoothnessAsPercentTR = 60,
                smoothnessAsPercentBL = 60,
                smoothnessAsPercentBR = 60,
            )
            else -> AbsoluteSmoothCornerShape(inner, 60)
        },
    )
    expressiveListShapeCache.put(key, shape)
    return shape
}

/**
 * Minimal insertion-ordered LRU standing in for `android.util.LruCache` on
 * non-Android targets: reads refresh recency, writes evict the eldest past
 * [maxSize]. UI-thread-only access, so no locking.
 */
private class ShapeLruCache<V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<String, V>(16, 0.75f)

    operator fun get(key: String): V? {
        val value = map.remove(key) ?: return null
        map[key] = value
        return value
    }

    fun put(key: String, value: V) {
        map.remove(key)
        map[key] = value
        while (map.size > maxSize) {
            map.remove(map.keys.first())
        }
    }
}

private val expressiveListShapeCache = ShapeLruCache<SynthwaveDynamicShape>(128)
