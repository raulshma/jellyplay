package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingStepScaffold(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    isLastStep: Boolean = false,
    canSkip: Boolean = true,
    onNext: () -> Unit,
    onSkip: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val iconAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scaffoldIconAlpha",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scaffoldTitleAlpha",
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 12f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "scaffoldTitleOffset",
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scaffoldSubtitleAlpha",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scaffoldContentAlpha",
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 16f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "scaffoldContentOffset",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { alpha = iconAlpha },
            )
            Spacer(Modifier.height(4.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                alpha = titleAlpha
                translationY = titleOffsetY
            },
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = subtitleAlpha },
        )

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier.graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffsetY
            },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}
