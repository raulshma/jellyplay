package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun AnimatedHeroHeader(
    featuredItem: MediaItem,
    getBackdropUrl: (String) -> String,
    height: Dp,
    backgroundColor: Color,
    contentPadding: Dp = 16.dp,
    listState: LazyListState,
    onItemClick: (String) -> Unit,
    onDetailsClick: ((String) -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val parallaxOffset by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.45f
            } else 0f
        }
    }

    val heroIsVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == 0 } || listState.layoutInfo.totalItemsCount == 0 }
    }

    val slowEffects = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val defaultEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = featuredItem,
        transitionSpec = {
            fadeIn(
                animationSpec = slowEffects,
            ) + scaleIn(
                initialScale = 1.02f,
                animationSpec = slowEffects,
            ) togetherWith fadeOut(
                animationSpec = defaultEffects,
            ) + scaleOut(
                targetScale = 0.985f,
                animationSpec = defaultEffects,
            )
        },
        label = "heroRotation",
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp)),
    ) { currentFeatured ->
        HeroHeader(
            item = currentFeatured,
            backdropUrl = getBackdropUrl(currentFeatured.id),
            height = height,
            backgroundColor = backgroundColor,
            contentPadding = contentPadding,
            parallaxOffset = parallaxOffset,
            onClick = { onItemClick(currentFeatured.id) },
            onDetailsClick = onDetailsClick?.let { { it(currentFeatured.id) } },
            onFocusChange = onFocusChange,
            isVisible = heroIsVisible,
        )
    }
}

@Composable
fun HeroHeader(
    item: MediaItem,
    backdropUrl: String,
    height: Dp,
    backgroundColor: Color,
    contentPadding: Dp = 16.dp,
    parallaxOffset: Float = 0f,
    onClick: () -> Unit,
    onDetailsClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    isVisible: Boolean = true,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "heroPress",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "playButtonScale",
    )
    val detailsInteractionSource = remember { MutableInteractionSource() }
    val isDetailsPressed by detailsInteractionSource.collectIsPressedAsState()
    val detailsScale by animateFloatAsState(
        targetValue = if (isDetailsPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "detailsButtonScale",
    )

    val heroTvFocusState = rememberTvFocusState()
    val heroPlayFocusRequester = remember { FocusRequester() }
    val heroDetailsFocusRequester = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(isTv, item.id) {
        if (isTv) {
            heroPlayFocusRequester.requestFocus()
        }
    }

    val breathScale: Float
    val playPulseScale: Float
    val playPulseAlpha: Float
    val ratingPulse: Float

    if (isVisible) {
        val heroTransition = rememberInfiniteTransition(label = "hero_animations")
        val rawBreathScale by heroTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = FancyTransitionEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath"
        )
        breathScale = rawBreathScale

        val rawPlayPulseScale by heroTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = AlphaEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "playPulseScale"
        )
        playPulseScale = rawPlayPulseScale

        val rawPlayPulseAlpha by heroTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = AlphaEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "playPulseAlpha"
        )
        playPulseAlpha = rawPlayPulseAlpha

        val rawRatingPulse by heroTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FancyTransitionEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ratingPulse"
        )
        ratingPulse = rawRatingPulse
    } else {
        breathScale = 1.0f
        playPulseScale = 1.0f
        playPulseAlpha = 0.45f
        ratingPulse = 0.9f
    }

    val heroShape = remember(isTv, adaptiveInfo.windowSizeClass) {
        if (!isTv && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = 0.dp,
                cornerRadiusTR = 0.dp,
                cornerRadiusBL = 36.dp,
                cornerRadiusBR = 14.dp,
                smoothnessAsPercentTL = 60,
                smoothnessAsPercentTR = 60,
                smoothnessAsPercentBL = 60,
                smoothnessAsPercentBR = 60,
            )
        } else {
            RoundedCornerShape(0.dp)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(heroShape)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (!isTv) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
    ) {
        MediaImage(
            url = backdropUrl,
            contentDescription = item.name,
            blurHash = item.blurHashes.backdrop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = parallaxOffset
                    scaleX = breathScale
                    scaleY = breathScale
                },
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    remember(backgroundColor) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                backgroundColor.copy(alpha = 0.3f),
                                backgroundColor.copy(alpha = 0.85f),
                                backgroundColor,
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        )
                    }
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = contentPadding)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        offset = androidx.compose.ui.geometry.Offset(2f, 4f),
                        blurRadius = 8f
                    )
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item.year?.let {
                    InfoChip(text = it.toString())
                }
                item.runTimeTicks?.let { ticks ->
                    val minutes = ticks / 600_000_000
                    InfoChip(text = "${minutes}m")
                }
                item.officialRating?.let {
                    InfoChip(
                        text = it,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        textColor = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
                item.communityRating?.let { rating ->
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)), ShapeCache.smooth8)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                Tabler.Outline.Heart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer {
                                        scaleX = ratingPulse
                                        scaleY = ratingPulse
                                    },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }

            if (item.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    item.genres.forEach { genre ->
                        Box(
                            modifier = Modifier
                                .clip(ShapeCache.smooth12)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                                    ShapeCache.smooth12
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            item.overview?.let { overview ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .then(heroTvFocusState.focusModifier)
                        .tvFocusIndicator(heroTvFocusState, ShapeCache.smoothPill)
                        .focusRequester(heroPlayFocusRequester)
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused || focusState.hasFocus)
                        }
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                ) {
                    if (!isTv) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(2.4f)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = playPulseScale
                                    scaleY = playPulseScale
                                    alpha = playPulseAlpha
                                }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), ShapeCache.smoothPill)
                        )
                    }

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val playGradientBrush = remember(primaryColor) {
                        Brush.verticalGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.85f),
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(ShapeCache.smoothPill)
                            .background(playGradientBrush)
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null,
                                onClick = onClick,
                            )
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                Tabler.Outline.PlayerPlay,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (hasProgress) "Resume" else "Play",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .tvFocusIndicator(heroTvFocusState, ShapeCache.smoothPill)
                        .focusRequester(heroDetailsFocusRequester)
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused || focusState.hasFocus)
                        }
                        .graphicsLayer { scaleX = detailsScale; scaleY = detailsScale }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                ShapeCache.smoothPill
                            )
                            .clickable(
                                interactionSource = detailsInteractionSource,
                                indication = null,
                                onClick = onDetailsClick ?: onClick,
                            )
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    textColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    fontWeight: FontWeight = FontWeight.SemiBold,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    Box(
        modifier = Modifier
            .clip(ShapeCache.smooth8)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), ShapeCache.smooth8)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = fontWeight,
                letterSpacing = letterSpacing,
            ),
            color = textColor,
        )
    }
}
