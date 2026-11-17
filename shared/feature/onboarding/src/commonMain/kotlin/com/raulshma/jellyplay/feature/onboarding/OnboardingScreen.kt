package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.feature.onboarding.generated.resources.Res
import com.raulshma.jellyplay.feature.onboarding.generated.resources.onboarding_next
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowRight
import com.raulshma.jellyplay.feature.onboarding.components.AppearanceStep
import com.raulshma.jellyplay.feature.onboarding.components.AudioPlayerStep
import com.raulshma.jellyplay.feature.onboarding.components.CompletionStep
import com.raulshma.jellyplay.feature.onboarding.components.HomeLayoutStep
import com.raulshma.jellyplay.feature.onboarding.components.OnboardingProgressHeader
import com.raulshma.jellyplay.feature.onboarding.components.PerformanceStep
import com.raulshma.jellyplay.feature.onboarding.components.SeerrStep
import com.raulshma.jellyplay.feature.onboarding.components.SecurityStep
import com.raulshma.jellyplay.feature.onboarding.components.SubtitlesStep
import com.raulshma.jellyplay.feature.onboarding.components.VideoPlayerStep
import com.raulshma.jellyplay.feature.onboarding.components.WelcomeStep
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { OnboardingStep.count })
    val scope = rememberCoroutineScope()
    val lastPage = OnboardingStep.count - 1
    var bottomBarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { bottomBarVisible = true }

    // Every page turn lands a pending debounced Seerr field write first, so the
    // last keystroke isn't lost when a step is left by button, D-pad or system
    // back. Targets are clamped so a fast double-tap can never leave the wizard.
    fun navigateToPage(target: Int) {
        scope.launch {
            viewModel.seerrEditActions.flushPendingFieldWrites()
            pagerState.animateScrollToPage(target.coerceIn(0, lastPage))
        }
    }

    // Wizard Back: step to the previous page, or fall through to the system (exit)
    // when on the first page. Without this, remote/system BACK relies solely on the
    // nav-host pop and skips the in-wizard step-back the D-pad left arrow already
    // provides — inconsistent on TV.
    JellyPlayBackHandler(enabled = pagerState.currentPage > 0) {
        navigateToPage(pagerState.currentPage - 1)
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            OnboardingProgressHeader(
                currentPage = pagerState.currentPage,
                pageCount = OnboardingStep.count,
                onBack = { navigateToPage(pagerState.currentPage - 1) },
                onSkip = {
                    // Skip exits onboarding from any page. Land the pending
                    // debounced Seerr write first so the last keystroke isn't
                    // lost; the wizard can be re-run from Settings → Setup wizard.
                    scope.launch {
                        viewModel.seerrEditActions.flushPendingFieldWrites()
                        viewModel.completeOnboarding()
                        onComplete()
                    }
                },
            )

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
                                if (pagerState.currentPage < lastPage) {
                                    navigateToPage(pagerState.currentPage + 1)
                                    true
                                } else false
                            },
                            onLeft = {
                                if (pagerState.currentPage > 0) {
                                    navigateToPage(pagerState.currentPage - 1)
                                    true
                                } else false
                            },
                        ),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (OnboardingStep.entries[page]) {
                        OnboardingStep.WELCOME -> {
                            WelcomeStep(
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
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
                                onThemeModeChange = { mode -> viewModel.edit { it.appearance.setThemeMode(mode) } },
                                onDynamicThemingChange = { enabled -> viewModel.edit { it.appearance.setDynamicTheming(enabled) } },
                                onOledModeChange = { enabled -> viewModel.edit { it.appearance.setOledMode(enabled) } },
                                onContrastLevelChange = { level -> viewModel.edit { it.appearance.setContrastLevel(level) } },
                                onAccentColorSwatchChange = { swatch -> viewModel.edit { it.appearance.setAccentColorSwatch(swatch) } },
                                onColorStyleChange = { style -> viewModel.edit { it.appearance.setColorStyle(style) } },
                                onHomeHeroEnabledChange = { enabled -> viewModel.edit { it.homeDiscovery.setHomeHeroEnabled(enabled) } },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.PERFORMANCE -> {
                            PerformanceStep(
                                performanceMode = preferences.performanceMode,
                                onPerformanceModeChange = { enabled -> viewModel.edit { it.appearance.setPerformanceMode(enabled) } },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.HOME_LAYOUT -> {
                            HomeLayoutStep(
                                homeMode = preferences.homeMode,
                                navBarShowLabels = preferences.navBarShowLabels,
                                enabledHomeSectionTypes = preferences.enabledHomeSectionTypes,
                                onHomeModeChange = { mode -> viewModel.edit { it.homeDiscovery.setHomeMode(mode) } },
                                onNavBarShowLabelsChange = { show -> viewModel.edit { it.navigation.setNavBarShowLabels(show) } },
                                onEnabledHomeSectionTypesChange = { types -> viewModel.edit { it.homeDiscovery.setEnabledHomeSectionTypes(types) } },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.VIDEO_PLAYER -> {
                            // One binary switch can't express GestureMode's three
                            // states: re-enabling must restore a TAP_ONLY user's
                            // choice, not silently upgrade them to ALL.
                            var lastEnabledGestureMode by remember {
                                mutableStateOf(preferences.videoGestureMode.takeIf { it != GestureMode.NONE })
                            }
                            VideoPlayerStep(
                                preferredPlayer = preferences.preferredPlayer,
                                streamingQuality = preferences.streamingQuality,
                                seekDurationMs = preferences.videoSeekDurationMs,
                                gesturesEnabled = preferences.videoGestureMode.tapsEnabled,
                                defaultOrientation = preferences.videoDefaultOrientation,
                                autoplayNext = preferences.videoAutoplayNext,
                                onPreferredPlayerChange = { playerType -> viewModel.edit { it.playback.setPreferredPlayer(playerType) } },
                                onStreamingQualityChange = { quality -> viewModel.edit { it.playback.setStreamingQuality(quality) } },
                                onSeekDurationChange = { ms -> viewModel.edit { it.videoPlayer.setVideoSeekDurationMs(ms) } },
                                onGesturesEnabledChange = { enabled ->
                                    if (preferences.videoGestureMode != GestureMode.NONE) {
                                        lastEnabledGestureMode = preferences.videoGestureMode
                                    }
                                    viewModel.edit {
                                        it.videoPlayer.setVideoGestureMode(
                                            if (enabled) lastEnabledGestureMode ?: GestureMode.ALL else GestureMode.NONE,
                                        )
                                    }
                                },
                                onDefaultOrientationChange = { mode -> viewModel.edit { it.videoPlayer.setVideoDefaultOrientation(mode) } },
                                onAutoplayNextChange = { enabled -> viewModel.edit { it.videoPlayer.setVideoAutoplayNext(enabled) } },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.AUDIO_PLAYER -> {
                            AudioPlayerStep(
                                defaultSpeed = preferences.audioDefaultSpeed,
                                gaplessEnabled = preferences.audioGaplessEnabled,
                                crossfadeDurationMs = preferences.audioCrossfadeDurationMs,
                                normalizationEnabled = preferences.audioNormalizationEnabled,
                                autoplayNext = preferences.audioAutoplayNext,
                                onDefaultSpeedChange = { speed -> viewModel.edit { it.audio.setAudioDefaultSpeed(speed) } },
                                onGaplessEnabledChange = { enabled -> viewModel.edit { it.audio.setAudioGaplessEnabled(enabled) } },
                                onCrossfadeDurationChange = { ms -> viewModel.edit { it.audio.setAudioCrossfadeDurationMs(ms) } },
                                onNormalizationEnabledChange = { enabled -> viewModel.edit { it.audio.setAudioNormalizationEnabled(enabled) } },
                                onAutoplayNextChange = { enabled -> viewModel.edit { it.audio.setAudioAutoplayNext(enabled) } },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.SUBTITLES -> {
                            SubtitlesStep(
                                subtitleStyle = preferences.subtitleStyle,
                                onSubtitleStyleChange = { style -> viewModel.edit { it.subtitle.setSubtitleStyle(style) } },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.SECURITY -> {
                            val biometricAvailable = rememberBiometricAvailable()
                            SecurityStep(
                                pinLockEnabled = preferences.pinLockEnabled,
                                biometricLockEnabled = preferences.biometricLockEnabled,
                                autoLockTimerMs = preferences.autoLockTimerMs,
                                onPinLockEnabledChange = { enabled -> viewModel.edit { it.security.setPinLockEnabled(enabled) } },
                                onPinHashSet = { hash -> viewModel.edit { it.security.setPinHash(hash) } },
                                biometricAvailable = biometricAvailable,
                                onBiometricLockEnabledChange = { enabled -> viewModel.edit { it.security.setBiometricLockEnabled(enabled) } },
                                onAutoLockTimerMsChange = { ms -> viewModel.edit { it.security.setAutoLockTimerMs(ms) } },
                                hashPin = viewModel::hashPin,
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.SEERR -> {
                            val seerrPrefs by viewModel.seerrPreferences.collectAsStateWithLifecycle()
                            SeerrStep(
                                seerrPreferences = seerrPrefs,
                                actions = viewModel.seerrEditActions,
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }

                        OnboardingStep.COMPLETION -> {
                            CompletionStep(
                                onStartWatching = {
                                    scope.launch {
                                        // Land a pending debounced Seerr field
                                        // write before the wizard completes, so
                                        // the last keystroke isn't lost.
                                        viewModel.seerrEditActions.flushPendingFieldWrites()
                                        viewModel.completeOnboarding()
                                        onComplete()
                                    }
                                },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = bottomBarVisible && pagerState.currentPage < lastPage,
                enter = slideInVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    initialOffsetY = { it },
                ) + fadeIn(),
                exit = slideOutVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    targetOffsetY = { it },
                ) + fadeOut(),
            ) {
                OnboardingBottomBar(
                    onNext = { navigateToPage(pagerState.currentPage + 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Sticky primary action: one full-width Next button, matching the completion
 * step's Start Watching button. Back and skip live in [OnboardingProgressHeader]
 * so the bottom bar never splits attention between competing controls.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingBottomBar(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val nextFocusRequester = remember { FocusRequester() }
    val nextFocusState = rememberTvFocusState(focusedScale = 1.04f)

    LaunchedEffect(Unit) {
        if (isTv) nextFocusRequester.tryRequestFocus("onboarding_next")
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                // max(nav bar, IME) so the button rises above the keyboard too
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(56.dp)
                .focusRequester(nextFocusRequester)
                .then(nextFocusState.focusModifier)
                .tvFocusIndicator(nextFocusState, ShapeCache.smooth16),
            shape = ShapeCache.smooth16,
        ) {
            Text(
                text = stringResource(Res.string.onboarding_next),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Tabler.Outline.ArrowRight,
                contentDescription = null,
            )
        }
    }
}
