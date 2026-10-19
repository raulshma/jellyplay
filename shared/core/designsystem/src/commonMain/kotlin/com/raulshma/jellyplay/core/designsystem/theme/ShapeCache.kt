package com.raulshma.jellyplay.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Compose-visible mirror of the active synthwave mode, written by [JellyPlayTheme]
 * after each successful composition.
 *
 * This is snapshot state — not a StateFlow — deliberately: the draw phase can't
 * observe a flow, so reading it inside `Shape.createOutline` made a mode flip
 * invisible to already-composed shapes until some unrelated redraw happened.
 * Reads that occur during composition (the accessors below, and
 * [expressiveListShape]) ARE observed, so a flip recomposes their readers and
 * they hand out a DIFFERENT shape instance — and a changed shape instance is
 * the draw input that deterministically invalidates draw.
 */
internal val _isSynthwaveActive = mutableStateOf(false)

/** Synthwave squares every corner, so one shared zero-corner shape covers all radii. */
private val zeroCornerShape = RoundedCornerShape(0.dp)

/**
 * Cached instances of frequently-used [AbsoluteSmoothCornerShape].
 *
 * [AbsoluteSmoothCornerShape] is significantly more expensive than [RoundedCornerShape][androidx.compose.foundation.shape.RoundedCornerShape]
 * because it computes cubic Bézier curves analytically. In LazyColumn items (cards, list items, etc.)
 * each item pays this cost on first composition. By reusing singleton instances we avoid
 * repeated Path construction for the most common radii.
 *
 * The accessors resolve per synthwave mode: each read branches between the
 * smooth singleton and the shared zero-corner shape, so steady-state reads
 * never construct anything, while a mode flip still changes the returned
 * instance identity (which is what forces already-composed surfaces to redraw).
 */
object ShapeCache {
    /** 4dp smooth corners — tiny badges, micro surfaces */
    val smooth4: Shape get() = synthwaveAware(smooth4Shape)

    /** 8dp smooth corners — compact chips, small surfaces */
    val smooth8: Shape get() = synthwaveAware(smooth8Shape)

    /** 10dp smooth corners */
    val smooth10: Shape get() = synthwaveAware(smooth10Shape)

    /** 12dp smooth corners — song list items, small cards */
    val smooth12: Shape get() = synthwaveAware(smooth12Shape)

    /** 14dp smooth corners */
    val smooth14: Shape get() = synthwaveAware(smooth14Shape)

    /** 16dp smooth corners — album cards, playlist items */
    val smooth16: Shape get() = synthwaveAware(smooth16Shape)

    /** 20dp smooth corners — larger cards, buttons */
    val smooth20: Shape get() = synthwaveAware(smooth20Shape)

    /** 24dp smooth corners — dialog surfaces, settings items */
    val smooth24: Shape get() = synthwaveAware(smooth24Shape)

    /** 28dp smooth corners — bottom sheets, floating panels */
    val smooth28: Shape get() = synthwaveAware(smooth28Shape)

    /** 28dp top-only smooth corners — bottom sheets that sit flush on the screen base */
    val smoothTop28: Shape get() = synthwaveAware(smoothTop28Shape)

    /** 32dp smooth corners — full-width cards, hero surfaces */
    val smooth32: Shape get() = synthwaveAware(smooth32Shape)

    /** 36dp smooth corners — extra-large surfaces */
    val smooth36: Shape get() = synthwaveAware(smooth36Shape)

    /** Fully smooth (pill) — 50dp, used for buttons and chips */
    val smoothPill: Shape get() = synthwaveAware(smoothPillShape)
}

/**
 * The expensive smooth-corner constructions, each built exactly once. Private
 * on purpose: consumers go through [ShapeCache] so the synthwave resolution
 * can't be bypassed.
 */
