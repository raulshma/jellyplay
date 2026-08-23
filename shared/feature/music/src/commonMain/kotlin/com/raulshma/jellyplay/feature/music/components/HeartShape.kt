package com.raulshma.jellyplay.feature.music.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class HeartShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val width = size.width
        val height = size.height

        path.moveTo(width / 2, height * 0.35f)

        // Left curve
        path.cubicTo(
            x1 = width * 0.0f,
            y1 = height * 0.0f,
            x2 = width * 0.0f,
            y2 = height * 0.6f,
            x3 = width / 2,
            y3 = height
        )

        // Right curve
        path.cubicTo(
            x1 = width,
            y1 = height * 0.6f,
            x2 = width,
            y2 = height * 0.0f,
            x3 = width / 2,
            y3 = height * 0.35f
        )

        path.close()

        return Outline.Generic(path)
    }
}
