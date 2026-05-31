package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

@Composable
fun CompletionStep(
    onStartWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkAnimatable = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        checkAnimatable.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            val color = MaterialTheme.colorScheme.primary
            val progress = checkAnimatable.value
            Canvas(modifier = Modifier.size(120.dp)) {
                val strokeWidth = 6.dp.toPx()
                val circleRadius = (size.minDimension - strokeWidth) / 2f
                drawCircle(
                    color = color,
                    radius = circleRadius,
                    center = center,
                    style = Stroke(width = strokeWidth),
                    alpha = (progress * 2f).coerceIn(0f, 1f),
                )
                if (progress > 0.5f) {
                    val checkProgress = (progress - 0.5f) * 2f
                    val path = Path().apply {
                        moveTo(size.width * 0.3f, size.height * 0.5f)
                        lineTo(size.width * 0.45f, size.height * 0.65f)
                        lineTo(size.width * 0.7f, size.height * 0.35f)
                    }
                    val measuredPath = PathMeasure().apply { setPath(path, false) }
                    val partialPath = Path()
                    measuredPath.getSegment(0f, measuredPath.length * checkProgress, partialPath, true)
                    drawPath(
                        path = partialPath,
                        color = color,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "All Set!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Your JellyPlay is configured and ready to go. You can always change these settings later.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onStartWatching,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = ShapeCache.smooth16,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = "Start Watching",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
