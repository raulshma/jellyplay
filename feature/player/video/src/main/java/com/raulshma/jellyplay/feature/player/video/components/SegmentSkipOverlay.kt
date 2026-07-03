package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.model.MediaSegmentType
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
fun SegmentSkipOverlay(
    isVisible: Boolean,
    segmentType: MediaSegmentType,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    SkipButtonOverlay(
        isVisible = isVisible,
        text = segmentType.skipLabel,
        onSkip = onSkip,
        modifier = modifier,
        focusRequester = focusRequester,
    )
}

@Composable
fun IntroSkipOverlay(
    isVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    SkipButtonOverlay(
        isVisible = isVisible,
        text = "Skip Intro",
        onSkip = onSkip,
        modifier = modifier,
        focusRequester = focusRequester,
    )
}

@Composable
fun CreditsSkipOverlay(
    isVisible: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    SkipButtonOverlay(
        isVisible = isVisible,
        text = "Skip Credits",
        onSkip = onSkip,
        modifier = modifier,
        focusRequester = focusRequester,
    )
}

@Composable
private fun SkipButtonOverlay(
    isVisible: Boolean,
    text: String,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val isTv = LocalTvMode.current
    val skipFocusState = rememberTvFocusState(focusedScale = 1.06f)
    val localFocusRequester = remember { FocusRequester() }
    val tvFocusRequester = focusRequester ?: localFocusRequester

    LaunchedEffect(isVisible, isTv) {
        if (isVisible && isTv) {
            kotlinx.coroutines.delay(300)
            tvFocusRequester.tryRequestFocus("tv_skip_segment")
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(150, easing = AlphaEasing)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150, easing = PointToPointEasing)) + slideInHorizontally(animationSpec = tween(400, easing = PointToPointEasing), initialOffsetX = { it / 3 }),
        exit = fadeOut(tween(200, easing = AlphaEasing)) + scaleOut(targetScale = 0.85f, animationSpec = tween(200, easing = PointToPointEasing)) + slideOutHorizontally(animationSpec = tween(400, easing = PointToPointEasing), targetOffsetX = { it / 3 }),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(ShapeCache.smoothPill)
                .background(playerOnScrim().copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), ShapeCache.smoothPill)
                .ifElse(isTv, Modifier.focusRequester(tvFocusRequester))
                .then(skipFocusState.focusModifier)
                .tvFocusIndicator(skipFocusState, ShapeCache.smoothPill)
                .clickable(onClick = onSkip)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.PlayerTrackNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
