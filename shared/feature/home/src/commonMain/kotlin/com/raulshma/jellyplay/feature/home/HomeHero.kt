@file:Suppress("DEPRECATION")
package com.raulshma.jellyplay.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import coil3.size.Size as CoilSize
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.model.hasPlaybackPosition
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.components.formatRuntimeLabelFromTicks
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
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import com.raulshma.jellyplay.feature.home.generated.resources.home_hero_pause_video
import com.raulshma.jellyplay.feature.home.generated.resources.home_resume
import com.raulshma.jellyplay.feature.home.generated.resources.home_play
import com.raulshma.jellyplay.feature.home.generated.resources.home_details
import com.raulshma.jellyplay.feature.home.generated.resources.Res

// Hero ambient-animation durations (ms). Named so the tuning is discoverable.
private const val HERO_BREATH_DURATION_MS = 12_000
private const val HERO_PLAY_PULSE_DURATION_MS = 1_800
private const val HERO_RATING_PULSE_DURATION_MS = 2_000

// Reference height the hero dissolve's fractional stops were tuned on: the
// 520dp portrait phone hero, where the 55–85% mask spans ~156dp. Shorter
// heroes (320dp tablet landscape) compress that melt band to ~96dp, which
// reads as a hard cut instead of the phone's soft merge, so the band is
// floored at the reference width.
private val HeroDissolveReferenceHeight = 520.dp

/**
 * The scroll-coupled render signals both hero renderers ([AnimatedHeroHeader]
 * and the TV_SLIDER's [HeroSliderHeader]) read off the home list — the
 * parallax offset and the visibility/prominence/scrolling gates.
 *
 * Values are exposed as State (not delegated): the parallax value changes on
 * every scroll frame, and its only consumer is the graphicsLayer lambda in
 * HeroHeader. Keeping the read inside that lambda means scroll updates
 * re-draw the layer instead of recomposing this scope and the whole hero
 * tree below it.
 */
internal class HeroRenderSignals(
    /**
     * Stable wrapper instance (the rememberStableCallback idiom, which is
     * typed for () -> Unit): a fresh `{ parallaxOffsetState.value }` per
     * recomposition would hand HeroHeader a new lambda on every parent
     * invalidation and un-skip the whole header.
     */
    val parallaxOffset: () -> Float,
    val isVisible: androidx.compose.runtime.State<Boolean>,
    val isProminent: androidx.compose.runtime.State<Boolean>,
    val isScrolling: androidx.compose.runtime.State<Boolean>,
)

@Composable
internal fun rememberHeroRenderSignals(listState: LazyListState): HeroRenderSignals {
    val parallaxOffsetState = remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat() * 0.45f
            } else 0f
        }
    }
    val parallaxOffset = remember(parallaxOffsetState) {
        { parallaxOffsetState.value }
    }
    val heroIsVisible = remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == 0 } || listState.layoutInfo.totalItemsCount == 0 }
    }
    // Whether the hero is "prominent" (near the top, not scrolled under
    // content). The attention-grabbing play-pulse animations are gated on
    // this so they only run when the hero is the focal element; once the
    // user scrolls content over the hero, the high-frequency (1.8s) layer
    // redraws stop, cutting continuous draw-phase work while keeping the
    // benign slow breath.
    val heroIsProminent = remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < listState.layoutInfo.viewportSize.height / 2 }
    }
    // The clip's scroll-defer leg — while the list is scrolling the
    // ambient video unmounts (the same posture the rotation cadence takes to
    // scrolling). Derived like the gates above so it stays a draw-phase
    // read, not a per-scroll-frame recomposition of the header.
    val heroIsScrolling = remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    return remember(listState) {
        HeroRenderSignals(parallaxOffset, heroIsVisible, heroIsProminent, heroIsScrolling)
    }
}

/**
 * Start fraction (0–1 of the hero height) of the bottom dissolve band. The
 * fully-transparent anchor stays at 0.85 of the height on every device; only
 * the band top moves, and never higher than needed to keep the band at least
 * as wide as the reference phone hero's.
 */
