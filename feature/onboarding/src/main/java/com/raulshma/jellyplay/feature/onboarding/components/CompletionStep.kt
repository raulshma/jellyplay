package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayExpressiveTitles
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

@Composable
fun CompletionStep(
    onStartWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkProgress = remember { Animatable(0f) }
    val pulseScale = remember { Animatable(1f) }
    var contentVisible by remember { mutableStateOf(false) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
        contentVisible = true
        kotlinx.coroutines.delay(300)
        buttonVisible = true
    }

    LaunchedEffect(Unit) {
        pulseScale.animateTo(
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "completionTitleAlpha",
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 16f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "completionTitleOffset",
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(500, 100),
        label = "completionSubtitleAlpha",
    )

    val buttonAlpha by animateFloatAsState(
        targetValue = if (buttonVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "completionButtonAlpha",
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "completionButtonScale",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val progress = checkProgress.value

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                }
                .drawBehind {
                    val a = ((progress * 2f).coerceIn(0f, 1f)) * 0.15f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = a),
                                primaryColor.copy(alpha = 0f),
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 0.7f,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val strokeWidth = 6.dp.toPx()
                val circleRadius = (size.minDimension - strokeWidth) / 2f
                drawCircle(
                    color = primaryColor,
                    radius = circleRadius,
                    center = center,
                    style = Stroke(width = strokeWidth),
                    alpha = (progress * 2f).coerceIn(0f, 1f),
                )
                if (progress > 0.5f) {
                    val checkProgress2 = (progress - 0.5f) * 2f
                    val path = Path().apply {
                        moveTo(size.width * 0.3f, size.height * 0.5f)
                        lineTo(size.width * 0.45f, size.height * 0.65f)
                        lineTo(size.width * 0.7f, size.height * 0.35f)
                    }
                    val measuredPath = PathMeasure().apply { setPath(path, false) }
                    val partialPath = Path()
                    measuredPath.getSegment(0f, measuredPath.length * checkProgress2, partialPath, true)
                    drawPath(
                        path = partialPath,
                        color = primaryColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "All Set!",
            style = JellyPlayExpressiveTitles.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = titleAlpha
                translationY = titleOffset
            },
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Your JellyPlay is configured and ready to go. You can always change these settings later.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = subtitleAlpha },
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onStartWatching,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                    alpha = buttonAlpha
                },
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
