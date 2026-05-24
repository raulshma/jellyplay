package com.raulshma.jellyplay.feature.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.image.MediaImage

/**
 * Pixel Player–inspired organic blob artwork collage.
 * Shows overlapping album art in circular/oval blob shapes.
 *
 * @param imageUrls List of artwork URLs (uses up to 3)
 */
@Composable
fun BlobArtCollage(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val w = if (maxWidth == Dp.Unspecified || maxWidth == 0.dp) 320.dp else maxWidth
        val h = if (maxHeight == Dp.Unspecified || maxHeight == 0.dp) 220.dp else maxHeight

        Box(
            modifier = Modifier
                .width(w)
                .height(h),
            contentAlignment = Alignment.Center,
        ) {
            // Central large oval blob
            if (imageUrls.isNotEmpty()) {
                MediaImage(
                    url = imageUrls[0],
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = w * 0.62f, height = h)
                        .clip(OvalBlobShape)
                        .align(Alignment.Center),
                    contentScale = ContentScale.Crop,
                )
            }

            // Small circular blob — top-left
            if (imageUrls.size > 1) {
                MediaImage(
                    url = imageUrls[1],
                    contentDescription = null,
                    modifier = Modifier
                        .size(h * 0.33f)
                        .clip(CircleShape)
                        .align(Alignment.TopStart)
                        .offset(x = w * 0.02f, y = h * 0.07f),
                    contentScale = ContentScale.Crop,
                )
            }

            // Small circular blob — right
            if (imageUrls.size > 2) {
                MediaImage(
                    url = imageUrls[2],
                    contentDescription = null,
                    modifier = Modifier
                        .size(h * 0.27f)
                        .clip(CircleShape)
                        .align(Alignment.CenterEnd)
                        .offset(x = -(w * 0.01f), y = -(h * 0.09f)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** Organic oval blob shape using cubic Bézier curves */
private val OvalBlobShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // Start at top-center
            moveTo(w * 0.5f, h * 0.02f)
            // Top-right curve
            cubicTo(w * 0.78f, h * -0.02f, w * 0.98f, h * 0.18f, w * 0.96f, h * 0.4f)
            // Right-bottom curve
            cubicTo(w * 1.0f, h * 0.65f, w * 0.88f, h * 0.88f, w * 0.65f, h * 0.96f)
            // Bottom-left curve
            cubicTo(w * 0.45f, h * 1.02f, w * 0.15f, h * 0.92f, w * 0.06f, h * 0.7f)
            // Left-top curve
            cubicTo(w * -0.02f, h * 0.48f, w * 0.08f, h * 0.12f, w * 0.35f, h * 0.04f)
            // Close back to start
            cubicTo(w * 0.4f, h * 0.02f, w * 0.45f, h * 0.02f, w * 0.5f, h * 0.02f)
            close()
        }
        return Outline.Generic(path)
    }
}
