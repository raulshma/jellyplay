package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.size.Size as CoilSize
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.R
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.preview.LocalMediaPreviewController
import com.raulshma.jellyplay.core.ui.preview.toMediaPreview
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import java.time.LocalDate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SeerrMediaCard(
    item: SeerrSearchItem,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    clipToShape: Boolean = false,
) {
    val isTv = LocalTvMode.current

    // --- Loading shimmer / glow state machine (Seerr-only affordance) --------
    // The card glows while a request is in flight. Kept here (not in the
    // scaffold) because no other card has a loading state. The scaffold owns
    // the press/focus scale; the animated border below carries the load signal.
    val shimmerOffset = remember { Animatable(-500f) }
    val glowAlpha = remember { Animatable(0.3f) }
    val reducedMotion = LocalReducedMotion.current

    LaunchedEffect(isLoading) {
        if (isLoading) {
            // Skip the ambient shimmer/glow loops under reduce motion / performance mode;
            // the card just rests at its initial values while loading.
            if (reducedMotion) return@LaunchedEffect
            launch {
                while (isActive) {
                    shimmerOffset.animateTo(
                        1500f,
                        animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
                    )
                    shimmerOffset.snapTo(-500f)
                }
            }
            launch {
                while (isActive) {
                    glowAlpha.animateTo(0.8f, animationSpec = tween(800, easing = FancyTransitionEasing))
                    glowAlpha.animateTo(0.3f, animationSpec = tween(800, easing = FancyTransitionEasing))
                }
            }
        } else {
            shimmerOffset.snapTo(-500f)
            glowAlpha.snapTo(0.3f)
        }
    }

    // --- Derived display state ----------------------------------------------
    val isUpcoming = remember(item.releaseDate, item.firstAirDate) {
        val dateStr = item.releaseDate ?: item.firstAirDate
        if (dateStr.isNullOrBlank()) false
        else {
            try {
                val now = LocalDate.now()
                val releaseDate = LocalDate.parse(dateStr)
                releaseDate.isAfter(now)
            } catch (e: Exception) {
                false
            }
        }
    }

    val mediaStatus = remember(item.mediaInfo?.status) {
        item.mediaInfo?.status?.let { SeerrMediaStatus.fromValue(it) } ?: SeerrMediaStatus.UNKNOWN
    }
    val isAvailable = remember(mediaStatus) {
        mediaStatus == SeerrMediaStatus.AVAILABLE ||
            mediaStatus == SeerrMediaStatus.PARTIALLY_AVAILABLE
    }
    val isPending = remember(mediaStatus) {
        mediaStatus == SeerrMediaStatus.PENDING ||
            mediaStatus == SeerrMediaStatus.PROCESSING
    }
    val hasRequest = item.mediaInfo?.requests?.isNotEmpty() == true

    // --- Border: animated glow when loading, themed default otherwise --------
    // The glow animates entirely at draw phase: a static remembered
    // BorderStroke with the full-alpha gradient, and the scaffold draws it
    // inside a layer whose alpha reads glowAlpha at draw time — no
    // recomposition and no per-tick stroke allocation. Layer alpha multiplies
    // the same gradient, so the rendered stroke is identical to rebuilding the
    // color list per frame.
    val glowColor = MaterialTheme.colorScheme.primary
    val loadingBorder = remember(glowColor) {
        BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.6f),
                    glowColor.copy(alpha = 1f),
                    glowColor.copy(alpha = 0.6f),
                ),
                start = Offset.Zero,
                end = Offset(1000f, 1500f),
            ),
        )
    }

    val shimmerBaseColor = MaterialTheme.colorScheme.onSurface
    val shimmerColors = remember(shimmerBaseColor) {
        listOf(
            shimmerBaseColor.copy(alpha = 0.0f),
            shimmerBaseColor.copy(alpha = 0.18f),
            shimmerBaseColor.copy(alpha = 0.0f),
        )
    }

    // --- Peek preview via the generalized factory overload ------------------
    val previewController = LocalMediaPreviewController.current
    val previewFactory = if (previewController != null) {
        remember(item, imageUrl) {
            { sourceBounds: androidx.compose.ui.geometry.Rect? ->
                item.toMediaPreview(
                    posterUrl = imageUrl,
                    backdropUrl = item.backdropUrl,
                    sourceBounds = sourceBounds,
                )
            }
        }
    } else null

    MediaCardScaffold(
        onClick = onClick,
        image = { imageModifier ->
            if (imageUrl != null) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = item.displayName,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                    crossfade = false,
                    // Poster card (2:3, fillMaxWidth). ~360×540 covers ~2× density
                    // for typical grid card widths without the 512×512 over-decode.
                    size = CoilSize(360, 540),
                )
            } else {
                Box(
                    modifier = imageModifier
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.displayName.take(2),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        title = item.displayName,
        modifier = modifier,
        aspectRatio = 2f / 3f,
        enabled = !isLoading,
        clipToShape = clipToShape,
        border = if (isLoading) loadingBorder else null,
        borderAlpha = if (isLoading) {
            // Read inside the scaffold's graphicsLayer lambda — draw-phase only.
            { glowAlpha.value }
        } else null,
        titleColor = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        previewFactory = previewFactory,
        overlays = {
            if (isLoading) {
                // Shimmer sweep drawn behind-phase: the animated offset is read
                // inside the draw lambda, so each tick redraws this overlay
                // without recomposing the card (or any sibling loading card).
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRect(
                                Brush.linearGradient(
                                    colors = shimmerColors,
                                    start = Offset(shimmerOffset.value, 0f),
                                    end = Offset(shimmerOffset.value + 400f, 400f),
                                ),
                            )
                        }
                )
            }

            if (!isLoading) {
                if (item.voteAverage != null) {
                    RatingBadge(
                        rating = item.voteAverage,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }

                SeerrMediaLabelBadge(
                    isUpcoming = isUpcoming,
                    mediaType = item.mediaType,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )

                if (isAvailable || isPending || hasRequest) {
                    SeerrStatusBadge(
                        isAvailable = isAvailable,
                        isPending = isPending,
                        hasRequest = hasRequest,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }

                if (onRequestClick != null && !isAvailable && !hasRequest) {
                    val requestBtnFocusState = rememberTvFocusState(focusedScale = 1.12f)
                    IconButton(
                        onClick = onRequestClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 4.dp)
                            .then(requestBtnFocusState.focusModifier)
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .tvFocusIndicator(requestBtnFocusState, ShapeCache.smooth8),
                    ) {
                        Icon(
                            Tabler.Outline.Plus,
                            contentDescription = stringResource(R.string.core_ui_request),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(2.dp),
                        )
                    }
                }
            }
        },
        footer = {
            if (item.year != null) {
                Text(
                    text = item.year.toString(),
                    style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * Bottom-left UPCOMING / SERIES / MOVIE label for Seerr cards. Routes through
 * [GlassBadge] (the shared badge primitive) instead of a hand-rolled
 * background box, so badge styling stays consistent with the rest of the app.
 */
@Composable
private fun SeerrMediaLabelBadge(
    isUpcoming: Boolean,
    mediaType: String,
    modifier: Modifier = Modifier,
) {
    val label = remember(isUpcoming, mediaType) {
        when {
            isUpcoming -> "UPCOMING"
            mediaType.equals("tv", ignoreCase = true) -> "SERIES"
            mediaType.equals("movie", ignoreCase = true) -> "MOVIE"
            else -> mediaType.uppercase()
        }
    }
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val surface = MaterialTheme.colorScheme.surface
    val background = remember(isUpcoming, tertiaryContainer, surface) {
        if (isUpcoming) tertiaryContainer.copy(alpha = 0.9f) else surface.copy(alpha = 0.6f)
    }
    val borderColor = remember(isUpcoming) {
        if (isUpcoming) tertiaryContainer.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f)
    }
    val textColor = if (isUpcoming) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    GlassBadge(
        modifier = modifier,
        background = background,
        borderColor = borderColor,
        shape = ShapeCache.smooth4,
        horizontalPadding = 6.dp,
        verticalPadding = 2.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                letterSpacing = 0.5.sp,
            ),
            color = textColor,
        )
    }
}

/**
 * Top-end availability/status glyph (✓ available, ⏳ pending, → requested) for
 * Seerr cards. Built on [GlassBadge].
 */
@Composable
private fun SeerrStatusBadge(
    isAvailable: Boolean,
    isPending: Boolean,
    hasRequest: Boolean,
    modifier: Modifier = Modifier,
) {
    val badgeColor = remember(isAvailable, isPending, hasRequest) {
        when {
            isAvailable -> StatusColors.available
            isPending -> StatusColors.pending
            hasRequest -> StatusColors.requested
            else -> Color.Transparent
        }
    }
    if (badgeColor == Color.Transparent) return
    val badgeTextColor = remember(badgeColor) {
        if (isLightColor(badgeColor)) Color.Black else Color.White
    }
    val glyph = when {
        isAvailable -> "✓"
        isPending -> "⏳"
        hasRequest -> "→"
        else -> ""
    }
    GlassBadge(
        modifier = modifier,
        background = badgeColor.copy(alpha = 0.9f),
        shape = ShapeCache.smooth4,
        horizontalPadding = 6.dp,
        verticalPadding = 2.dp,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelSmall,
            color = badgeTextColor,
        )
    }
}
