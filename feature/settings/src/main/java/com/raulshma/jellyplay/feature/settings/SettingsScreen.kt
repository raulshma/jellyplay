package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.components.AuthChallengeScreen
import androidx.fragment.app.FragmentActivity
import com.raulshma.jellyplay.core.ui.components.BiometricAuthHelper
import com.raulshma.jellyplay.core.ui.components.rememberBiometricAvailability

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onServerManagement: () -> Unit = {},
    onUserManagement: () -> Unit = {},
    onSeerrSettings: () -> Unit = {},
    onAdminDashboard: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val userName = viewModel.currentUserName
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

    val listFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv) {
        if (isTv) {
            kotlinx.coroutines.delay(150)
            try { listFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var showPlayerPicker by remember { mutableStateOf(false) }
    var showAudioLanguagePicker by remember { mutableStateOf(false) }
    var showSubtitleLanguagePicker by remember { mutableStateOf(false) }
    var showOrientationPicker by remember { mutableStateOf(false) }
    var showAspectRatioPicker by remember { mutableStateOf(false) }
    var showVideoSpeedPicker by remember { mutableStateOf(false) }
    var showAudioSpeedPicker by remember { mutableStateOf(false) }
    var showVideoSeekDurationPicker by remember { mutableStateOf(false) }
    var showControlsTimeoutPicker by remember { mutableStateOf(false) }
    var showSwipeSeekPicker by remember { mutableStateOf(false) }
    var showPreloadBufferPicker by remember { mutableStateOf(false) }
    var showAudioPreloadBufferPicker by remember { mutableStateOf(false) }
    var showNightModeVolumePicker by remember { mutableStateOf(false) }
    var showNightModeGainPicker by remember { mutableStateOf(false) }
    var showSkipPrevThresholdPicker by remember { mutableStateOf(false) }
    var showCrossfadePicker by remember { mutableStateOf(false) }
    var showAudioDelayPicker by remember { mutableStateOf(false) }

    var showSubtitleFontPicker by remember { mutableStateOf(false) }
    var showSubtitleColorPicker by remember { mutableStateOf(false) }
    var showSubtitleBgColorPicker by remember { mutableStateOf(false) }
    var showSubtitleEdgePicker by remember { mutableStateOf(false) }
    var showSubtitleOffsetPicker by remember { mutableStateOf(false) }
    var showSubtitlePositionPicker by remember { mutableStateOf(false) }
    var showEqualizerEditor by remember { mutableStateOf(false) }
    var showNormalizationModePicker by remember { mutableStateOf(false) }
    var showPreAmpPicker by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    var showPinDisableAuth by remember { mutableStateOf(false) }
    var pinDisableAuthError by remember { mutableStateOf<String?>(null) }
    var showBiometricDisableAuth by remember { mutableStateOf(false) }

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = "Settings",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .then(if (isTv) Modifier
                    .tvFocusRestorer()
                    .focusRequester(listFocusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                            onBack()
                            true
                        } else false
                    }
                else Modifier)
                .padding(
                    start = adaptiveInfo.contentPadding(LocalTvMode.current),
                    end = adaptiveInfo.contentPadding(LocalTvMode.current),
                    bottom = adaptiveInfo.bottomPadding(LocalTvMode.current),
                ),
        ) {
            AnimatedSettingsEntrance(0) {
                if (userName.isNotBlank()) {
                    SettingsProfileBanner(
                        userName = userName,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            AnimatedSettingsEntrance(1) {
                SettingsGroup(
                    icon = Tabler.Outline.User,
                    title = "Account",
                    summary = { "Signed in as $userName" },
                    initiallyExpanded = true,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.Server,
                        title = "Servers",
                        subtitle = "Manage your Jellyfin servers",
                        index = 0, count = 3,
                        onClick = onServerManagement,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.User,
                        title = "Switch User",
                        subtitle = userName.ifBlank { "Manage users" },
                        index = 1, count = 3,
                        onClick = onUserManagement,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Logout,
                        title = "Sign Out",
                        subtitle = "Log out of current account",
                        index = 2, count = 3,
                        isDestructive = true,
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        },
                    )
                }
            }

            AnimatedSettingsEntrance(2) {
                if (viewModel.currentUser?.isAdmin == true) {
                    SettingsGroup(
                        icon = Tabler.Outline.Server,
                        title = "Administration",
                        summary = { "Server management dashboard" },
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        SettingListItem(
                            icon = Tabler.Outline.Server,
                            title = "Dashboard",
                            subtitle = "Server info, tasks, devices, and logs",
                            index = 0, count = 1,
                            onClick = onAdminDashboard,
                        )
                    }
                }
            }

            AnimatedSettingsEntrance(3) {
                SettingsGroup(
                    icon = Tabler.Outline.Puzzle,
                    title = "Integrations",
                    summary = { "Seerr" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.Puzzle,
                        title = "Seerr",
                        subtitle = "Media request and discovery manager",
                        index = 0, count = 1,
                        onClick = onSeerrSettings,
                    )
                }
            }

            AnimatedSettingsEntrance(3) {
                SettingsGroup(
                    icon = Tabler.Outline.Home,
                    title = "Home Screen",
                    summary = {
                        val enabled = preferences.enabledHomeSectionTypes
                        "${enabled.size} of ${HomeSectionType.CONFIGURABLE.size} sections visible"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val sectionItems = buildList {
                        add(HomeSectionType.CONTINUE_WATCHING)
                        add(HomeSectionType.NEXT_UP)
                        if (HomeSectionType.LATEST_MEDIA in preferences.enabledHomeSectionTypes) {
                            add(HomeSectionType.RECENTLY_ADDED)
                        }
                        add(HomeSectionType.LATEST_MEDIA)
                    }
                    val totalCount = sectionItems.size + viewModel.libraryFolders.size
                    var idx = 0

                    sectionItems.forEach { type ->
                        if (type == HomeSectionType.LATEST_MEDIA) {
                            SettingToggleItem(
                                icon = Tabler.Outline.LayersLinked,
                                title = type.displayName,
                                subtitle = if (type in preferences.enabledHomeSectionTypes) type.description else "Hidden",
                                checked = type in preferences.enabledHomeSectionTypes,
                                index = idx++, count = totalCount,
                                onCheckedChange = { viewModel.toggleHomeSectionType(type, it) },
                            )
                            viewModel.libraryFolders.forEach { folder ->
                                val isLibraryVisible = folder.id !in preferences.hiddenLibrarySectionIds
                                SettingToggleItem(
                                    icon = Tabler.Outline.Folder,
                                    title = "Latest ${folder.name}",
                                    subtitle = if (isLibraryVisible) "Show latest items" else "Hidden",
                                    checked = isLibraryVisible,
                                    index = idx++, count = totalCount,
                                    onCheckedChange = { viewModel.toggleLibrarySection(folder.id, it) },
                                )
                            }
                        } else {
                            SettingToggleItem(
                                icon = when (type) {
                                    HomeSectionType.CONTINUE_WATCHING -> Tabler.Outline.PlayerPlay
                                    HomeSectionType.NEXT_UP -> Tabler.Outline.PlayerSkipForward
                                    HomeSectionType.RECENTLY_ADDED -> Tabler.Outline.Clock
                                    else -> Tabler.Outline.LayersLinked
                                },
                                title = type.displayName,
                                subtitle = if (type in preferences.enabledHomeSectionTypes) type.description else "Hidden",
                                checked = type in preferences.enabledHomeSectionTypes,
                                index = idx++, count = totalCount,
                                onCheckedChange = { viewModel.toggleHomeSectionType(type, it) },
                            )
                        }
                    }
                }
            }

            AnimatedSettingsEntrance(3) {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerPlay,
                    title = "Video Player",
                    summary = { "Player Engine: ${preferences.preferredPlayer.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val videoItems = buildList {
                        add("player" to 0)
                        add("seek" to 1)
                        add("orientation" to 2)
                        add("timeout" to 3)
                        add("gestures" to 4)
                        add("speed" to 5)
                        add("aspect" to 6)
                        add("autoplay" to 7)
                        add("browser" to 8)
                        add("swipe" to 9)
                        add("brightness" to 10)
                        add("trickplay" to 11)
                        add("trickplayGesture" to 12)
                        add("preload" to 13)
                    }
                    val total = videoItems.size

                    SettingListItem(
                        icon = Tabler.Outline.PlayerPlay,
                        title = "Player Engine",
                        subtitle = "Choose media playback engine",
                        trailingText = preferences.preferredPlayer.displayName,
                        index = 0, count = total,
                        onClick = { showPlayerPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.PlayerTrackNext,
                        title = "Seek Duration",
                        subtitle = "Double-tap to seek",
                        trailingText = "${preferences.videoSeekDurationMs / 1000}s",
                        index = 1, count = total,
                        onClick = { showVideoSeekDurationPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.DeviceMobileRotated,
                        title = "Orientation",
                        subtitle = "Default screen orientation",
                        trailingText = preferences.videoDefaultOrientation.displayName,
                        index = 2, count = total,
                        onClick = { showOrientationPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Stopwatch,
                        title = "Controls Timeout",
                        subtitle = "Auto-hide player controls",
                        trailingText = "${preferences.videoControlsTimeoutMs / 1000}s",
                        index = 3, count = total,
                        onClick = { showControlsTimeoutPicker = true },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.HandMove,
                        title = "Gestures",
                        subtitle = if (preferences.videoGesturesEnabled) "Swipe & tap controls active" else "Touch gestures disabled",
                        checked = preferences.videoGesturesEnabled,
                        index = 4, count = total,
                        onCheckedChange = { viewModel.setVideoGesturesEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Default Speed",
                        subtitle = if (preferences.videoDefaultSpeed == 1.0f) "Normal playback speed" else "${preferences.videoDefaultSpeed}x playback",
                        trailingText = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                        index = 5, count = total,
                        onClick = { showVideoSpeedPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.AspectRatio,
                        title = "Default Aspect",
                        subtitle = "Video aspect ratio mode",
                        trailingText = preferences.videoDefaultAspectRatio,
                        index = 6, count = total,
                        onClick = { showAspectRatioPicker = true },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = "Auto-play Next",
                        subtitle = if (preferences.videoAutoplayNext) "Automatically plays next episode" else "Manual episode selection",
                        checked = preferences.videoAutoplayNext,
                        index = 7, count = total,
                        onCheckedChange = { viewModel.setVideoAutoplayNext(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Video,
                        title = "Episode Browser",
                        subtitle = if (preferences.videoEpisodeBrowserEnabled) "Browse episodes during playback" else "Episode picker disabled",
                        checked = preferences.videoEpisodeBrowserEnabled,
                        index = 8, count = total,
                        onCheckedChange = { viewModel.setVideoEpisodeBrowserEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.HandFinger,
                        title = "Swipe Seek Range",
                        subtitle = "Maximum seek distance",
                        trailingText = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                        index = 9, count = total,
                        onClick = { showSwipeSeekPicker = true },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.BrightnessHalf,
                        title = "Remember Brightness",
                        subtitle = if (preferences.videoRememberBrightness) "Brightness saved between sessions" else "Reset brightness each session",
                        checked = preferences.videoRememberBrightness,
                        index = 10, count = total,
                        onCheckedChange = { viewModel.setVideoRememberBrightness(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Video,
                        title = "Trickplay Preview",
                        subtitle = if (preferences.trickplayEnabled) "Show preview images while scrubbing" else "No preview images on seek bar",
                        checked = preferences.trickplayEnabled,
                        index = 11, count = total,
                        onCheckedChange = { viewModel.setTrickplayEnabled(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.HandClick,
                        title = "Trickplay on Gestures",
                        subtitle = if (preferences.trickplayOnSeekGesture) "Show preview on swipe seek" else "No preview on swipe gestures",
                        checked = preferences.trickplayOnSeekGesture,
                        index = 12, count = total,
                        onCheckedChange = { viewModel.setTrickplayOnSeekGesture(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Refresh,
                        title = "Preload Buffer",
                        subtitle = "Amount to buffer ahead during playback",
                        trailingText = preferences.videoPreloadBufferSize.displayName,
                        index = 13, count = total,
                        onClick = { showPreloadBufferPicker = true },
                    )
                }
            }

            AnimatedSettingsEntrance(4) {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerTrackNext,
                    title = "Media Segments",
                    summary = {
                        val autoCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.AUTO_SKIP }
                        val buttonCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON }
                        "$autoCount auto-skip, $buttonCount show button"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val segmentTypes = com.raulshma.jellyplay.core.model.MediaSegmentType.entries
                    val totalTypes = segmentTypes.size
                    segmentTypes.forEachIndexed { index, type ->
                        val behavior = preferences.segmentBehaviors[type]
                            ?: com.raulshma.jellyplay.core.model.SegmentBehavior.IGNORE
                        SettingListItem(
                            icon = Tabler.Outline.PlayerTrackNext,
                            title = type.displayName,
                            subtitle = type.description,
                            trailingText = behavior.displayName,
                            index = index, count = totalTypes,
                            onClick = {
                                val behaviors = com.raulshma.jellyplay.core.model.SegmentBehavior.entries
                                val currentIndex = behaviors.indexOf(behavior)
                                val nextIndex = (currentIndex + 1) % behaviors.size
                                viewModel.setSegmentBehavior(type, behaviors[nextIndex])
                            },
                        )
                    }
                }
            }

            AnimatedSettingsEntrance(5) {
                SettingsGroup(
                    icon = Tabler.Outline.BadgeHd,
                    title = "Advanced Video",
                    summary = { "Decoder: ${preferences.decoderMode.displayName}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val advancedItems = mutableListOf<Pair<String, Int>>()
                    advancedItems.add("dialogue" to 0)
                    if (preferences.dialogueBoostEnabled) {
                        advancedItems.add("dialogueStrength" to advancedItems.size)
                    }
                    advancedItems.add("decoder" to advancedItems.size)
                    advancedItems.add("passthrough" to advancedItems.size)
                    advancedItems.add("framerate" to advancedItems.size)
                    advancedItems.add("quality" to advancedItems.size)
                    advancedItems.add("delay" to advancedItems.size)
                    val total = advancedItems.size

                    var idx = 0
                    SettingToggleItem(
                        icon = Tabler.Outline.Microphone2,
                        title = "Dialogue Boost",
                        subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else "Off",
                        checked = preferences.dialogueBoostEnabled,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                    )
                    if (preferences.dialogueBoostEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = "Dialogue Boost Strength",
                            subtitle = preferences.dialogueBoostStrength.displayName,
                            trailingText = preferences.dialogueBoostStrength.displayName,
                            index = idx++, count = total,
                            onClick = {
                                val strengths = EffectStrength.entries
                                val currentIndex = strengths.indexOf(preferences.dialogueBoostStrength)
                                val nextIndex = (currentIndex + 1) % strengths.size
                                viewModel.setDialogueBoostStrength(strengths[nextIndex])
                            },
                        )
                    }
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = "Decoder",
                        subtitle = preferences.decoderMode.displayName,
                        trailingText = preferences.decoderMode.displayName.split(" ").first(),
                        index = idx++, count = total,
                        onClick = {
                            val modes = DecoderMode.entries
                            val currentIndex = modes.indexOf(preferences.decoderMode)
                            val nextIndex = (currentIndex + 1) % modes.size
                            viewModel.setDecoderMode(modes[nextIndex])
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Movie,
                        title = "Audio Passthrough",
                        subtitle = if (preferences.audioPassthrough) "Direct audio to receiver" else "Software audio processing",
                        checked = preferences.audioPassthrough,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioPassthrough(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Maximize,
                        title = "Frame Rate Match",
                        subtitle = if (preferences.frameRateMatching) "Display refresh matches content" else "Fixed display refresh rate",
                        checked = preferences.frameRateMatching,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setFrameRateMatching(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.BadgeHd,
                        title = "Streaming Quality",
                        subtitle = streamingQualityLabel(preferences.streamingQuality),
                        trailingText = streamingQualityShort(preferences.streamingQuality),
                        index = idx++, count = total,
                        onClick = {
                            val qualities = StreamingQuality.entries
                            val currentIndex = qualities.indexOf(preferences.streamingQuality)
                            val nextIndex = (currentIndex + 1) % qualities.size
                            viewModel.setStreamingQuality(qualities[nextIndex])
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Music,
                        title = "Audio Delay",
                        subtitle = if (preferences.audioDelayMs == 0L) "No audio delay" else "${preferences.audioDelayMs}ms delay",
                        trailingText = if (preferences.audioDelayMs == 0L) "Off" else "${preferences.audioDelayMs}ms",
                        index = idx, count = total,
                        onClick = { showAudioDelayPicker = true },
                    )
                }
            }

            AnimatedSettingsEntrance(6) {
                SettingsGroup(
                    icon = Tabler.Outline.Users,
                    title = "SyncPlay",
                    summary = { "Watch together with friends" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                }
            }

            AnimatedSettingsEntrance(7) {
                SettingsGroup(
                    icon = Tabler.Outline.Music,
                    title = "Audio Player",
                    summary = { "Default speed: ${if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val audioItems = mutableListOf<Int>()
                    for (i in 0..20) audioItems.add(i)
                    var idx = 0
                    val total = run {
                        var c = 9
                        if (preferences.equalizerEnabled) c++
                        if (preferences.dialogueBoostEnabled) c++
                        if (preferences.nightModeEnabled) c++
                        if (preferences.audioNormalizationMode != AudioNormalizationMode.NONE) c++
                        c
                    }

                    SettingListItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Default Speed",
                        subtitle = if (preferences.audioDefaultSpeed == 1.0f) "Normal playback speed" else "${preferences.audioDefaultSpeed}x playback",
                        trailingText = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                        index = idx++, count = total,
                        onClick = { showAudioSpeedPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Music,
                        title = "Night Mode Volume",
                        subtitle = "Maximum volume level at night",
                        trailingText = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                        index = idx++, count = total,
                        onClick = { showNightModeVolumePicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Adjustments,
                        title = "Night Mode Gain",
                        subtitle = "Loudness compensation",
                        trailingText = "${preferences.audioNightModeGain}",
                        index = idx++, count = total,
                        onClick = { showNightModeGainPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.PlayerSkipForward,
                        title = "Skip Prev Threshold",
                        subtitle = "Restart song if past this point",
                        trailingText = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                        index = idx++, count = total,
                        onClick = { showSkipPrevThresholdPicker = true },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = "Auto-play Next",
                        subtitle = if (preferences.audioAutoplayNext) "Automatically plays next track" else "Manual track selection",
                        checked = preferences.audioAutoplayNext,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAudioAutoplayNext(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.PlaylistAdd,
                        title = "Gapless Playback",
                        subtitle = if (preferences.audioGaplessEnabled) "Seamless track transitions" else "Brief pause between tracks",
                        checked = preferences.audioGaplessEnabled,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setGaplessEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Music,
                        title = "Crossfade Duration",
                        subtitle = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s overlap between tracks" else "No crossfade",
                        trailingText = if (preferences.audioCrossfadeDurationMs > 0) "${preferences.audioCrossfadeDurationMs / 1000}s" else "Off",
                        index = idx++, count = total,
                        onClick = { showCrossfadePicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Refresh,
                        title = "Preload Buffer",
                        subtitle = "Amount to buffer ahead during audio playback",
                        trailingText = preferences.audioPreloadBufferSize.displayName,
                        index = idx++, count = total,
                        onClick = { showAudioPreloadBufferPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Adjustments,
                        title = "Volume Normalization",
                        subtitle = when (preferences.audioNormalizationMode) {
                            AudioNormalizationMode.NONE -> "Off"
                            AudioNormalizationMode.DYNAMIC -> "Dynamic compression"
                            AudioNormalizationMode.TRACK -> "Per-track ReplayGain"
                            AudioNormalizationMode.ALBUM -> "Album-aware ReplayGain"
                        },
                        trailingText = when (preferences.audioNormalizationMode) {
                            AudioNormalizationMode.NONE -> "Off"
                            AudioNormalizationMode.DYNAMIC -> "Dynamic"
                            AudioNormalizationMode.TRACK -> "Track"
                            AudioNormalizationMode.ALBUM -> "Album"
                        },
                        index = idx++, count = total,
                        onClick = { showNormalizationModePicker = true },
                    )
                    if (preferences.audioNormalizationMode == AudioNormalizationMode.TRACK ||
                        preferences.audioNormalizationMode == AudioNormalizationMode.ALBUM
                    ) {
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = "ReplayGain Pre-Amp",
                            subtitle = "Fine-tune target loudness",
                            trailingText = "${if (preferences.replayGainPreAmpDb >= 0) "+" else ""}${String.format("%.1f", preferences.replayGainPreAmpDb)} dB",
                            index = idx++, count = total,
                            onClick = { showPreAmpPicker = true },
                        )
                    }
                    SettingToggleItem(
                        icon = Tabler.Outline.Adjustments,
                        title = "Equalizer",
                        subtitle = if (preferences.equalizerEnabled) "10-band equalizer active" else "Equalizer disabled",
                        checked = preferences.equalizerEnabled,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        onClick = { showEqualizerEditor = true },
                    )
                    if (preferences.equalizerEnabled) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Microphone2,
                            title = "Dialogue Boost",
                            subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else "Off",
                            checked = preferences.dialogueBoostEnabled,
                            index = idx++, count = total,
                            onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                        )
                    }
                    if (preferences.dialogueBoostEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = "Dialogue Boost Strength",
                            subtitle = preferences.dialogueBoostStrength.displayName,
                            trailingText = preferences.dialogueBoostStrength.displayName,
                            index = idx++, count = total,
                            onClick = {
                                val strengths = EffectStrength.entries
                                val currentIndex = strengths.indexOf(preferences.dialogueBoostStrength)
                                val nextIndex = (currentIndex + 1) % strengths.size
                                viewModel.setDialogueBoostStrength(strengths[nextIndex])
                            },
                        )
                    }
                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Night Mode",
                        subtitle = if (preferences.nightModeEnabled) preferences.nightModeStrength.displayName else "Off",
                        checked = preferences.nightModeEnabled,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setNightModeEnabled(it) },
                    )
                    if (preferences.nightModeEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = "Night Mode Strength",
                            subtitle = preferences.nightModeStrength.displayName,
                            trailingText = preferences.nightModeStrength.displayName,
                            index = idx++, count = total,
                            onClick = {
                                val strengths = EffectStrength.entries
                                val currentIndex = strengths.indexOf(preferences.nightModeStrength)
                                val nextIndex = (currentIndex + 1) % strengths.size
                                viewModel.setNightModeStrength(strengths[nextIndex])
                            },
                        )
                    }
                    SettingToggleItem(
                        icon = Tabler.Outline.WaveSine,
                        title = "Bass Boost",
                        subtitle = if (preferences.bassBoostEnabled) preferences.bassBoostStrength.displayName else "Off",
                        checked = preferences.bassBoostEnabled,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setBassBoostEnabled(it) },
                    )
                    if (preferences.bassBoostEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.WaveSine,
                            title = "Bass Boost Strength",
                            subtitle = preferences.bassBoostStrength.displayName,
                            trailingText = preferences.bassBoostStrength.displayName,
                            index = idx++, count = total,
                            onClick = {
                                val strengths = EffectStrength.entries
                                val currentIndex = strengths.indexOf(preferences.bassBoostStrength)
                                val nextIndex = (currentIndex + 1) % strengths.size
                                viewModel.setBassBoostStrength(strengths[nextIndex])
                            },
                        )
                    }
                    SettingToggleItem(
                        icon = Tabler.Outline.Speakerphone,
                        title = "Virtualizer / Spatial Audio",
                        subtitle = if (preferences.virtualizerEnabled) "${preferences.virtualizerStrength / 10}% strength" else "Off",
                        checked = preferences.virtualizerEnabled,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setVirtualizerEnabled(it) },
                    )
                    if (preferences.virtualizerEnabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Speakerphone,
                            title = "Virtualizer Strength",
                            subtitle = "${preferences.virtualizerStrength / 10}%",
                            trailingText = "${preferences.virtualizerStrength / 10}%",
                            index = idx++, count = total,
                            onClick = {
                                val steps = listOf(0, 200, 400, 500, 600, 800, 1000)
                                val currentIdx = steps.indexOf(preferences.virtualizerStrength).coerceAtLeast(0)
                                val nextIdx = (currentIdx + 1) % steps.size
                                viewModel.setVirtualizerStrength(steps[nextIdx])
                            },
                        )
                    }
                    SettingListItem(
                        icon = Tabler.Outline.WaveSine,
                        title = "Reverb",
                        subtitle = preferences.reverbPreset.displayName,
                        trailingText = preferences.reverbPreset.displayName,
                        index = idx++, count = total,
                        onClick = {
                            val presets = com.raulshma.jellyplay.core.model.ReverbPreset.entries
                            val currentIndex = presets.indexOf(preferences.reverbPreset)
                            val nextIndex = (currentIndex + 1) % presets.size
                            viewModel.setReverbPreset(presets[nextIndex])
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Wand,
                        title = "Auto-EQ by Genre",
                        subtitle = if (preferences.autoEqByGenre) "Automatically applies EQ preset based on genre" else "Off",
                        checked = preferences.autoEqByGenre,
                        index = idx++, count = total,
                        onCheckedChange = { viewModel.setAutoEqByGenre(it) },
                    )
                }
            }

            AnimatedSettingsEntrance(8) {
                SettingsGroup(
                    icon = Tabler.Outline.Language,
                    title = "Language",
                    summary = { "Audio: ${preferences.preferredAudioLanguage ?: "Default"}" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.Language,
                        title = "Audio Language",
                        subtitle = "Preferred audio track language",
                        trailingText = preferences.preferredAudioLanguage ?: "Default",
                        index = 0, count = 2,
                        onClick = { showAudioLanguagePicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Subtitles,
                        title = "Subtitle Language",
                        subtitle = "Preferred subtitle language",
                        trailingText = preferences.preferredSubtitleLanguage ?: "Default",
                        index = 1, count = 2,
                        onClick = { showSubtitleLanguagePicker = true },
                    )
                }
            }

            AnimatedSettingsEntrance(9) {
                SettingsGroup(
                    icon = Tabler.Outline.Subtitles,
                    title = "Subtitles",
                    summary = { "Font size: ${preferences.subtitleStyle.fontSize}sp" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val subTotal = 6
                    SettingListItem(
                        icon = Tabler.Outline.Typography,
                        title = "Font Size",
                        subtitle = "Subtitle text size",
                        trailingText = "${preferences.subtitleStyle.fontSize}sp",
                        index = 0, count = subTotal,
                        onClick = { showSubtitleFontPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Palette,
                        title = "Text Color",
                        subtitle = "Subtitle font color",
                        trailingText = preferences.subtitleStyle.fontColor.name,
                        index = 1, count = subTotal,
                        onClick = { showSubtitleColorPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Subtitles,
                        title = "Background",
                        subtitle = "${preferences.subtitleStyle.backgroundColor.name} \u2022 ${(preferences.subtitleStyle.backgroundOpacity * 100).toInt()}%",
                        index = 2, count = subTotal,
                        onClick = { showSubtitleBgColorPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Typography,
                        title = "Edge Style",
                        subtitle = "Text outline effect",
                        trailingText = preferences.subtitleStyle.edgeType.name,
                        index = 3, count = subTotal,
                        onClick = { showSubtitleEdgePicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Stopwatch,
                        title = "Sync Offset",
                        subtitle = "Subtitle timing adjustment",
                        trailingText = "${preferences.subtitleStyle.offsetMs}ms",
                        index = 4, count = subTotal,
                        onClick = { showSubtitleOffsetPicker = true },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Subtitles,
                        title = "Position",
                        subtitle = "Vertical placement on screen",
                        trailingText = "${(preferences.subtitleStyle.verticalPosition * 100).toInt()}%",
                        index = 5, count = subTotal,
                        onClick = { showSubtitlePositionPicker = true },
                    )
                }
            }

            AnimatedSettingsEntrance(10) {
                SettingsGroup(
                    icon = Tabler.Outline.Database,
                    title = "Storage",
                    summary = { "Cache: ${viewModel.cacheSizeMb} MB" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val storageTotal = 4
                    SettingInfoItem(
                        icon = Tabler.Outline.Database,
                        title = "Cache Used",
                        subtitle = "${viewModel.cacheSizeMb} MB",
                        index = 0, count = storageTotal,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Trash,
                        title = "Clear Cache",
                        subtitle = "Free up storage space",
                        index = 1, count = storageTotal,
                        onClick = { viewModel.clearCache() },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Refresh,
                        title = "Auto-delete Cache",
                        subtitle = if (preferences.autoDeleteCache) "Automatically clears on low storage" else "Manual cache management",
                        checked = preferences.autoDeleteCache,
                        index = 2, count = storageTotal,
                        onCheckedChange = { viewModel.setAutoDeleteCache(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Database,
                        title = "Max Cache Size",
                        subtitle = "Maximum disk space for caching",
                        trailingText = if (preferences.maxCacheSizeMb == 0) "Unlimited" else "${preferences.maxCacheSizeMb} MB",
                        index = 3, count = storageTotal,
                        onClick = {
                            val sizes = listOf(0, 250, 500, 1000, 2000, 5000)
                            val currentIndex = sizes.indexOf(preferences.maxCacheSizeMb)
                            val nextIndex = (currentIndex + 1) % sizes.size
                            viewModel.setMaxCacheSize(sizes[nextIndex])
                        },
                    )
                }
            }

            AnimatedSettingsEntrance(11) {
                SettingsGroup(
                    icon = Tabler.Outline.Palette,
                    title = "Appearance",
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.dynamicTheming) parts.add("Dynamic theming")
                        parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
                        if (preferences.oledMode) parts.add("OLED")
                        if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add("${preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() }} contrast")
                        parts.joinToString(", ")
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val isAndroid12 = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    val isDarkActive = when (preferences.themeMode) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    }

                    if (isAndroid12) {
                        val baseCount = if (isDarkActive) 5 else 4
                        SettingListItem(
                            icon = Tabler.Outline.Moon,
                            title = "Theme Mode",
                            subtitle = when (preferences.themeMode) {
                                ThemeMode.SYSTEM -> "Follow system setting"
                                ThemeMode.LIGHT -> "Always light"
                                ThemeMode.DARK -> "Always dark"
                            },
                            trailingText = preferences.themeMode.name,
                            index = 0, count = baseCount,
                            onClick = {
                                val next = when (preferences.themeMode) {
                                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                    ThemeMode.LIGHT -> ThemeMode.DARK
                                    ThemeMode.DARK -> ThemeMode.SYSTEM
                                }
                                viewModel.setThemeMode(next)
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Video,
                            title = "Dynamic Theming",
                            subtitle = "Colors extracted from artwork",
                            checked = preferences.dynamicTheming,
                            index = 1, count = baseCount,
                            onCheckedChange = { viewModel.setDynamicTheming(it) },
                        )
                        if (isDarkActive) {
                            SettingToggleItem(
                                icon = Tabler.Outline.BrightnessHalf,
                                title = "OLED Mode",
                                subtitle = "Pure black backgrounds for AMOLED displays",
                                checked = preferences.oledMode,
                                index = 2, count = baseCount,
                                onCheckedChange = { viewModel.setOledMode(it) },
                            )
                        }
                        val contrastIndex = if (isDarkActive) 3 else 2
                        SettingListItem(
                            icon = Tabler.Outline.Adjustments,
                            title = "Contrast",
                            subtitle = when (preferences.contrastLevel) {
                                ContrastLevel.DEFAULT -> "Standard contrast"
                                ContrastLevel.MEDIUM -> "Medium contrast"
                                ContrastLevel.HIGH -> "High contrast"
                            },
                            trailingText = preferences.contrastLevel.name,
                            index = contrastIndex, count = baseCount,
                            onClick = {
                                val next = when (preferences.contrastLevel) {
                                    ContrastLevel.DEFAULT -> ContrastLevel.MEDIUM
                                    ContrastLevel.MEDIUM -> ContrastLevel.HIGH
                                    ContrastLevel.HIGH -> ContrastLevel.DEFAULT
                                }
                                viewModel.setContrastLevel(next)
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Home,
                            title = "Home Mode",
                            subtitle = if (preferences.homeMode == HomeMode.VIDEO) "Video-focused home screen" else "Music-focused home screen",
                            trailingText = preferences.homeMode.name,
                            index = if (isDarkActive) 4 else 3, count = baseCount,
                            onClick = {
                                val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                                viewModel.setHomeMode(next)
                            },
                        )
                    } else {
                        SettingToggleItem(
                            icon = Tabler.Outline.Video,
                            title = "Dynamic Theming",
                            subtitle = "Colors extracted from artwork",
                            checked = preferences.dynamicTheming,
                            index = 0, count = 2,
                            onCheckedChange = { viewModel.setDynamicTheming(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Home,
                            title = "Home Mode",
                            subtitle = if (preferences.homeMode == HomeMode.VIDEO) "Video-focused home screen" else "Music-focused home screen",
                            trailingText = preferences.homeMode.name,
                            index = 1, count = 2,
                            onClick = {
                                val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                                viewModel.setHomeMode(next)
                            },
                        )
                    }
                }
            }

            if (isTv) {
                AnimatedSettingsEntrance(12) {
                    SettingsGroup(
                        icon = Tabler.Outline.Moon,
                        title = "Screensaver",
                        summary = {
                            val cats = preferences.dreamImageCategories.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                            cats.joinToString(", ")
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        val dreamTotal = 5
                        SettingToggleItem(
                            icon = Tabler.Outline.Typography,
                            title = "Show Title",
                            subtitle = if (preferences.dreamShowTitle) "Display media title" else "Hide media title",
                            checked = preferences.dreamShowTitle,
                            index = 0, count = dreamTotal,
                            onCheckedChange = { viewModel.setDreamShowTitle(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Movie,
                            title = "Categories",
                            subtitle = "Choose which library types appear",
                            trailingText = preferences.dreamImageCategories.joinToString(", ") {
                                when (it) {
                                    DreamImageCategory.MOVIES -> "Movies"
                                    DreamImageCategory.SERIES -> "TV"
                                    DreamImageCategory.MUSIC -> "Music"
                                }
                            },
                            index = 1, count = dreamTotal,
                            onClick = {
                                val allCats = DreamImageCategory.entries.toSet()
                                val current = preferences.dreamImageCategories
                                val next = if (current.size == allCats.size) {
                                    setOf(DreamImageCategory.MOVIES)
                                } else {
                                    val cycle = allCats.toList()
                                    val nextIndex = current.size
                                    cycle.take(nextIndex + 1).toSet()
                                }
                                viewModel.setDreamImageCategories(next)
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Stopwatch,
                            title = "Slideshow Interval",
                            subtitle = "Time between image transitions",
                            trailingText = "${(preferences.dreamSlideshowIntervalMs / 1000)}s",
                            index = 2, count = dreamTotal,
                            onClick = {
                                val intervals = listOf(10_000L, 15_000L, 20_000L, 30_000L, 45_000L, 60_000L)
                                val current = intervals.indexOf(preferences.dreamSlideshowIntervalMs)
                                val next = intervals[(current + 1) % intervals.size]
                                viewModel.setDreamSlideshowIntervalMs(next)
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.HandFinger,
                            title = "Ken Burns Effect",
                            subtitle = if (preferences.dreamKenBurnsEnabled) "Gentle pan and zoom" else "Static images",
                            checked = preferences.dreamKenBurnsEnabled,
                            index = 3, count = dreamTotal,
                            onCheckedChange = { viewModel.setDreamKenBurnsEnabled(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.ChevronRight,
                            title = "Transition Style",
                            subtitle = "How images transition between each other",
                            trailingText = preferences.dreamTransitionStyle.name,
                            index = 4, count = dreamTotal,
                            onClick = {
                                val styles = DreamTransitionStyle.entries
                                val current = styles.indexOf(preferences.dreamTransitionStyle)
                                val next = styles[(current + 1) % styles.size]
                                viewModel.setDreamTransitionStyle(next)
                            },
                        )
                    }
                }
            }

            AnimatedSettingsEntrance(if (isTv) 13 else 12) {
                val biometricAvailability = rememberBiometricAvailability()
                val canShowBiometric = biometricAvailability == BiometricAuthHelper.Availability.AVAILABLE

                SettingsGroup(
                    icon = Tabler.Outline.Lock,
                    title = "Security",
                    summary = {
                        when {
                            preferences.pinLockEnabled && preferences.biometricLockEnabled -> "PIN + Biometric lock: On"
                            preferences.biometricLockEnabled -> "Biometric lock: On"
                            preferences.pinLockEnabled -> "PIN lock: On"
                            else -> "Lock: Off"
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val secTotal = if (canShowBiometric) 3 else 2
                    SettingToggleItem(
                        icon = if (preferences.pinLockEnabled) Tabler.Outline.Lock else Tabler.Outline.LockOpen,
                        title = "PIN Lock",
                        subtitle = if (preferences.pinLockEnabled) "App locked with PIN" else "No PIN set",
                        checked = preferences.pinLockEnabled,
                        index = 0, count = secTotal,
                        onCheckedChange = { enabled ->
                            if (enabled) showPinDialog = true
                            else showPinDisableAuth = true
                        },
                        onClick = {
                            if (preferences.pinLockEnabled) showPinDisableAuth = true
                            else showPinDialog = true
                        },
                    )
                    if (canShowBiometric) {
                        val bioContext = LocalContext.current
                        val bioActivity = remember(bioContext) { bioContext as? FragmentActivity }
                        SettingToggleItem(
                            icon = Tabler.Outline.Fingerprint,
                            title = "Biometric Unlock",
                            subtitle = if (preferences.biometricLockEnabled) "Use fingerprint, face, or device credential" else "Disabled",
                            checked = preferences.biometricLockEnabled,
                            index = 1, count = secTotal,
                            onCheckedChange = { enabled ->
                                if (enabled && bioActivity != null) {
                                    BiometricAuthHelper.authenticate(
                                        activity = bioActivity,
                                        title = "Enable Biometric Unlock",
                                        subtitle = "Verify your identity to enable biometric lock",
                                        onSuccess = { viewModel.setBiometricLockEnabled(true) },
                                        onError = {},
                                        onFailed = {},
                                    )
                                } else if (!enabled && bioActivity != null) {
                                    BiometricAuthHelper.authenticate(
                                        activity = bioActivity,
                                        title = "Disable Biometric Unlock",
                                        subtitle = "Verify your identity to disable biometric lock",
                                        onSuccess = { viewModel.setBiometricLockEnabled(false) },
                                        onError = {},
                                        onFailed = {},
                                    )
                                }
                            },
                            onClick = {
                                if (preferences.biometricLockEnabled && bioActivity != null) {
                                    BiometricAuthHelper.authenticate(
                                        activity = bioActivity,
                                        title = "Disable Biometric Unlock",
                                        subtitle = "Verify your identity to disable biometric lock",
                                        onSuccess = { viewModel.setBiometricLockEnabled(false) },
                                        onError = {},
                                        onFailed = {},
                                    )
                                } else if (!preferences.biometricLockEnabled && bioActivity != null) {
                                    BiometricAuthHelper.authenticate(
                                        activity = bioActivity,
                                        title = "Enable Biometric Unlock",
                                        subtitle = "Verify your identity to enable biometric lock",
                                        onSuccess = { viewModel.setBiometricLockEnabled(true) },
                                        onError = {},
                                        onFailed = {},
                                    )
                                }
                            },
                        )
                    }
                    if (preferences.pinLockEnabled || preferences.biometricLockEnabled) {
                        val autoLockPresets = listOf(
                            15_000L to "15 sec",
                            30_000L to "30 sec",
                            60_000L to "1 min",
                            120_000L to "2 min",
                            300_000L to "5 min",
                            600_000L to "10 min",
                            1_800_000L to "30 min",
                            3_600_000L to "1 hour",
                            7_200_000L to "2 hours",
                            0L to "Never",
                        )
                        val currentLabel = autoLockPresets.find { it.first == preferences.autoLockTimerMs }?.second ?: "30 sec"
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = "Auto-Lock Timer",
                            subtitle = "Lock app after going to background",
                            trailingText = currentLabel,
                            index = if (canShowBiometric) 2 else 1, count = secTotal,
                            onClick = {
                                val currentIndex = autoLockPresets.indexOfFirst { it.first == preferences.autoLockTimerMs }
                                val nextIndex = (currentIndex + 1) % autoLockPresets.size
                                viewModel.setAutoLockTimerMs(autoLockPresets[nextIndex].first)
                            },
                        )
                    }
                }
            }

            AnimatedSettingsEntrance(if (isTv) 14 else 13) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingInfoItem(
                        icon = Tabler.Outline.Video,
                        title = "Version",
                        subtitle = "JellyPlay v${viewModel.appVersion}",
                        index = 0, count = 1,
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                pinConfirm = ""
                pinError = null
            },
            title = { Text("Set PIN Lock") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        label = { Text("4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinConfirm = it
                                pinError = null
                            }
                        },
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = pinError != null,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        pinError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pinInput.length != 4 -> pinError = "PIN must be 4 digits"
                        pinInput != pinConfirm -> pinError = "PINs do not match"
                        else -> {
                            viewModel.setPin(pinInput)
                            showPinDialog = false
                            pinInput = ""
                            pinConfirm = ""
                            pinError = null
                        }
                    }
                }) { Text("Set PIN") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pinInput = ""
                    pinConfirm = ""
                    pinError = null
                }) { Text("Cancel") }
            },
        )
    }

    if (showPinDisableAuth) {
        AlertDialog(
            onDismissRequest = {
                showPinDisableAuth = false
                pinDisableAuthError = null
            },
            title = { Text("Disable PIN Lock") },
            text = {
                Column {
                    Text(
                        "Enter your current PIN to disable PIN lock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinInput = it
                                pinDisableAuthError = null
                            }
                        },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = pinDisableAuthError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = pinDisableAuthError != null,
                        enter = expandVertically(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        pinDisableAuthError?.let { error ->
                            Text(
                                error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val valid = viewModel.verifyPin(pinInput)
                    if (valid) {
                        viewModel.clearPin()
                        showPinDisableAuth = false
                        pinInput = ""
                        pinDisableAuthError = null
                    } else {
                        pinDisableAuthError = "Incorrect PIN"
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDisableAuth = false
                    pinInput = ""
                    pinDisableAuthError = null
                }) { Text("Cancel") }
            },
        )
    }

    if (showPlayerPicker) {
        SettingsListPickerSheet(
            title = "Preferred Player",
            items = PlayerType.entries,
            label = { it.displayName },
            subtitle = { it.description },
            isSelected = { it == preferences.preferredPlayer },
            onDismiss = { showPlayerPicker = false },
            onSelect = { viewModel.setPreferredPlayer(it); showPlayerPicker = false },
        )
    }

    if (showOrientationPicker) {
        SettingsListPickerSheet(
            title = "Default Orientation",
            items = OrientationMode.entries,
            label = { it.displayName },
            subtitle = { it.constant },
            isSelected = { it == preferences.videoDefaultOrientation },
            onDismiss = { showOrientationPicker = false },
            onSelect = { viewModel.setVideoDefaultOrientation(it); showOrientationPicker = false },
        )
    }

    if (showAspectRatioPicker) {
        val aspectRatios = listOf("AUTO", "FIT", "FILL", "CROP", "16:9", "4:3", "21:9")
        SettingsListPickerSheet(
            title = "Default Aspect Ratio",
            items = aspectRatios,
            label = { it },
            isSelected = { it == preferences.videoDefaultAspectRatio },
            onDismiss = { showAspectRatioPicker = false },
            onSelect = {
                viewModel.setVideoDefaultAspectRatio(it)
                showAspectRatioPicker = false
            },
        )
    }

    if (showVideoSpeedPicker) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        SettingsChipPickerSheet(
            title = "Default Video Speed",
            options = speeds.map { if (it == 1.0f) "1x" else "${it}x" },
            selectedIndex = speeds.indexOf(preferences.videoDefaultSpeed),
            onDismiss = { showVideoSpeedPicker = false },
            onSelect = { index ->
                viewModel.setVideoDefaultSpeed(speeds[index])
                showVideoSpeedPicker = false
            },
        )
    }

    if (showAudioSpeedPicker) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        SettingsChipPickerSheet(
            title = "Default Audio Speed",
            options = speeds.map { if (it == 1.0f) "1x" else "${it}x" },
            selectedIndex = speeds.indexOf(preferences.audioDefaultSpeed),
            onDismiss = { showAudioSpeedPicker = false },
            onSelect = { index ->
                viewModel.setAudioDefaultSpeed(speeds[index])
                showAudioSpeedPicker = false
            },
        )
    }

    if (showVideoSeekDurationPicker) {
        val durations = listOf(5_000L, 10_000L, 15_000L, 20_000L, 30_000L, 60_000L)
        SettingsChipPickerSheet(
            title = "Double-Tap Seek Duration",
            options = durations.map { "${it / 1000}s" },
            selectedIndex = durations.indexOf(preferences.videoSeekDurationMs),
            onDismiss = { showVideoSeekDurationPicker = false },
            onSelect = { index ->
                viewModel.setVideoSeekDurationMs(durations[index])
                showVideoSeekDurationPicker = false
            },
        )
    }

    if (showControlsTimeoutPicker) {
        val timeouts = listOf(3_000L, 5_000L, 10_000L, 15_000L, 20_000L, 30_000L)
        SettingsChipPickerSheet(
            title = "Controls Auto-Hide Timeout",
            options = timeouts.map { "${it / 1000}s" },
            selectedIndex = timeouts.indexOf(preferences.videoControlsTimeoutMs),
            onDismiss = { showControlsTimeoutPicker = false },
            onSelect = { index ->
                viewModel.setVideoControlsTimeoutMs(timeouts[index])
                showControlsTimeoutPicker = false
            },
        )
    }

    if (showSwipeSeekPicker) {
        val ranges = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L, 300_000L)
        SettingsChipPickerSheet(
            title = "Swipe Seek Maximum Range",
            options = ranges.map { "${it / 1000}s" },
            selectedIndex = ranges.indexOf(preferences.videoSwipeSeekMaxMs),
            onDismiss = { showSwipeSeekPicker = false },
            onSelect = { index ->
                viewModel.setVideoSwipeSeekMaxMs(ranges[index])
                showSwipeSeekPicker = false
            },
        )
    }

    if (showPreloadBufferPicker) {
        SettingsListPickerSheet(
            title = "Preload Buffer Size",
            items = PreloadBufferSize.entries,
            label = { it.displayName },
            subtitle = {
                "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s"
            },
            isSelected = { it == preferences.videoPreloadBufferSize },
            onDismiss = { showPreloadBufferPicker = false },
            onSelect = {
                viewModel.setVideoPreloadBufferSize(it)
                showPreloadBufferPicker = false
            },
        )
    }

    if (showAudioPreloadBufferPicker) {
        SettingsListPickerSheet(
            title = "Audio Preload Buffer Size",
            items = PreloadBufferSize.entries,
            label = { it.displayName },
            subtitle = {
                "Min: ${it.minBufferMs / 1000}s · Max: ${it.maxBufferMs / 1000}s"
            },
            isSelected = { it == preferences.audioPreloadBufferSize },
            onDismiss = { showAudioPreloadBufferPicker = false },
            onSelect = {
                viewModel.setAudioPreloadBufferSize(it)
                showAudioPreloadBufferPicker = false
            },
        )
    }

    if (showNightModeVolumePicker) {
        SettingsSliderSheet(
            title = "Night Mode Volume",
            value = preferences.audioNightModeVolume,
            valueRange = 0.1f..0.8f,
            steps = 6,
            valueLabel = { "${(it * 100).toInt()}%" },
            rangeStartLabel = "10%",
            rangeEndLabel = "80%",
            onDismiss = { showNightModeVolumePicker = false },
            onConfirm = {
                viewModel.setAudioNightModeVolume(it)
                showNightModeVolumePicker = false
            },
        )
    }

    if (showNightModeGainPicker) {
        SettingsSliderSheet(
            title = "Night Mode Loudness Gain",
            value = preferences.audioNightModeGain.toFloat(),
            valueRange = 0f..3000f,
            steps = 29,
            valueLabel = { "${it.toInt()}" },
            rangeStartLabel = "0",
            rangeEndLabel = "3000",
            onDismiss = { showNightModeGainPicker = false },
            onConfirm = {
                viewModel.setAudioNightModeGain(it.toInt())
                showNightModeGainPicker = false
            },
        )
    }

    if (showSkipPrevThresholdPicker) {
        val thresholds = listOf(1_000L, 2_000L, 3_000L, 5_000L, 7_000L, 10_000L)
        SettingsChipPickerSheet(
            title = "Skip Previous Threshold",
            options = thresholds.map { "${it / 1000}s" },
            selectedIndex = thresholds.indexOf(preferences.audioSkipPreviousThresholdMs),
            onDismiss = { showSkipPrevThresholdPicker = false },
            onSelect = { index ->
                viewModel.setAudioSkipPreviousThresholdMs(thresholds[index])
                showSkipPrevThresholdPicker = false
            },
        )
    }

    if (showCrossfadePicker) {
        val durations = listOf(0L, 2000L, 3000L, 5000L, 8000L, 12000L)
        SettingsChipPickerSheet(
            title = "Crossfade Duration",
            options = durations.map { if (it == 0L) "Off" else "${it / 1000}s" },
            selectedIndex = durations.indexOf(preferences.audioCrossfadeDurationMs),
            onDismiss = { showCrossfadePicker = false },
            onSelect = { index ->
                viewModel.setCrossfadeDurationMs(durations[index])
                showCrossfadePicker = false
            },
        )
    }

    if (showNormalizationModePicker) {
        val modes = AudioNormalizationMode.entries
        SettingsChipPickerSheet(
            title = "Volume Normalization",
            options = modes.map { it.displayName },
            selectedIndex = modes.indexOf(preferences.audioNormalizationMode),
            onDismiss = { showNormalizationModePicker = false },
            onSelect = { index ->
                viewModel.setAudioNormalizationMode(modes[index])
                showNormalizationModePicker = false
            },
        )
    }

    if (showPreAmpPicker) {
        SettingsSliderSheet(
            title = "ReplayGain Pre-Amp",
            value = preferences.replayGainPreAmpDb,
            valueRange = -15f..15f,
            steps = 59,
            valueLabel = { "${if (it >= 0) "+" else ""}${String.format("%.1f", it)} dB" },
            rangeStartLabel = "-15 dB",
            rangeEndLabel = "+15 dB",
            onDismiss = { showPreAmpPicker = false },
            onConfirm = {
                viewModel.setReplayGainPreAmpDb(it)
                showPreAmpPicker = false
            },
        )
    }

    if (showAudioDelayPicker) {
        SettingsSliderSheet(
            title = "Audio Delay",
            value = preferences.audioDelayMs.toFloat(),
            valueRange = -500f..500f,
            steps = 99,
            valueLabel = { if (it.toLong() == 0L) "No delay" else "${it.toLong()}ms" },
            rangeStartLabel = "-500ms",
            rangeEndLabel = "+500ms",
            onDismiss = { showAudioDelayPicker = false },
            onConfirm = {
                viewModel.setAudioDelayMs(it.toLong())
                showAudioDelayPicker = false
            },
        )
    }

    if (showSubtitleFontPicker) {
        val sizes = listOf(14, 18, 22, 24, 28, 32, 36, 40)
        SettingsChipPickerSheet(
            title = "Subtitle Font Size",
            options = sizes.map { "${it}sp" },
            selectedIndex = sizes.indexOf(preferences.subtitleStyle.fontSize),
            onDismiss = { showSubtitleFontPicker = false },
            onSelect = { index ->
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontSize = sizes[index]))
                showSubtitleFontPicker = false
            },
        )
    }

    if (showSubtitleColorPicker) {
        SettingsListPickerSheet(
            title = "Subtitle Text Color",
            items = SubtitleColor.entries,
            label = { it.name },
            isSelected = { it == preferences.subtitleStyle.fontColor },
            onDismiss = { showSubtitleColorPicker = false },
            onSelect = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontColor = it))
                showSubtitleColorPicker = false
            },
        )
    }

    if (showSubtitleBgColorPicker) {
        var bgOpacity by remember { mutableStateOf(preferences.subtitleStyle.backgroundOpacity) }
        AdaptiveSheet(onDismissRequest = { showSubtitleBgColorPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "Subtitle Background",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.35f),
                ) {
                    itemsIndexed(SubtitleColor.entries, contentType = { _, _ -> "color" }) { index, color ->
                        val selected = color == preferences.subtitleStyle.backgroundColor
                        val shape = com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape(
                            index, SubtitleColor.entries.size,
                        )
                        val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                )
                                .then(tvFocusState.focusModifier)
                                .tvFocusIndicator(tvFocusState, shape)
                                .clickable {
                                    viewModel.setSubtitleStyle(
                                        preferences.subtitleStyle.copy(backgroundColor = color),
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                color.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Opacity: ${(bgOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = bgOpacity,
                    onValueChange = { bgOpacity = it },
                    valueRange = 0f..1f,
                    steps = 9,
                )
            }
        }
    }

    if (showSubtitleEdgePicker) {
        SettingsListPickerSheet(
            title = "Subtitle Edge Style",
            items = SubtitleEdgeType.entries,
            label = { it.name },
            isSelected = { it == preferences.subtitleStyle.edgeType },
            onDismiss = { showSubtitleEdgePicker = false },
            onSelect = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(edgeType = it))
                showSubtitleEdgePicker = false
            },
        )
    }

    if (showSubtitleOffsetPicker) {
        SettingsSliderSheet(
            title = "Subtitle Sync Offset",
            value = preferences.subtitleStyle.offsetMs.toFloat(),
            valueRange = -5000f..5000f,
            steps = 99,
            valueLabel = { "${it.toLong()}ms" },
            rangeStartLabel = "-5s",
            rangeEndLabel = "+5s",
            onDismiss = { showSubtitleOffsetPicker = false },
            onConfirm = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(offsetMs = it.toLong()))
                showSubtitleOffsetPicker = false
            },
        )
    }

    if (showSubtitlePositionPicker) {
        SettingsSliderSheet(
            title = "Subtitle Vertical Position",
            value = preferences.subtitleStyle.verticalPosition,
            valueRange = 0f..0.4f,
            steps = 7,
            valueLabel = { "${(it * 100).toInt()}%" },
            rangeStartLabel = "Bottom",
            rangeEndLabel = "40%",
            onDismiss = { showSubtitlePositionPicker = false },
            onConfirm = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(verticalPosition = it))
                showSubtitlePositionPicker = false
            },
        )
    }

    if (showEqualizerEditor) {
        val bandLevels = preferences.equalizerSettings.bandLevels.toMutableStateList()
        AdaptiveSheet(onDismissRequest = { showEqualizerEditor = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "Equalizer",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f),
                ) {
                    items(EqualizerSettings.BAND_FREQUENCIES.size, contentType = { _ -> "band" }) { i ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${EqualizerSettings.BAND_FREQUENCIES[i]} Hz",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("-15", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                                Slider(
                                    value = (bandLevels[i] + 15).toFloat(),
                                    onValueChange = { bandLevels[i] = (it - 15).toInt() },
                                    valueRange = 0f..30f,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("+15", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showEqualizerEditor = false }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        viewModel.setEqualizerSettings(EqualizerSettings(bandLevels.toList()))
                        showEqualizerEditor = false
                    }) { Text("Apply") }
                }
            }
        }
    }

    if (showAudioLanguagePicker) {
        SettingsListPickerSheet(
            title = "Audio Language",
            items = languages.map { it.first },
            label = { code -> languages.find { it.first == code }?.second ?: code ?: "Default" },
            isSelected = { it == preferences.preferredAudioLanguage },
            onDismiss = { showAudioLanguagePicker = false },
            onSelect = {
                viewModel.setPreferredAudioLanguage(it)
                showAudioLanguagePicker = false
            },
        )
    }

    if (showSubtitleLanguagePicker) {
        SettingsListPickerSheet(
            title = "Subtitle Language",
            items = languages.map { it.first },
            label = { code -> languages.find { it.first == code }?.second ?: code ?: "Default" },
            isSelected = { it == preferences.preferredSubtitleLanguage },
            onDismiss = { showSubtitleLanguagePicker = false },
            onSelect = {
                viewModel.setPreferredSubtitleLanguage(it)
                showSubtitleLanguagePicker = false
            },
        )
    }
}

@Composable
private fun SettingsProfileBanner(
    userName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.User,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                "Signed in as",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Text(
                userName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AnimatedSettingsEntrance(
    index: Int,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        ) + expandVertically(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ),
    ) {
        content()
    }
}

private fun streamingQualityLabel(quality: StreamingQuality): String = when (quality) {
    StreamingQuality.AUTO -> "Auto (adaptive)"
    StreamingQuality.LOW_360P -> "360p (Low)"
    StreamingQuality.SD_480P -> "480p (SD)"
    StreamingQuality.HD_720P -> "720p (HD)"
    StreamingQuality.FHD_1080P -> "1080p (Full HD)"
    StreamingQuality.UHD_4K -> "4K (Ultra HD)"
}

private fun streamingQualityShort(quality: StreamingQuality): String = when (quality) {
    StreamingQuality.AUTO -> "Auto"
    StreamingQuality.LOW_360P -> "360p"
    StreamingQuality.SD_480P -> "480p"
    StreamingQuality.HD_720P -> "720p"
    StreamingQuality.FHD_1080P -> "1080p"
    StreamingQuality.UHD_4K -> "4K"
}
