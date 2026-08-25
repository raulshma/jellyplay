@file:Suppress("DEPRECATION")
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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvHeroFocusExitHandler
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import com.raulshma.jellyplay.feature.home.generated.resources.home_resume
import com.raulshma.jellyplay.feature.home.generated.resources.home_play
import com.raulshma.jellyplay.feature.home.generated.resources.home_details
import com.raulshma.jellyplay.feature.home.generated.resources.Res

// Hero ambient-animation durations (ms). Named so the tuning is discoverable.
private const val HERO_BREATH_DURATION_MS = 12_000
private const val HERO_PLAY_PULSE_DURATION_MS = 1_800
private const val HERO_RATING_PULSE_DURATION_MS = 2_000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatedHeroHeader(
    featuredItem: MediaItem,
    getBackdropUrl: (String) -> String,
    height: Dp,
    backgroundColor: Color,
    contentPadding: Dp = 16.dp,
    homeBackdropEnabled: Boolean = false,
    listState: LazyListState,
    onItemClick: (String) -> Unit,
    onDetailsClick: ((String) -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    requestInitialFocus: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val isTv = LocalTvMode.current
    val bringIntoViewResponder = remember(listState) {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect = Rect.Zero
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                listState.scrollToItem(0, 0)
            }
        }
    }

    // Exposed as State (not delegated): the parallax value changes on every
    // scroll frame, and its only consumer is the graphicsLayer lambda in
    // HeroHeader. Keeping the read inside that lambda means scroll updates
    // re-draw the layer instead of recomposing this scope and the whole hero
    // tree below it.
    val parallaxOffsetState = remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.45f
            } else 0f
        }
    }

    // Stable wrapper instance (the rememberStableCallback idiom, which is
    // typed for () -> Unit): a fresh `{ parallaxOffsetState.value }` per
    // recomposition would hand HeroHeader a new lambda on every parent
    // invalidation and un-skip the whole header.
    val parallaxOffset: () -> Float = remember(parallaxOffsetState) {
        { parallaxOffsetState.value }
    }

    val heroIsVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == 0 } || listState.layoutInfo.totalItemsCount == 0 }
    }
    // Whether the hero is "prominent" (near the top, not scrolled under
    // content). The attention-grabbing play-pulse animations are gated on
    // this so they only run when the hero is the focal element; once the
    // user scrolls content over the hero, the high-frequency (1.8s) layer
    // redraws stop, cutting continuous draw-phase work while keeping the
    // benign slow breath.
    val heroIsProminent by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < listState.layoutInfo.viewportSize.height / 2 }
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
            .ifElse(isTv, Modifier.bringIntoViewResponder(bringIntoViewResponder)),
    ) { currentFeatured ->
        HeroHeader(
            item = currentFeatured,
            backdropUrl = getBackdropUrl(currentFeatured.id),
            height = height,
            backgroundColor = backgroundColor,
            contentPadding = contentPadding,
            homeBackdropEnabled = homeBackdropEnabled,
            parallaxOffset = parallaxOffset,
            onClick = { onItemClick(currentFeatured.id) },
            onDetailsClick = onDetailsClick?.let { { it(currentFeatured.id) } },
            onFocusChange = onFocusChange,
            requestInitialFocus = requestInitialFocus,
            isVisible = heroIsVisible,
            isProminent = heroIsProminent,
            focusRequester = focusRequester,
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
    homeBackdropEnabled: Boolean = false,
    /** Scroll-coupled parallax; deferred () -> Float so reads stay in draw/layer phase. */
    parallaxOffset: () -> Float = { 0f },
    onClick: () -> Unit,
    onDetailsClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    isVisible: Boolean = true,
    isProminent: Boolean = true,
    requestInitialFocus: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    // Performance mode collapses the four simultaneous hero infinite
    // animations (breath, play pulse scale/alpha, rating pulse) to their
    // static else-branch values — the hero otherwise drives a continuous
    // redraw loop on top of the 1920×1080 backdrop decode.
    val reducedMotion = com.raulshma.jellyplay.core.ui.components.LocalReducedMotion.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Animation values are kept as State and read only inside graphicsLayer
    // lambdas below: delegating them (`by`) would invalidate this whole
    // composable on every animation frame (breath 12s, play-pulse 1.8s,
    // rating-pulse 2s — continuously while the hero is prominent and Home is
    // the launch screen). Layer-property updates need no recomposition.
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "heroPress",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScaleState = animateFloatAsState(
        targetValue = if (isPlayPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "playButtonScale",
    )
    val detailsInteractionSource = remember { MutableInteractionSource() }
    val isDetailsPressed by detailsInteractionSource.collectIsPressedAsState()
    val detailsScaleState = animateFloatAsState(
        targetValue = if (isDetailsPressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "detailsButtonScale",
    )

    val playTvFocusState = rememberTvFocusState()
    val detailsTvFocusState = rememberTvFocusState()
    val heroPlayFocusRequester = focusRequester ?: remember { FocusRequester() }
    val heroDetailsFocusRequester = remember { FocusRequester() }

    // Hero recomposes on every parallax/breath animation frame; memoize the
    // per-item string derivations so they aren't rebuilt per frame.
    val yearText = remember(item.id, item.year) { item.year?.toString() }
    val runtimeText = remember(item.id, item.runTimeTicks) {
        item.runTimeTicks?.let { "${it / 600_000_000}m" }
    }
    val ratingText = remember(item.id, item.communityRating) {
        item.communityRating?.let { formatFixed(it.toDouble(), 1) }
    }

    RequestOrRestoreFocus(
        focusRequester = if (isTv && requestInitialFocus) heroPlayFocusRequester else null,
        debugKey = "hero_play",
    )

    // All four animation values are consumed exclusively by graphicsLayer
    // lambdas below, so they are held as State<Float> (never delegated in
    // this scope) — the loops tick layer properties only, zero recomposition.
    val breathScaleState: androidx.compose.runtime.State<Float>
    val playPulseScaleState: androidx.compose.runtime.State<Float>
    val playPulseAlphaState: androidx.compose.runtime.State<Float>
    val ratingPulseState: androidx.compose.runtime.State<Float>

    // The infinite animations run only while the hero is on screen and is the
    // focal element (near the top, not scrolled under content). The slow
    // 12 s breath and the high-frequency (1.8 s) play-pulse loops otherwise
    // drive continuous draw-phase layer redraws that compete with scroll work
    // and stack on the 1920×1080 backdrop decode; collapsed values match each
    // animation's frame-0 state so the layers render identically at rest.
    if (isVisible && isProminent && !reducedMotion) {
        val heroTransition = rememberInfiniteTransition(label = "hero_animations")
        breathScaleState = heroTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(HERO_BREATH_DURATION_MS, easing = FancyTransitionEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath"
        )
        playPulseScaleState = heroTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(HERO_PLAY_PULSE_DURATION_MS, easing = AlphaEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "playPulseScale"
        )
        playPulseAlphaState = heroTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(HERO_PLAY_PULSE_DURATION_MS, easing = AlphaEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "playPulseAlpha"
        )
        ratingPulseState = heroTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(HERO_RATING_PULSE_DURATION_MS, easing = FancyTransitionEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ratingPulse"
        )
    } else {
        breathScaleState = androidx.compose.runtime.mutableStateOf(1.0f)
        playPulseScaleState = androidx.compose.runtime.mutableStateOf(1.0f)
        playPulseAlphaState = androidx.compose.runtime.mutableStateOf(0.45f)
        ratingPulseState = androidx.compose.runtime.mutableStateOf(0.9f)
    }

    val heroShape = remember(isTv, adaptiveInfo.windowSizeClass) {
        if (!isTv && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) {
            // Bottom corners are squared: the hero artwork fades to transparent
            // at the bottom edge, so a flat bottom merges seamlessly into the
            // screen backdrop instead of showing a rounded image edge.
            smoothCornerShape(
                cornerRadiusTL = 0.dp,
                cornerRadiusTR = 0.dp,
                cornerRadiusBL = 0.dp,
                cornerRadiusBR = 0.dp,
            )
        } else {
            RectangleShape
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(heroShape)
            .graphicsLayer {
                scaleX = pressScaleState.value
                scaleY = pressScaleState.value
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
                // OUTER layer — fixed (no translation). Its only job is to isolate
                // the DstIn erase below into an offscreen buffer so the erased
                // pixels become transparent (letting the ambient backdrop show
                // through) instead of blending against whatever sits behind the
                // hero. Because this layer never moves, the dissolve mask drawn
                // inside it stays anchored to the hero box.
                //
                // In reduced-motion/performance mode the offscreen buffer +
                // DstIn erase is skipped: it forces a per-frame GPU allocation
                // and the parallax/breath animations that justified the dissolve
                // are already collapsed. The bottom edge instead gets a cheap
                // static alpha gradient drawn over it (below).
                .then(
                    if (!reducedMotion) {
                        Modifier.graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                    } else Modifier
                )
                // Dissolve the bottom edge into transparency so the hero melts
                // into the ambient backdrop (a blurred tint of the same artwork)
                // instead of hard-cutting at a rectangular edge. DstIn keeps the
                // image where the mask alpha is opaque and erases it to transparent
                // where it isn't, letting whatever sits behind (HomeBackdrop, or
                // the flat fill when the backdrop is off) show through.
                //
                // The mask is evaluated in THIS node's local coordinates, which
                // belong to the fixed outer layer above — NOT the parallaxing
                // inner layer below. That is deliberate: if the mask shared the
                // parallax layer it would slide down with the image as you scroll,
                // sliding the melt zone below the hero's clipped bottom edge and
                // leaving a hard rectangular cut against the content. Keeping the
                // melt anchored to the box means the bottom always dissolves, so
                // the hero stays seamless with the content at every scroll offset.
                .then(
                    if (!reducedMotion) {
                        // drawWithCache: the dissolve mask brush is built once
                        // per size change instead of on every draw pass (the
                        // parallax/breath animations below redraw every frame).
                        Modifier.drawWithCache {
                            val mask = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    0.55f to Color.Black,
                                    0.85f to Color.Transparent,
                                    1.0f to Color.Transparent,
                                ),
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = mask,
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                    } else {
                        // Cheap static bottom fade drawn over the artwork so the
                        // hero's bottom edge still melts instead of hard-cutting,
                        // without the offscreen buffer. Only the bottom needs it
                        // here (the legibility scrim Box below covers the top).
                        Modifier.drawWithCache {
                            val fade = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.6f to Color.Transparent,
                                    0.85f to backgroundColor.copy(alpha = 0.6f),
                                    1.0f to backgroundColor,
                                ),
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = fade)
                            }
                        }
                    }
                )
                // INNER layer — the image itself parallaxes (scrolls slower than
                // the list) and breathes. It moves underneath the fixed dissolve
                // mask above, so the artwork shifts while the melt stays put.
                // In reduced-motion mode breathScale is 1f and parallaxOffset is
                // still applied (scroll-coupled, not an animation), so this layer
                // stays cheap.
                .graphicsLayer {
                    translationY = parallaxOffset()
                    scaleX = breathScaleState.value
                    scaleY = breathScaleState.value
                },
            contentScale = ContentScale.Crop,
            // Full-bleed hero: decode large enough to stay sharp on a 4K TV.
            // The default 384² is a poster thumbnail size — upscaled full-screen
            // it looks soft. Backdrop URL is now 1920px wide (see
            // ImageUrlProvider.DEFAULT_BACKDROP_WIDTH), so decode matches source.
            // performanceModeAware stays true: MediaImage now tiers the clamp so
            // a ≥1080 request decodes at 768² in performance mode (still crisp
            // full-screen on phones) instead of the poster 256² tier.
            size = CoilSize(1920, 1080),
        )

        // Legibility scrim: darkens only the top third (title/overline sits at
        // the bottom, which is protected by the dissolve into the backdrop, and
        // the ambient backdrop itself is already dark). The bottom is left
        // transparent so that the image, which erodes to transparent via the
        // DstIn mask above, blends straight into the HomeBackdrop behind it —
        // no flat fill seam. When the backdrop is off we still keep the top
        // darkening; the bottom transparent strip just merges into the flat
        // background fill that sits behind the whole screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    remember(backgroundColor, homeBackdropEnabled) {
                        if (homeBackdropEnabled) {
                            // Top legibility only; bottom transparent so the dissolved
                            // artwork hands off to the ambient backdrop seamlessly.
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.45f),
                                    0.3f to Color.Transparent,
                                    0.6f to Color.Transparent,
                                    1.0f to Color.Transparent,
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    backgroundColor.copy(alpha = 0.3f),
                                    backgroundColor,
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            )
                        }
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
                yearText?.let { InfoChip(text = it) }
                runtimeText?.let { InfoChip(text = it) }
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
                ratingText?.let {
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
                                        scaleX = ratingPulseState.value
                                        scaleY = ratingPulseState.value
                                    },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = ratingText,
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
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.ifElse(isTv, Modifier.tvHeroFocusExitHandler()),
            ) {
                val hasProgress = item.playbackPositionTicks != null && item.playbackPositionTicks!! > 0

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .tvFocusIndicator(playTvFocusState, ShapeCache.smoothPill, Color.White)
                        .graphicsLayer { scaleX = playScaleState.value; scaleY = playScaleState.value }
                ) {
                    if (!isTv) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(2.4f)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = playPulseScaleState.value
                                    scaleY = playPulseScaleState.value
                                    alpha = playPulseAlphaState.value
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
                            .ifElse(isTv, playTvFocusState.focusModifier)
                            .focusRequester(heroPlayFocusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChange(focusState.isFocused || focusState.hasFocus)
                            }
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
                                if (hasProgress) stringResource(Res.string.home_resume) else stringResource(Res.string.home_play),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .tvFocusIndicator(detailsTvFocusState, ShapeCache.smoothPill)
                        .graphicsLayer { scaleX = detailsScaleState.value; scaleY = detailsScaleState.value }
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
                            .ifElse(isTv, detailsTvFocusState.focusModifier)
                            .focusRequester(heroDetailsFocusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChange(focusState.isFocused || focusState.hasFocus)
                            }
                            .clickable(
                                interactionSource = detailsInteractionSource,
                                indication = null,
                                onClick = onDetailsClick ?: onClick,
                            )
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(Res.string.home_details),
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
