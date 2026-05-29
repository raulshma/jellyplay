package com.raulshma.jellyplay.feature.music.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

class CloverShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2
        val centerY = size.height / 2
        val petalRadius = size.width * 0.32f
        val petalOffset = size.width * 0.22f

        // Create 4 petals in a clover pattern
        for (i in 0 until 4) {
            val angle = Math.toRadians((i * 90.0) - 90.0)
            val petalCenterX = centerX + (petalOffset * cos(angle)).toFloat()
            val petalCenterY = centerY + (petalOffset * sin(angle)).toFloat()

            // Draw each petal as a circle
            val petalPath = Path()
            petalPath.addOval(
                androidx.compose.ui.geometry.Rect(
                    left = petalCenterX - petalRadius,
                    top = petalCenterY - petalRadius,
                    right = petalCenterX + petalRadius,
                    bottom = petalCenterY + petalRadius
                )
            )

            if (i == 0) {
                path.addPath(petalPath)
            } else {
                path.addPath(petalPath)
            }
        }

        // Add center circle to connect petals
        val centerRadius = size.width * 0.18f
        path.addOval(
            androidx.compose.ui.geometry.Rect(
                left = centerX - centerRadius,
                top = centerY - centerRadius,
                right = centerX + centerRadius,
                bottom = centerY + centerRadius
            )
        )

        path.close()

        return Outline.Generic(path)
    }
}
