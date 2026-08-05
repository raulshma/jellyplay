package com.raulshma.jellyplay.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowRight
import com.raulshma.jellyplay.feature.onboarding.components.AppearanceStep
import com.raulshma.jellyplay.feature.onboarding.components.AudioPlayerStep
import com.raulshma.jellyplay.feature.onboarding.components.CompletionStep
import com.raulshma.jellyplay.feature.onboarding.components.HomeLayoutStep
import com.raulshma.jellyplay.feature.onboarding.components.OnboardingPagerIndicator
import com.raulshma.jellyplay.feature.onboarding.components.PerformanceStep
import com.raulshma.jellyplay.feature.onboarding.components.SeerrStep
import com.raulshma.jellyplay.feature.onboarding.components.SecurityStep
import com.raulshma.jellyplay.feature.onboarding.components.SubtitlesStep
import com.raulshma.jellyplay.feature.onboarding.components.VideoPlayerStep
import com.raulshma.jellyplay.feature.onboarding.components.WelcomeStep
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { OnboardingStep.count })
    val scope = rememberCoroutineScope()
    var bottomBarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { bottomBarVisible = true }

    // Wizard Back: step to the previous page, or fall through to the system (exit)
    // when on the first page. Without this, remote BACK relies solely on the nav-
    // host pop and skips the in-wizard step-back the D-pad left arrow already
    // provides — inconsistent on TV.
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onDpadKey(
                            onRight = {
                                if (pagerState.currentPage < OnboardingStep.count - 1) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                    true
                                } else false
                            },
                            onLeft = {
                                if (pagerState.currentPage > 0) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                    true
                                } else false
                            },
                        ),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (OnboardingStep.entries[page]) {
                        OnboardingStep.WELCOME -> {
                            WelcomeStep(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.APPEARANCE -> {
                            AppearanceStep(
                                themeMode = preferences.themeMode,
                                dynamicTheming = preferences.theme.dynamicTheming,
                                oledMode = preferences.theme.oledMode,
                                contrastLevel = preferences.contrastLevel,
                                accentColorSwatch = preferences.theme.accentColorSwatch,
                                colorStyle = preferences.theme.colorStyle,
                                homeHeroEnabled = preferences.homeHeroEnabled,
                                onThemeModeChange = viewModel::setThemeMode,
                                onDynamicThemingChange = viewModel::setDynamicTheming,
                                onOledModeChange = viewModel::setOledMode,
                                onContrastLevelChange = viewModel::setContrastLevel,
                                onAccentColorSwatchChange = viewModel::setAccentColorSwatch,
                                onColorStyleChange = viewModel::setColorStyle,
                                onHomeHeroEnabledChange = viewModel::setHomeHeroEnabled,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.PERFORMANCE -> {
                            PerformanceStep(
                                performanceMode = preferences.performanceMode,
                                onPerformanceModeChange = viewModel::setPerformanceMode,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.HOME_LAYOUT -> {
                            HomeLayoutStep(
                                homeMode = preferences.homeMode,
                                navBarShowLabels = preferences.navBarShowLabels,
                                enabledHomeSectionTypes = preferences.enabledHomeSectionTypes,
                                onHomeModeChange = viewModel::setHomeMode,
                                onNavBarShowLabelsChange = viewModel::setNavBarShowLabels,
                                onEnabledHomeSectionTypesChange = viewModel::setEnabledHomeSectionTypes,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.VIDEO_PLAYER -> {
                            VideoPlayerStep(
                                preferredPlayer = preferences.preferredPlayer,
                                streamingQuality = preferences.streamingQuality,
                                seekDurationMs = preferences.videoSeekDurationMs,
                                gesturesEnabled = preferences.videoGesturesEnabled,
                                defaultOrientation = preferences.videoDefaultOrientation,
                                autoplayNext = preferences.videoAutoplayNext,
                                onPreferredPlayerChange = viewModel::setPreferredPlayer,
                                onStreamingQualityChange = viewModel::setStreamingQuality,
                                onSeekDurationChange = viewModel::setVideoSeekDurationMs,
                                onGesturesEnabledChange = viewModel::setVideoGesturesEnabled,
                                onDefaultOrientationChange = viewModel::setVideoDefaultOrientation,
                                onAutoplayNextChange = viewModel::setVideoAutoplayNext,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.AUDIO_PLAYER -> {
                            AudioPlayerStep(
                                defaultSpeed = preferences.audioDefaultSpeed,
                                gaplessEnabled = preferences.audioGaplessEnabled,
                                crossfadeDurationMs = preferences.audioCrossfadeDurationMs,
                                normalizationEnabled = preferences.audioNormalizationEnabled,
                                autoplayNext = preferences.audioAutoplayNext,
                                onDefaultSpeedChange = viewModel::setAudioDefaultSpeed,
                                onGaplessEnabledChange = viewModel::setGaplessEnabled,
                                onCrossfadeDurationChange = viewModel::setCrossfadeDurationMs,
                                onNormalizationEnabledChange = viewModel::setAudioNormalizationEnabled,
                                onAutoplayNextChange = viewModel::setAudioAutoplayNext,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.SUBTITLES -> {
                            SubtitlesStep(
                                subtitleStyle = preferences.subtitleStyle,
                                onSubtitleStyleChange = viewModel::setSubtitleStyle,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.SECURITY -> {
                            val biometricAvailability = com.raulshma.jellyplay.core.ui.components.rememberBiometricAvailability()
                            SecurityStep(
                                pinLockEnabled = preferences.pinLockEnabled,
                                biometricLockEnabled = preferences.biometricLockEnabled,
                                autoLockTimerMs = preferences.autoLockTimerMs,
                                onPinLockEnabledChange = viewModel::setPinLockEnabled,
                                onPinHashSet = viewModel::setPinHash,
                                biometricAvailable = biometricAvailability == com.raulshma.jellyplay.core.ui.components.BiometricAuthHelper.Availability.AVAILABLE,
                                onBiometricLockEnabledChange = viewModel::setBiometricLockEnabled,
                                onAutoLockTimerMsChange = viewModel::setAutoLockTimerMs,
                                hashPin = viewModel::hashPin,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.SEERR -> {
                            val seerrPrefs by viewModel.seerrPreferences.collectAsStateWithLifecycle()
                            SeerrStep(
                                seerrPreferences = seerrPrefs,
                                onSetServerUrl = viewModel::setSeerrServerUrl,
                                onSetApiKey = viewModel::setSeerrApiKey,
                                onSetAuthMethod = viewModel::setSeerrAuthMethod,
                                onSetUsername = viewModel::setSeerrUsername,
                                onSetEmail = viewModel::setSeerrEmail,
                                onSetPassword = viewModel::setSeerrPassword,
                                onSetEnabled = viewModel::setSeerrEnabled,
                                onSetSearchEnabled = viewModel::setSeerrSearchEnabled,
                                onSetRecommendationsEnabled = viewModel::setSeerrRecommendationsEnabled,
                                onSetDiscoverEnabled = viewModel::setSeerrDiscoverEnabled,
                                onSetStreamingRegion = viewModel::setSeerrStreamingRegion,
                                onSetDiscoverRegion = viewModel::setSeerrDiscoverRegion,
                                onDisconnect = viewModel::seerrDisconnect,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.COMPLETION -> {
                            CompletionStep(
                                onStartWatching = {
                                    viewModel.completeOnboarding()
                                    onComplete()
                                },
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    initialOffsetY = { it },
                ) + fadeIn(),
            ) {
                OnboardingBottomBar(
                    pagerState = pagerState,
                    onSkip = {
                        // Skip now jumps to the review/finish step (CompletionStep)
                        // instead of instantly completing — see OnboardingViewModel
                        // .skipOnboarding. Animate the pager there and let the user
                        // confirm via "Start Watching"; do NOT call onComplete() here
                        // or an accidental Skip permanently dismisses the wizard.
                        viewModel.skipOnboarding()
                        scope.launch {
                            pagerState.animateScrollToPage(OnboardingStep.count - 1)
                        }
                    },
                    onNext = {
                        scope.launch {
                            if (pagerState.currentPage < OnboardingStep.count - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingBottomBar(
    pagerState: PagerState,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLastPage = pagerState.currentPage == OnboardingStep.count - 1
    val isTv = LocalTvMode.current
    val nextFocusRequester = remember { FocusRequester() }
    val skipFocusState = rememberTvFocusState(focusedScale = 1.04f)
    val nextFocusState = rememberTvFocusState(focusedScale = 1.04f)
    // Capture once in composable scope; AnimatedContent's transitionSpec is not composable.
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(Unit) {
        if (isTv) nextFocusRequester.tryRequestFocus("onboarding_next")
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Row(
            modifier = modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = isLastPage,
                transitionSpec = {
                    fadeIn(fadeSpec) togetherWith
                            fadeOut(fadeSpec)
                },
                label = "skipButtonTransition",
            ) { last ->
                if (!last) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.then(skipFocusState.focusModifier).tvFocusIndicator(skipFocusState, ShapeCache.smooth12),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            OnboardingPagerIndicator(
                pageCount = OnboardingStep.count,
                currentPage = pagerState.currentPage,
            )

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = isLastPage,
                transitionSpec = {
                    fadeIn(fadeSpec) togetherWith
                            fadeOut(fadeSpec)
                },
                label = "nextButtonTransition",
            ) { last ->
                if (!last) {
                    FilledTonalButton(
                        onClick = onNext,
                        modifier = Modifier.focusRequester(nextFocusRequester)
                            .then(nextFocusState.focusModifier)
                            .tvFocusIndicator(nextFocusState, ShapeCache.smooth12),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_next),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Tabler.Outline.ArrowRight,
                            contentDescription = null,
                        )
                    }
                } else {
                    Spacer(Modifier.width(96.dp))
                }
            }
        }
    }
}
