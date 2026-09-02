package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayExpressiveTitles
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_feature_download
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_feature_music
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_feature_stream
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_feature_syncplay
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_welcome_subtitle
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_welcome_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun WelcomeStep(
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current
    val floatingOffset = remember { Animatable(-6f) }
    val glowScale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        visible = true
        if (!reducedMotion) {
            floatingOffset.animateTo(
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            glowScale.animateTo(
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }

    val iconScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.3f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "welcomeIconScale",
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(500),
        label = "welcomeTitleAlpha",
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "welcomeTitleOffset",
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(600, 100),
        label = "welcomeSubtitleAlpha",
    )

    val featuresAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(600, 200),
        label = "welcomeFeaturesAlpha",
    )
    val featuresOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "welcomeFeaturesOffset",
    )

    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
    val glowColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(y = floatingOffset.value.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.3f * glowScale.value),
                                glowColor.copy(alpha = 0f),
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 0.8f * glowScale.value,
                        ),
                    )
                }
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Tabler.Outline.PlayerPlay,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(Res.string.onboarding_welcome_title),
            style = JellyPlayExpressiveTitles.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp,
            modifier = Modifier.graphicsLayer {
                alpha = titleAlpha
                translationY = titleOffset
            },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.onboarding_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = subtitleAlpha },
        )

        Spacer(Modifier.height(32.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = featuresAlpha
                    translationY = featuresOffset
                },
        ) {
            StaggeredFeatureHighlight(
                icon = Tabler.Outline.PlayerPlay,
                text = stringResource(Res.string.onboarding_feature_stream),
                index = 0,
            )
            StaggeredFeatureHighlight(
                icon = Tabler.Outline.Headphones,
                text = stringResource(Res.string.onboarding_feature_music),
                index = 1,
            )
            StaggeredFeatureHighlight(
                icon = Tabler.Outline.CloudDownload,
                text = stringResource(Res.string.onboarding_feature_download),
                index = 2,
            )
            StaggeredFeatureHighlight(
                icon = Tabler.Outline.Users,
                text = stringResource(Res.string.onboarding_feature_syncplay),
                index = 3,
            )
        }
    }
}

@Composable
private fun StaggeredFeatureHighlight(
    icon: ImageVector,
    text: String,
    index: Int,
) {
    var visible by remember { mutableStateOf(false) }
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * AnimationTokens.StaggerDelayPerItem.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(300),
        label = "featureAlpha_$index",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
