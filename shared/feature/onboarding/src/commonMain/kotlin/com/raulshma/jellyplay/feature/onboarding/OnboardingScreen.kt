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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.raulshma.jellyplay.core.ui.components.biometricPromptAvailable
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    // The ViewModel owns page truth — the pager below only mirrors it and
    // reports drag-settled turns back. The wizard chrome (header position,
    // bottom bar, D-pad bounds) reads this one page so every control agrees.
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { OnboardingStep.count })
    val lastPage = OnboardingStep.count - 1
    var bottomBarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { bottomBarVisible = true }

    // Pager <-> ViewModel two-way sync. Each direction acts only on a
    // difference, so the pair cannot feed each other a loop: a ViewModel
    // turn animates the pager, whose settle is then a same-page ack (no-op);
    // a user drag settles the pager somewhere the ViewModel doesn't know
    // yet — the only case the ack acts on.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> viewModel.onPageSettled(page) }
    }
    LaunchedEffect(pagerState) {
        viewModel.currentPage.collect { page ->
            if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
        }
    }

    // One-shot wizard exits pop the nav route. Both variants leave the
    // screen; Complete additionally persisted the completion flag on the
    // ViewModel side before this fires (the gate never re-fires for a
    // completer), Abandon closed without it.
    LaunchedEffect(viewModel) {
        viewModel.exits.collect { exit ->
            when (exit) {
                WizardExit.Complete -> onComplete()
                WizardExit.Abandon -> onComplete()
            }
        }
    }

    // Wizard Back: one page up, or — on the first page — leave the wizard
    // without completing. Always enabled so remote/system BACK is never a
    // bare nav pop: the ViewModel flushes a pending debounced Seerr write
    // before either arm (WELCOME has no fields today, so the flush is a
    // no-op there — the invariant is load-bearing the day fields land on
    // the first page).
    JellyPlayBackHandler(enabled = true) {
        viewModel.onBack()
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
                currentPage = currentPage,
                pageCount = OnboardingStep.count,
                onBack = { viewModel.onBack() },
                onSkip = {
                    // Skip exits onboarding from any page — flush, the
                    // completion write, and the exit signal all live in
                    // exitWizard; the wizard can be re-run from Settings →
                    // Setup wizard.
                    viewModel.exitWizard(markComplete = true)
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
                                if (currentPage < lastPage) {
                                    viewModel.navigateToPage(currentPage + 1)
                                    true
                                } else false
                            },
                            onLeft = {
                                if (currentPage > 0) {
                                    viewModel.onBack()
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
                            val biometricAvailable = biometricPromptAvailable()
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
                                onStartWatching = { viewModel.exitWizard(markComplete = true) },
                                modifier = Modifier.imePadding().verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = bottomBarVisible && currentPage < lastPage,
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
                    onNext = { viewModel.navigateToPage(currentPage + 1) },
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
