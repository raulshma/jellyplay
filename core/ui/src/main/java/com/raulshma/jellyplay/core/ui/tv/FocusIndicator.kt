package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import kotlin.math.ceil
import kotlin.math.min

object TvFocusDefaults {
    val BorderWidth = 2.dp
    val GlowElevation = 16.dp
    const val GlowAmbientAlpha = 0.5f
    const val GlowSpotAlpha = 0.3f
}

@Stable
data class TvFocusState(
    val isFocused: Boolean = false,
    val scale: Float = 1f,
    val alphaProvider: () -> Float = { 1f },
    val borderWidthProvider: () -> Dp = { 0.dp },
    val glowElevationProvider: () -> Dp = { 0.dp },
    val focusModifier: Modifier = Modifier,
) {
    val alpha: Float get() = alphaProvider()
}

@Composable
fun rememberTvFocusState(
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = TvFocusDefaults.BorderWidth,
): TvFocusState = rememberTvFocusStateImpl(
    focusedBorderWidth = focusedBorderWidth,
    labelSuffix = "",
)

@Composable
fun rememberRowSharedFocusState(
    focusedScale: Float = 1.08f,
    focusedBorderWidth: Dp = TvFocusDefaults.BorderWidth,
): TvFocusState = rememberTvFocusStateImpl(
    focusedBorderWidth = focusedBorderWidth,
    labelSuffix = "Shared",
)

/** Shared body of [rememberTvFocusState] / [rememberRowSharedFocusState]. */
@Composable
private fun rememberTvFocusStateImpl(
    focusedBorderWidth: Dp,
    labelSuffix: String,
): TvFocusState {
    val isTv = LocalTvMode.current
    val focusTokens = LocalJellyPlayUi.current.focus
    var isFocused by remember { mutableStateOf(false) }

    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current

    // Breathing fade alpha animation (TV only). On non-TV devices the breathing
    // fraction is always 0 so the effect is invisible; skip constructing the
    // infinite transition entirely to avoid a continuous animation coroutine
    // driving recomposition on every focusable item. Also skip under reduce
    // motion / performance mode. The transition only exists while the breathing
    // fraction is above zero — reading it through derivedStateOf keeps the
    // per-frame fraction updates out of composition.
    val alphaProvider = if (isTv && !reducedMotion) {
        val breathingFraction = animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            animationSpec = motionScheme.fastSpatialSpec(),
            label = "breathingFraction$labelSuffix"
        )

        val breathingActive by remember { derivedStateOf { breathingFraction.value > 0f } }

        if (breathingActive) {
            val infiniteTransition = rememberInfiniteTransition(label = "tvFocusBreathing$labelSuffix")
            val breathingAlpha = infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.65f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "breathingAlpha$labelSuffix"
            )

            // Wrap in lambda to avoid recomposition on every animation frame
            remember(breathingAlpha, breathingFraction) {
                {
                    1f - (1f - breathingAlpha.value) * breathingFraction.value
                }
            }
        } else {
            remember { { 1f } }
        }
    } else {
        remember { { 1f } }
    }

    val animatedBorder = animateDpAsState(
        targetValue = if (isFocused) {
            if (isTv) focusedBorderWidth else focusTokens.borderWidth
        } else 0.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tvFocusBorder$labelSuffix",
    )

    val animatedGlowElevation = animateDpAsState(
        targetValue = if (isFocused && isTv) TvFocusDefaults.GlowElevation else 0.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "tvFocusGlow$labelSuffix",
    )

    val focusModifier = Modifier.onFocusChanged { focusState ->
        isFocused = focusState.isFocused
    }

    return TvFocusState(
        isFocused = isFocused,
        scale = 1f, // Always 1f to disable scaling on TV focused items
        alphaProvider = alphaProvider,
        borderWidthProvider = { animatedBorder.value },
        glowElevationProvider = { animatedGlowElevation.value },
        focusModifier = focusModifier,
    )
}