private val smooth4Shape = AbsoluteSmoothCornerShape(cornerRadius = 4.dp, smoothnessAsPercent = 60)
private val smooth8Shape = AbsoluteSmoothCornerShape(cornerRadius = 8.dp, smoothnessAsPercent = 60)
private val smooth10Shape = AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 60)
private val smooth12Shape = AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 60)
private val smooth14Shape = AbsoluteSmoothCornerShape(cornerRadius = 14.dp, smoothnessAsPercent = 60)
private val smooth16Shape = AbsoluteSmoothCornerShape(cornerRadius = 16.dp, smoothnessAsPercent = 60)
private val smooth20Shape = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60)
private val smooth24Shape = AbsoluteSmoothCornerShape(cornerRadius = 24.dp, smoothnessAsPercent = 60)
private val smooth28Shape = AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60)
private val smoothTop28Shape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = 28.dp,
    cornerRadiusTR = 28.dp,
    cornerRadiusBL = 0.dp,
    cornerRadiusBR = 0.dp,
    smoothnessAsPercentTL = 60,
    smoothnessAsPercentTR = 60,
    smoothnessAsPercentBL = 60,
    smoothnessAsPercentBR = 60,
)
private val smooth32Shape = AbsoluteSmoothCornerShape(cornerRadius = 32.dp, smoothnessAsPercent = 60)
private val smooth36Shape = AbsoluteSmoothCornerShape(cornerRadius = 36.dp, smoothnessAsPercent = 60)
private val smoothPillShape = AbsoluteSmoothCornerShape(cornerRadius = 50.dp, smoothnessAsPercent = 60)

/**
 * Picks the current variant for a cached smooth shape: the smooth singleton
 * itself, or the shared zero-corner shape while synthwave mode is active. The
 * flag read here is observed by composition whenever the call happens during
 * composition (the normal case — shapes are resolved once per composition and
 * passed on as parameters), which is what makes a flip redraw.
 */
private fun synthwaveAware(smooth: Shape): Shape =
    if (_isSynthwaveActive.value) zeroCornerShape else smooth

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
    // Synthwave squares every corner: short-circuit before the LRU so the
    // expensive analytic construction never runs for a mode that won't render
    // it. This early return is also the flag-keyed branch of the cache — the
    // keys themselves stay radii-only — and the flag read remains
    // composition-observed so a flip re-resolves every list shape.
    if (_isSynthwaveActive.value) return zeroCornerShape
    val key = listShapeKey(index, count, outerRadius, innerRadius)
    val cached = expressiveListShapeCache[key]
    if (cached != null) {
        return cached
    }
    val outer = outerRadius
    val inner = innerRadius
    val shape = when {
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
    }
    expressiveListShapeCache.put(key, shape)
    return shape
}

/**
 * The grouped-list shape LRU key packed into one primitive: the
 * old `"${index}_${count}_${outer}_${inner}"` string interpolated a fresh
 * String per grouped-list row per recomposition before the LRU probe. Four
 * 16-bit fields: outer/inner radii quantized to 1/4 dp steps (values under
 * 16384 dp — every real corner radius), then count and index.
 */
private fun listShapeKey(index: Int, count: Int, outerRadius: androidx.compose.ui.unit.Dp, innerRadius: androidx.compose.ui.unit.Dp): Long {
    val outerQ = (outerRadius.value * 4).roundToInt().coerceIn(0, 0xFFFF)
    val innerQ = (innerRadius.value * 4).roundToInt().coerceIn(0, 0xFFFF)
    val countQ = count.coerceIn(0, 0xFFFF)
    val indexQ = index.coerceIn(0, 0xFFFF)
    return (outerQ.toLong() shl 48) or (innerQ.toLong() shl 32) or (countQ.toLong() shl 16) or indexQ.toLong()
}

/**
 * Minimal insertion-ordered LRU standing in for `android.util.LruCache` on
 * non-Android targets: reads refresh recency, writes evict the eldest past
 * [maxSize]. UI-thread-only access, so no locking.
 */
private class ShapeLruCache<V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<Long, V>(16, 0.75f)

    operator fun get(key: Long): V? {
        val value = map.remove(key) ?: return null
        map[key] = value
        return value
    }

    fun put(key: Long, value: V) {
        map.remove(key)
        map[key] = value
        while (map.size > maxSize) {
            map.remove(map.keys.first())
        }
    }
}

private val expressiveListShapeCache = ShapeLruCache<Shape>(128)