private fun heroDissolveStartFraction(
    heightPx: Float,
    proportionalStop: Float,
    referenceHeightPx: Float,
): Float {
    val minBandPx = referenceHeightPx * (0.85f - proportionalStop)
    val bandTopPx = minOf(proportionalStop * heightPx, 0.85f * heightPx - minBandPx)
    return (bandTopPx / heightPx).coerceIn(0f, 1f)
}

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
    /** The curated headline override; null renders the item's own title. */
    headline: String? = null,
    /** The curated subhead (kicker) override; null renders nothing above the title. */
    subhead: String? = null,
    /** The style fold's chrome level ([homeHeroStyleDecision]); STANDARD is the shipped hero. */
    chromeVisibility: HomeHeroChromeVisibility = HomeHeroChromeVisibility.STANDARD,
    /** False (the DETAILED spotlight) insets the hero into a rounded card. */
    fullBleed: Boolean = true,
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

    // The scroll-coupled gates + parallax (see [rememberHeroRenderSignals]).
    val signals = rememberHeroRenderSignals(listState)

    // Inline control: the pause-video chip's session memory. Hoisted here
    // (outside AnimatedContent) so it survives hero rotations AND navigation
    // (Home stays in the back stack) — per-hero-item state would reset on
    // every rotation tick and per-process state would outlive its screen.
    var heroVideoPausedForSession by rememberSaveable { mutableStateOf(false) }

    // ── MINIMAL chrome reveal (the SLIDESHOW style) ──────────────────
    // The metadata overlay stays hidden until the user taps the backdrop
    // (touch) or focuses the hero buttons (TV). The tap latch resets per
    // featured item, so each new slide starts pristine; on TV the focus
    // mirror IS the reveal (leaving the hero hides the text again). The
    // action pills stay composed on TV even when unrevealed — they are the
    // hero's focus entry, and hiding them would make an unfocused slideshow
    // hero unreachable by D-pad.
    var slideshowRevealed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(featuredItem.id) { slideshowRevealed = false }
    var heroFocusHeldLocal by remember { mutableStateOf(false) }
    val chromeRevealed = when (chromeVisibility) {
        HomeHeroChromeVisibility.STANDARD, HomeHeroChromeVisibility.RICH -> true
        HomeHeroChromeVisibility.MINIMAL -> if (isTv) heroFocusHeldLocal else slideshowRevealed
    }
    // The focus wrapper the hero buttons report through — the outer
    // onFocusChange contract is unchanged (the controller's focusInHero
    // mirror still sees every transition); the MINIMAL style additionally
    // latches the reveal. remember'd so the per-recomposition lambda identity
    // doesn't un-skip HeroHeader.
    val revealAwareFocusChange = remember(onFocusChange, chromeVisibility) {
        { focused: Boolean ->
            if (chromeVisibility == HomeHeroChromeVisibility.MINIMAL) heroFocusHeldLocal = focused
            onFocusChange(focused)
        }
    }
    val onChromeRevealRequest = remember { { slideshowRevealed = true } }

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
            // The DETAILED spotlight (fullBleed=false) is a contained card:
            // inset by the screen content padding and clipped to rounded
            // corners. The full-bleed styles render edge-to-edge exactly as
            // the shipped hero always has.
            .then(
                if (fullBleed) {
                    Modifier
                } else {
                    Modifier
                        .padding(start = contentPadding, end = contentPadding, top = 16.dp)
                        .clip(ShapeCache.smooth16)
                }
            )
            .height(height),
    ) { currentFeatured ->
        HeroHeader(
            item = currentFeatured,
            backdropUrl = getBackdropUrl(currentFeatured.id),
            height = height,
            backgroundColor = backgroundColor,
            contentPadding = contentPadding,
            homeBackdropEnabled = homeBackdropEnabled,
            parallaxOffset = signals.parallaxOffset,
            onClick = { onItemClick(currentFeatured.id) },
            onDetailsClick = onDetailsClick?.let { { it(currentFeatured.id) } },
            onFocusChange = revealAwareFocusChange,
            requestInitialFocus = requestInitialFocus,
            focusRequester = focusRequester,
            isVisible = signals.isVisible.value,
            isProminent = signals.isProminent.value,
            isScrolling = signals.isScrolling.value,
            videoPausedForSession = heroVideoPausedForSession,
            onPauseVideoForSession = { heroVideoPausedForSession = true },
            headline = headline,
            subhead = subhead,
            chromeVisibility = chromeVisibility,
            chromeRevealed = chromeRevealed,
            onChromeRevealRequest = onChromeRevealRequest,
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
    /** The list is mid-scroll — the clip's scroll-defer leg (rotation-cadence posture). */
    isScrolling: Boolean = false,
    /** The pause-video chip's session latch (hoisted to [AnimatedHeroHeader]). */
    videoPausedForSession: Boolean = false,
    /** Chip tap — falls back to the static image for the session. */
    onPauseVideoForSession: () -> Unit = {},
    requestInitialFocus: Boolean = true,
    focusRequester: FocusRequester? = null,
    /** The curated headline override; null renders the item's own title. */
    headline: String? = null,
    /** The curated subhead (kicker) override; null renders nothing above the title. */
    subhead: String? = null,
    /** The style fold's chrome level ([homeHeroStyleDecision]); STANDARD is the shipped hero. MINIMAL (SLIDESHOW) suppresses the ambient clip. */
    chromeVisibility: HomeHeroChromeVisibility = HomeHeroChromeVisibility.STANDARD,
    /**
     * Whether the metadata overlay is visible. STANDARD/RICH always pass true;
     * MINIMAL passes the reveal latch — hidden until the user taps (touch) or
     * focuses the hero (TV). Default true keeps the shipped hero byte-identical.
     */
    chromeRevealed: Boolean = true,
    /** Backdrop tap while the MINIMAL chrome is hidden — reveals it instead of navigating (touch only). */
    onChromeRevealRequest: () -> Unit = {},
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
        formatRuntimeLabelFromTicks(item.runTimeTicks)
    }
    val ratingText = remember(item.id, item.communityRating) {
        item.communityRating?.let { formatFixed(it.toDouble(), 1) }
    }

    // ── Video hero billboard ─────────────────────────────────────────
    // The featured candidate's clip — the spec's two-tier source: the server
    // ThemeVideo clip first, the muted YouTube trailer embed second. The
    // loader effect runs as soon as the pref + platform gate hold (one
    // TTL-memoised probe per candidate, so rotation revisit and pause-chip
    // re-mount are free); the MOUNT below waits for the full
    // [heroVideoShouldPlay] gate set — visibility, prominence, focus hold,
    // RESUMED, not scrolling, reduced-motion/performance mode, session
    // pause — so the clip plays only when the hero is actually the focal
    // element. Desktop never gets here (platform-capable false → the static
    // image stays).
    val videoGates = rememberHeroVideoGates()
    val lifecycleResumed = rememberIsLifecycleResumed()
    // The whole metadata leg (loader injection + fetch effect) lives inside
    // the capability+pref branch — the desktop / pref-off composition pays
    // for nothing but the gate booleans (the HomeTrickplayPeek gate posture).
    // The SLIDESHOW style (MINIMAL chrome) never mounts the clip: a pure
    // backdrop slideshow, per the style spec.
    val heroClip: HomeHeroVideoLoader.HeroClip? = if (
        videoGates.prefEnabled &&
        heroVideoPlatformCapable &&
        chromeVisibility != HomeHeroChromeVisibility.MINIMAL
    ) {
        val videoLoader = org.koin.compose.koinInject<HomeHeroVideoLoader>()
        var clip by remember(item.id) { mutableStateOf<HomeHeroVideoLoader.HeroClip?>(null) }
        LaunchedEffect(item.id) {
            clip = videoLoader.heroClip(item.id, item.mediaType)
        }
        clip
    } else {
        null
    }
    val heroVideoUrl: String? = (heroClip as? HomeHeroVideoLoader.HeroClip.ThemeVideo)?.streamUrl
    val heroTrailerKey: String? = (heroClip as? HomeHeroVideoLoader.HeroClip.TrailerEmbed)?.videoKey
    // TV focus-hold: the clip is attention-grabbing, so on TV it must stop
    // the moment D-pad focus leaves the hero buttons (the rotation cadence's
    // focusInHero gate, read at the source). Touch has no focus traversal —
    // the leg always holds there.
    var heroButtonFocused by remember { mutableStateOf(false) }
    val heroFocusHeld = !isTv || heroButtonFocused
    val videoShouldPlay = heroVideoShouldPlay(
        prefEnabled = videoGates.prefEnabled,
        sessionPaused = videoPausedForSession,
        reducedMotion = reducedMotion,
        performanceMode = videoGates.performanceMode,
        isVisible = isVisible,
        isProminent = isProminent,
        heroFocusHeld = heroFocusHeld,
        lifecycleResumed = lifecycleResumed.value,
        isScrolling = isScrolling,
        platformCapable = heroVideoPlatformCapable,
    )
    val heroVideoCrossfadeSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

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
                        // The MINIMAL (SLIDESHOW) chrome's touch reveal: the
                        // first backdrop tap surfaces the metadata, only a
                        // revealed hero navigates. Every other style is always
                        // revealed, so the tap routes through unchanged.
                        onClick = { if (chromeRevealed) onClick() else onChromeRevealRequest() },
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
                                    heroDissolveStartFraction(
                                        heightPx = size.height,
                                        proportionalStop = 0.55f,
                                        referenceHeightPx = HeroDissolveReferenceHeight.toPx(),
                                    ) to Color.Black,
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
                                    heroDissolveStartFraction(
                                        heightPx = size.height,
                                        proportionalStop = 0.6f,
                                        referenceHeightPx = HeroDissolveReferenceHeight.toPx(),
                                    ) to Color.Transparent,
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

        // The ambient clip crossfades in OVER the static image (the mount
        // point the plan pins: between the backdrop image and the legibility
        // scrim). The Crossfade is the whole image→video swap — no separate
        // opacity choreography — and nulling the target on any gate flip
        // fades back to the untouched image beneath.
        var trailerEmbedFailed by remember(item.id) { mutableStateOf(false) }
        Crossfade(
            targetState = if (videoShouldPlay) heroClip else null,
            animationSpec = heroVideoCrossfadeSpec,
            label = "heroVideo",
            modifier = Modifier.fillMaxSize(),
        ) { clip ->
            when (clip) {
                is HomeHeroVideoLoader.HeroClip.ThemeVideo ->
                    AmbientHeroVideoSurface(url = clip.streamUrl, modifier = Modifier.fillMaxSize())

                // The fallback leg: no ThemeVideo → the muted trailer embed
                // (Android/TV; the desktop actual fails fast). An embed
                // failure folds back to the static image for this candidate.
                is HomeHeroVideoLoader.HeroClip.TrailerEmbed ->
                    if (!trailerEmbedFailed) {
                        HeroTrailerEmbedSurface(
                            videoKey = clip.videoKey,
                            modifier = Modifier.fillMaxSize(),
                            onEmbedFailed = { trailerEmbedFailed = true },
                        )
                    }

                null -> {}
            }
        }

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

        // Inline control: the pause-video chip — while the clip plays the
        // hero gains a small top-end affordance; tapping it drops the video
        // for the session (the latch lives on AnimatedHeroHeader). Touch-only
        // on purpose: a focusable chip in the TV hero would inject a stop in
        // the D-pad traversal (poster → play → details → rows), and the TV
        // pref is a separate default-off row anyway.
        if (heroClip != null && videoShouldPlay && !isTv) {
            val pauseChipInteractionSource = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = contentPadding)
                    .clip(ShapeCache.smoothPill)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = pauseChipInteractionSource,
                        indication = null,
                        onClick = onPauseVideoForSession,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Icon(
                    Tabler.Outline.PlayerPause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.home_hero_pause_video),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = contentPadding)
                .padding(bottom = 32.dp)
        ) {
            // ── the metadata leg ── Hidden until revealed in the MINIMAL
            // (SLIDESHOW) chrome; always composed for STANDARD/RICH, so the
            // shipped hero's layout is untouched.
            if (chromeRevealed) {
            // The curated billboard's subhead — a kicker line above the
            // headline. Rendered only when the featured candidate carries a
            // curated subhead; the local hero's layout is unchanged otherwise.
            if (!subhead.isNullOrBlank()) {
                Text(
                    text = subhead,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            // The title sits at the protected (opaque) bottom
                            // of the hero — the kicker carries the same
                            // legibility shadow the headline below renders.
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            offset = androidx.compose.ui.geometry.Offset(2f, 4f),
                            blurRadius = 8f,
                        ),
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = headline?.takeIf { it.isNotBlank() } ?: item.name,
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
                    // The DETAILED spotlight's expanded synopsis; every other
                    // style keeps the shipped two-line cap.
                    maxLines = if (chromeVisibility == HomeHeroChromeVisibility.RICH) 5 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(20.dp))
            } // ── end chromeRevealed metadata leg ──

            // The action pills. On TV they stay composed even when the MINIMAL
            // chrome is unrevealed — they are the hero's focus entry, and
            // hiding them would make an unfocused slideshow hero unreachable
            // by D-pad (focus IS the reveal).
            if (chromeRevealed || isTv) Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.ifElse(isTv, Modifier.tvHeroFocusExitHandler()),
            ) {
                val hasProgress = item.hasPlaybackPosition

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
                                val focused = focusState.isFocused || focusState.hasFocus
                                heroButtonFocused = focused
                                onFocusChange(focused)
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
                                val focused = focusState.isFocused || focusState.hasFocus
                                heroButtonFocused = focused
                                onFocusChange(focused)
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

/**
 * The TV_SLIDER hero (the [HomeHeroStyle.TV_SLIDER] presentation, TV only —
 * the policy folds the style back to the carousel flags everywhere else): a
 * horizontal snap pager over the rotation candidate list — the same pool
 * [AnimatedHeroHeader] rotates through — replacing the timed rotation with
 * explicit D-pad left/right paging and page indicators. One [HeroHeader] per
 * page, so the render code is shared and only the choreography differs.
 *
 * The settled page snaps the hero controller ([HeroController.snapToSliderPage])
 * so the ambient backdrop and the "Surprise Me" state stay in step with what
 * the slider shows; an external featured change (a Surprise pick) pages the
 * slider back to it. The preview-key handler intercepts D-pad left/right
 * before the focused pill so a page move never has to wait for focus search
 * across not-yet-composed pages; up/down (rows) pass through untouched.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HeroSliderHeader(
    candidates: List<MediaItem>,
    controller: HeroController,
    getBackdropUrl: (String) -> String,
    height: Dp,
    backgroundColor: Color,
    contentPadding: Dp = 16.dp,
    homeBackdropEnabled: Boolean = false,
    listState: LazyListState,
    /** The curated billboard copy keyed by item id (the overlay AnimatedHeroHeader reads). */
    curatedHeroOverlay: Map<String, com.raulshma.jellyplay.core.model.CuratedHeroEntry> = emptyMap(),
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
    // The scroll-coupled gates + parallax, identical to the carousel hero's.
    val signals = rememberHeroRenderSignals(listState)
    var heroVideoPausedForSession by rememberSaveable { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = controller.currentFeaturedIndex.coerceAtLeast(0),
        initialPageOffsetFraction = 0f,
        pageCount = { candidates.size },
    )
    // Settled page → controller: the ambient backdrop and Surprise Me follow
    // the slider (the snap is a no-op write when the index already matches).
    LaunchedEffect(controller, pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { controller.snapToSliderPage(it) }
    }
    // Controller → pager: an external featured change (a Surprise pick) pages
    // the slider; a snap the slider itself produced is already settled here,
    // so the two effects cannot chase each other.
    LaunchedEffect(controller, pagerState) {
        snapshotFlow { controller.currentFeaturedIndex }.collect { index ->
            if (index in candidates.indices && index != pagerState.settledPage) {
                pagerState.animateScrollToPage(index)
            }
        }
    }

    val scope = rememberCoroutineScope()
    val pageBy: (Int) -> Unit = { delta ->
        if (candidates.isNotEmpty()) {
            val target = (pagerState.currentPage + delta).coerceIn(0, candidates.lastIndex)
            if (target != pagerState.currentPage) {
                scope.launch { pagerState.animateScrollToPage(target) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .ifElse(isTv, Modifier.bringIntoViewResponder(bringIntoViewResponder))
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            pageBy(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            pageBy(+1)
                            true
                        }
                        else -> false
                    }
                },
        ) { page ->
            val item = candidates.getOrNull(page) ?: return@HorizontalPager
            val curated = curatedHeroOverlay[item.id]
            HeroHeader(
                item = item,
                backdropUrl = getBackdropUrl(item.id),
                height = height,
                backgroundColor = backgroundColor,
                contentPadding = contentPadding,
                homeBackdropEnabled = homeBackdropEnabled,
                parallaxOffset = signals.parallaxOffset,
                onClick = { onItemClick(item.id) },
                onDetailsClick = onDetailsClick?.let { { it(item.id) } },
                onFocusChange = onFocusChange,
                requestInitialFocus = requestInitialFocus,
                focusRequester = focusRequester,
                isVisible = signals.isVisible.value,
                isProminent = signals.isProminent.value,
                // A pager slide is the scrolling leg too — the ambient clip
                // unmounts mid-slide (the same posture the list scroll takes).
                isScrolling = signals.isScrolling.value || pagerState.isScrollInProgress,
                videoPausedForSession = heroVideoPausedForSession,
                onPauseVideoForSession = { heroVideoPausedForSession = true },
                headline = curated?.headline,
                subhead = curated?.subhead,
            )
        }

        // Page indicators: the D-pad slider's position affordance (the timed
        // carousel renders none — its position IS the rotation). Top-center:
        // the bottom edge belongs to the metadata leg, and TV screens keep the
        // top strip clear of the dissolve.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .clip(ShapeCache.smoothPill)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            candidates.indices.forEach { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (active) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        ),
                )
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
