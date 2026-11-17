package com.raulshma.jellyplay.feature.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.JellyPlayLinearProgressIndicator
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_back
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.onboarding.OnboardingStep
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_skip
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_step_progress
import org.jetbrains.compose.resources.stringResource

/**
 * Persistent wizard chrome: back, exact position ("Step 3 of 10"), skip, and a
 * progress line. Replaces the row of ten dots — a single line reads at a glance,
 * the label gives sighted and TalkBack users the same exact position, and back
 * is reachable with one tap instead of only the system gesture.
 *
 * The back slot is absent on the first page (there is nowhere to go) and the
 * skip action is absent on the completion page (there is nothing left to
 * skip). [pageCount] is the total number of wizard pages, not a zero-based
 * index.
 */
@Composable
fun OnboardingProgressHeader(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canGoBack = currentPage > 0
    val canSkip = currentPage < pageCount - 1
    val backFocusState = rememberTvFocusState(focusedScale = 1.04f)
    val skipFocusState = rememberTvFocusState(focusedScale = 1.04f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = canGoBack, enter = fadeIn(), exit = fadeOut()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .then(backFocusState.focusModifier)
                        .tvFocusIndicator(backFocusState, CircleShape),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.ArrowLeft,
                        contentDescription = stringResource(CoreUiRes.string.core_ui_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = stringResource(Res.string.onboarding_step_progress, currentPage + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            AnimatedVisibility(visible = canSkip, enter = fadeIn(), exit = fadeOut()) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .then(skipFocusState.focusModifier)
                        .tvFocusIndicator(skipFocusState, ShapeCache.smooth12),
                ) {
                    Text(
                        text = stringResource(Res.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        JellyPlayLinearProgressIndicator(
            progress = { OnboardingStep.progressFraction(currentPage) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