@Composable
fun Modifier.tvFocusIndicator(
    focusState: TvFocusState,
    shape: Shape = RectangleShape,
    color: Color? = null,
): Modifier {
    val glowColor = color ?: MaterialTheme.colorScheme.primary
    val borderColor = color ?: MaterialTheme.colorScheme.primary
    val focusShape = shape
    val ambientColor = glowColor.copy(alpha = TvFocusDefaults.GlowAmbientAlpha)
    val spotColor = glowColor.copy(alpha = TvFocusDefaults.GlowSpotAlpha)
    val borderCache = remember { FocusBorderCache() }

    return this
        .graphicsLayer {
            alpha = focusState.alphaProvider()
        }
        .graphicsLayer {
            this.shape = focusShape
            shadowElevation = focusState.glowElevationProvider().toPx()
            ambientShadowColor = ambientColor
            spotShadowColor = spotColor
        }
        .drawWithCache {
            val borderWidthPx = focusState.borderWidthProvider().toPx()
            if (borderWidthPx <= 0f || size.minDimension <= 0f) {
                onDrawWithContent { drawContent() }
            } else {
                val strokeWidthPx = min(ceil(borderWidthPx), ceil(size.minDimension / 2))
                val halfStroke = strokeWidthPx / 2
                val topLeft = Offset(halfStroke, halfStroke)
                val borderSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                // The stroke is larger than the drawing area so just draw a full shape instead
                val fillArea = (strokeWidthPx * 2) > size.minDimension
                when (val outline = focusShape.createOutline(size, layoutDirection, this)) {
                    is Outline.Generic ->
                        drawGenericFocusBorder(borderCache, outline, fillArea, strokeWidthPx, borderColor)
                    is Outline.Rounded ->
                        drawRoundRectFocusBorder(
                            borderCache,
                            outline,
                            topLeft,
                            borderSize,
                            fillArea,
                            strokeWidthPx,
                            borderColor,
                        )
                    is Outline.Rectangle -> onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = SolidColor(borderColor),
                            topLeft = if (fillArea) Offset.Zero else topLeft,
                            size = if (fillArea) size else borderSize,
                            style = if (fillArea) Fill else Stroke(strokeWidthPx),
                        )
                    }
                }
            }
        }
}

/**
 * Lazily-allocated draw caches for the focus border, mirroring
 * [androidx.compose.foundation.BorderStroke]-style border rendering so the
 * animated border width can be resolved at draw time without recomposition.
 */
private class FocusBorderCache(
    private var imageBitmap: ImageBitmap? = null,
    private var canvas: androidx.compose.ui.graphics.Canvas? = null,
    private var canvasDrawScope: CanvasDrawScope? = null,
    private var borderPath: Path? = null,
) {
    fun obtainPath(): Path = borderPath ?: Path().also { borderPath = it }

    fun CacheDrawScope.drawBorderCache(
        borderSize: IntSize,
        config: ImageBitmapConfig,
        block: DrawScope.() -> Unit,
    ): ImageBitmap {
        var targetImageBitmap = imageBitmap
        var targetCanvas = canvas
        // If we previously had allocated a full Argb888 ImageBitmap but are only
        // requiring an alpha mask, just re-use the same ImageBitmap instead
        val compatibleConfig =
            targetImageBitmap?.config == ImageBitmapConfig.Argb8888 ||
                config == targetImageBitmap?.config
        if (
            targetImageBitmap == null ||
            targetCanvas == null ||
            size.width > targetImageBitmap.width ||
            size.height > targetImageBitmap.height ||
            !compatibleConfig
        ) {
            targetImageBitmap =
                ImageBitmap(borderSize.width, borderSize.height, config = config).also {
                    imageBitmap = it
                }
            targetCanvas =
                androidx.compose.ui.graphics.Canvas(targetImageBitmap).also { canvas = it }
        }

        val targetDrawScope = canvasDrawScope ?: CanvasDrawScope().also { canvasDrawScope = it }
        val drawSize = borderSize.toSize()
        targetDrawScope.draw(this, layoutDirection, targetCanvas, drawSize) {
            // Clear the previously rendered portion within this ImageBitmap as
            // we could be re-using it
            drawRect(color = Color.Black, size = drawSize, blendMode = BlendMode.Clear)
            block()
        }
        targetImageBitmap.prepareToDraw()
        return targetImageBitmap
    }
}

/**
 * Border implementation for generic paths (smooth-corner shapes). Draws the
 * stroke into an alpha-8 mask keeping only the inner half, then tints it.
 */
private fun CacheDrawScope.drawGenericFocusBorder(
    cache: FocusBorderCache,
    outline: Outline.Generic,
    fillArea: Boolean,
    strokeWidth: Float,
    color: Color,
): DrawResult =
    if (fillArea) {
        onDrawWithContent {
            drawContent()
            drawPath(outline.path, color)
        }
    } else {
        val config = ImageBitmapConfig.Alpha8
        // The brush is drawn into the mask with the corresponding color including
        // the alpha channel so when tinting we should not apply the alpha as it
        // would end up modulating it twice
        val colorFilter = ColorFilter.tint(color.copy(alpha = 1f))

        val pathBounds = outline.path.getBounds()
        // Mask that includes a rectangle with the original path cut out of it.
        val maskPath =
            cache.obtainPath().apply {
                reset()
                addRect(pathBounds)
                op(this, outline.path, PathOperation.Difference)
            }

        val cacheImageBitmap: ImageBitmap
        val pathBoundsSize =
            IntSize(ceil(pathBounds.width).toInt(), ceil(pathBounds.height).toInt())
        with(cache) {
            // Draw into offscreen bitmap with the size of the path to act as a
            // layer and avoid underdraw into the render target
            cacheImageBitmap = drawBorderCache(pathBoundsSize, config) {
                translate(-pathBounds.left, -pathBounds.top) {
                    // Draw the path with a stroke width twice the provided value.
                    // Because strokes are centered, this draws both an inner and
                    // outer stroke with the desired stroke width.
                    drawPath(
                        path = outline.path,
                        brush = SolidColor(color),
                        style = Stroke(strokeWidth * 2),
                    )

                    // Scale the canvas slightly to cover the background that may
                    // be visible after clearing the outer stroke
                    scale((size.width + 1) / size.width, (size.height + 1) / size.height) {
                        drawPath(
                            path = maskPath,
                            brush = SolidColor(color),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
            }
        }

        onDrawWithContent {
            drawContent()
            translate(pathBounds.left, pathBounds.top) {
                drawImage(cacheImageBitmap, srcSize = pathBoundsSize, colorFilter = colorFilter)
            }
        }
    }

/** Border implementation for simple rounded rects and those with different corner radii */
private fun CacheDrawScope.drawRoundRectFocusBorder(
    cache: FocusBorderCache,
    outline: Outline.Rounded,
    topLeft: Offset,
    borderSize: Size,
    fillArea: Boolean,
    strokeWidthPx: Float,
    color: Color,
): DrawResult {
    val brush = SolidColor(color)
    return if (outline.roundRect.isSimple) {
        val cornerRadius = outline.roundRect.topLeftCornerRadius
        val halfStroke = strokeWidthPx / 2
        val borderStroke = Stroke(strokeWidthPx)
        onDrawWithContent {
            drawContent()
            when {
                fillArea -> {
                    // If the drawing area is smaller than the stroke being drawn
                    // just draw a filled in rounded rect
                    drawRoundRect(brush, cornerRadius = cornerRadius)
                }
                cornerRadius.x < halfStroke -> {
                    // The interior curvature of the stroke would be a sharp edge;
                    // draw a filled rounded rect clipping out the interior rectangle
                    clipRect(
                        strokeWidthPx,
                        strokeWidthPx,
                        size.width - strokeWidthPx,
                        size.height - strokeWidthPx,
                        clipOp = ClipOp.Difference,
                    ) {
                        drawRoundRect(brush, cornerRadius = cornerRadius)
                    }
                }
                else -> {
                    // Draw a stroked rounded rect with the corner radius shrunk by
                    // half of the stroke width so the outer curvature keeps the
                    // desired corner radius.
                    drawRoundRect(
                        brush = brush,
                        topLeft = topLeft,
                        size = borderSize,
                        cornerRadius = cornerRadius.shrink(halfStroke),
                        style = borderStroke,
                    )
                }
            }
        }
    } else {
        val path = cache.obtainPath()
        val roundedRectPath = createRoundRectPath(path, outline.roundRect, strokeWidthPx, fillArea)
        onDrawWithContent {
            drawContent()
            drawPath(roundedRectPath, brush = brush)
        }
    }
}

/** Helper that creates a round rect with the inner region removed by the given stroke width */
private fun createRoundRectPath(
    targetPath: Path,
    roundedRect: RoundRect,
    strokeWidth: Float,
    fillArea: Boolean,
): Path =
    targetPath.apply {
        reset()
        addRoundRect(roundedRect)
        if (!fillArea) {
            val insetPath =
                Path().apply { addRoundRect(createInsetRoundedRect(strokeWidth, roundedRect)) }
            op(this, insetPath, PathOperation.Difference)
        }
    }

private fun createInsetRoundedRect(widthPx: Float, roundedRect: RoundRect) =
    RoundRect(
        left = widthPx,
        top = widthPx,
        right = roundedRect.width - widthPx,
        bottom = roundedRect.height - widthPx,
        topLeftCornerRadius = roundedRect.topLeftCornerRadius.shrink(widthPx),
        topRightCornerRadius = roundedRect.topRightCornerRadius.shrink(widthPx),
        bottomLeftCornerRadius = roundedRect.bottomLeftCornerRadius.shrink(widthPx),
        bottomRightCornerRadius = roundedRect.bottomRightCornerRadius.shrink(widthPx),
    )

/** Shrinks the corner radius by the given value, clamping to 0 if it would go negative */
private fun CornerRadius.shrink(value: Float): CornerRadius =
    CornerRadius(maxOf(0f, this.x - value), maxOf(0f, this.y - value))

fun Modifier.tvFocusChanged(onFocusChanged: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { focusState ->
        onFocusChanged(focusState.isFocused)
    }
