package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.ui.tv.isTvDevice
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onServerManagement: () -> Unit = {},
    onUserManagement: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val userName = viewModel.currentUserName
    val adaptiveInfo = LocalAdaptiveInfo.current

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
    var showNightModeVolumePicker by remember { mutableStateOf(false) }
    var showNightModeGainPicker by remember { mutableStateOf(false) }
    var showSkipPrevThresholdPicker by remember { mutableStateOf(false) }
    var showAudioDelayPicker by remember { mutableStateOf(false) }
    var showSyncPlayMinDelayPicker by remember { mutableStateOf(false) }
    var showSyncPlayMaxDelayPicker by remember { mutableStateOf(false) }
    var showSyncPlayDurationPicker by remember { mutableStateOf(false) }
    var showSubtitleFontPicker by remember { mutableStateOf(false) }
    var showSubtitleColorPicker by remember { mutableStateOf(false) }
    var showSubtitleBgColorPicker by remember { mutableStateOf(false) }
    var showSubtitleEdgePicker by remember { mutableStateOf(false) }
    var showSubtitleOffsetPicker by remember { mutableStateOf(false) }
    var showSubtitlePositionPicker by remember { mutableStateOf(false) }
    var showEqualizerEditor by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings")
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusable()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { padding ->
        var visibleItemCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            visibleItemCount = Int.MAX_VALUE
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTvDevice()) - 16.dp,
                end = adaptiveInfo.contentPadding(isTvDevice()) - 16.dp,
                bottom = 80.dp,
            ),
        ) {
            if (userName.isNotBlank()) {
                item {
                    AnimatedSettingsEntrance(0, visibleItemCount) {
                        SettingsProfileBanner(
                            userName = userName,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            item {
                AnimatedSettingsEntrance(1, visibleItemCount) {
                    SettingsSectionHeader(title = "Account")
                }
            }

            item {
                AnimatedSettingsEntrance(2, visibleItemCount) {
                    SettingListItem(
                        icon = Icons.Default.Dns,
                        title = "Servers",
                        subtitle = "Manage your Jellyfin servers",
                        onClick = onServerManagement,
                    )
                }
            }

            item {
                AnimatedSettingsEntrance(3, visibleItemCount) {
                    SettingListItem(
                        icon = Icons.Default.Person,
                        title = "Switch User",
                        subtitle = userName.ifBlank { "Manage users" },
                        onClick = onUserManagement,
                    )
                }
            }

            item {
                AnimatedSettingsEntrance(4, visibleItemCount) {
                    SettingListItem(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Sign Out",
                        subtitle = "Log out of current account",
                        isDestructive = true,
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                AnimatedSettingsEntrance(5, visibleItemCount) {
                    SettingsSectionHeader(title = "Video Player")
                }
            }

            item {
                AnimatedSettingsEntrance(6, visibleItemCount) {
                    SettingListItem(
                        icon = Icons.Default.PlayCircle,
                        title = "Player Engine",
                        subtitle = "Choose media playback engine",
                        trailingText = preferences.preferredPlayer.displayName,
                        onClick = { showPlayerPicker = true },
                    )
                }
            }

            item {
                AnimatedSettingsEntrance(7, visibleItemCount) {
                    SettingListItem(
                        icon = Icons.Default.FastForward,
                        title = "Seek Duration",
                        subtitle = "Double-tap to seek",
                        trailingText = "${preferences.videoSeekDurationMs / 1000}s",
                        onClick = { showVideoSeekDurationPicker = true },
                    )
                }
            }

            item {
                AnimatedSettingsEntrance(8, visibleItemCount) {
                    SettingListItem(
                        icon = Icons.Default.ScreenLockLandscape,
                        title = "Orientation",
                        subtitle = "Default screen orientation",
                        trailingText = preferences.videoDefaultOrientation.displayName,
                        onClick = { showOrientationPicker = true },
                    )
                }
            }

            item {
                AnimatedSettingsEntrance(9, visibleItemCount) {
                    SettingListItem(
                        icon = Icons.Default.Timer,
                        title = "Controls Timeout",
                        subtitle = "Auto-hide player controls",
                        trailingText = "${preferences.videoControlsTimeoutMs / 1000}s",
                        onClick = { showControlsTimeoutPicker = true },
                    )
                }
            }

            item {
                AnimatedSettingsEntrance(10, visibleItemCount) {
                    SettingToggleItem(
                        icon = Icons.Default.Gesture,
                        title = "Gestures",
                        subtitle = if (preferences.videoGesturesEnabled) "Swipe & tap controls active" else "Touch gestures disabled",
                        checked = preferences.videoGesturesEnabled,
                        onCheckedChange = { viewModel.setVideoGesturesEnabled(it) },
                    )
                }
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Speed,
                    title = "Default Speed",
                    subtitle = if (preferences.videoDefaultSpeed == 1.0f) "Normal playback speed" else "${preferences.videoDefaultSpeed}x playback",
                    trailingText = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                    onClick = { showVideoSpeedPicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.AspectRatio,
                    title = "Default Aspect",
                    subtitle = "Video aspect ratio mode",
                    trailingText = preferences.videoDefaultAspectRatio,
                    onClick = { showAspectRatioPicker = true },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.QueuePlayNext,
                    title = "Auto-play Next",
                    subtitle = if (preferences.videoAutoplayNext) "Automatically plays next episode" else "Manual episode selection",
                    checked = preferences.videoAutoplayNext,
                    onCheckedChange = { viewModel.setVideoAutoplayNext(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.VideoLibrary,
                    title = "Episode Browser",
                    subtitle = if (preferences.videoEpisodeBrowserEnabled) "Browse episodes during playback" else "Episode picker disabled",
                    checked = preferences.videoEpisodeBrowserEnabled,
                    onCheckedChange = { viewModel.setVideoEpisodeBrowserEnabled(it) },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Swipe,
                    title = "Swipe Seek Range",
                    subtitle = "Maximum seek distance",
                    trailingText = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                    onClick = { showSwipeSeekPicker = true },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Brightness6,
                    title = "Remember Brightness",
                    subtitle = if (preferences.videoRememberBrightness) "Brightness saved between sessions" else "Reset brightness each session",
                    checked = preferences.videoRememberBrightness,
                    onCheckedChange = { viewModel.setVideoRememberBrightness(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.OndemandVideo,
                    title = "Trickplay Preview",
                    subtitle = if (preferences.trickplayEnabled) "Show preview images while scrubbing" else "No preview images on seek bar",
                    checked = preferences.trickplayEnabled,
                    onCheckedChange = { viewModel.setTrickplayEnabled(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.TouchApp,
                    title = "Trickplay on Gestures",
                    subtitle = if (preferences.trickplayOnSeekGesture) "Show preview on swipe seek" else "No preview on swipe gestures",
                    checked = preferences.trickplayOnSeekGesture,
                    onCheckedChange = { viewModel.setTrickplayOnSeekGesture(it) },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Skip Intro & Outro")
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.FastForward,
                    title = "Skip Intro Button",
                    subtitle = if (preferences.skipIntroEnabled) "Show skip intro button when available" else "Never show skip intro button",
                    checked = preferences.skipIntroEnabled,
                    onCheckedChange = { viewModel.setSkipIntroEnabled(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.FastForward,
                    title = "Skip Outro Button",
                    subtitle = if (preferences.skipOutroEnabled) "Show skip credits button when available" else "Never show skip credits button",
                    checked = preferences.skipOutroEnabled,
                    onCheckedChange = { viewModel.setSkipOutroEnabled(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.SkipNext,
                    title = "Auto-Skip Intro",
                    subtitle = if (preferences.autoSkipIntro) "Automatically skip intros" else "Manual intro skip only",
                    checked = preferences.autoSkipIntro,
                    onCheckedChange = { viewModel.setAutoSkipIntro(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.SkipNext,
                    title = "Auto-Skip Outro",
                    subtitle = if (preferences.autoSkipOutro) "Automatically skip credits" else "Manual credits skip only",
                    checked = preferences.autoSkipOutro,
                    onCheckedChange = { viewModel.setAutoSkipOutro(it) },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Advanced Video")
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Dialogue Boost",
                    subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else "Off",
                    checked = preferences.dialogueBoostEnabled,
                    onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                )
            }

            if (preferences.dialogueBoostEnabled) {
                item {
                    SettingListItem(
                        icon = Icons.Default.Audiotrack,
                        title = "Dialogue Boost Strength",
                        subtitle = preferences.dialogueBoostStrength.displayName,
                        trailingText = preferences.dialogueBoostStrength.displayName,
                        onClick = {
                            val strengths = EffectStrength.entries
                            val currentIndex = strengths.indexOf(preferences.dialogueBoostStrength)
                            val nextIndex = (currentIndex + 1) % strengths.size
                            viewModel.setDialogueBoostStrength(strengths[nextIndex])
                        },
                    )
                }
            }

            item {
                SettingListItem(
                    icon = Icons.Default.HighQuality,
                    title = "Decoder",
                    subtitle = preferences.decoderMode.displayName,
                    trailingText = preferences.decoderMode.displayName.split(" ").first(),
                    onClick = {
                        val modes = DecoderMode.entries
                        val currentIndex = modes.indexOf(preferences.decoderMode)
                        val nextIndex = (currentIndex + 1) % modes.size
                        viewModel.setDecoderMode(modes[nextIndex])
                    },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Movie,
                    title = "Audio Passthrough",
                    subtitle = if (preferences.audioPassthrough) "Direct audio to receiver" else "Software audio processing",
                    checked = preferences.audioPassthrough,
                    onCheckedChange = { viewModel.setAudioPassthrough(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Fullscreen,
                    title = "Frame Rate Match",
                    subtitle = if (preferences.frameRateMatching) "Display refresh matches content" else "Fixed display refresh rate",
                    checked = preferences.frameRateMatching,
                    onCheckedChange = { viewModel.setFrameRateMatching(it) },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.HighQuality,
                    title = "Streaming Quality",
                    subtitle = streamingQualityLabel(preferences.streamingQuality),
                    trailingText = streamingQualityShort(preferences.streamingQuality),
                    onClick = {
                        val qualities = StreamingQuality.entries
                        val currentIndex = qualities.indexOf(preferences.streamingQuality)
                        val nextIndex = (currentIndex + 1) % qualities.size
                        viewModel.setStreamingQuality(qualities[nextIndex])
                    },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Audiotrack,
                    title = "Audio Delay",
                    subtitle = if (preferences.audioDelayMs == 0L) "No audio delay" else "${preferences.audioDelayMs}ms delay",
                    trailingText = if (preferences.audioDelayMs == 0L) "Off" else "${preferences.audioDelayMs}ms",
                    onClick = { showAudioDelayPicker = true },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "SyncPlay")
            }

            item {
                var progressMode by remember { mutableStateOf(preferences.syncPlayProgressReportingMode) }
                SettingListItem(
                    icon = Icons.Default.Speed,
                    title = "Progress Reporting",
                    subtitle = when (progressMode) {
                        "SUPPRESS_DURING" -> "Suppress during SyncPlay"
                        "ALWAYS" -> "Always report"
                        "NEVER" -> "Never report"
                        else -> "Suppress during SyncPlay"
                    },
                    onClick = {
                        val modes = listOf("SUPPRESS_DURING", "ALWAYS", "NEVER")
                        val nextIndex = (modes.indexOf(progressMode) + 1) % modes.size
                        progressMode = modes[nextIndex]
                        viewModel.setSyncPlayProgressReportingMode(progressMode)
                    },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Group,
                    title = "Auto-Join Last Group",
                    subtitle = "Automatically rejoin last group on app start",
                    checked = preferences.syncPlayAutoJoinLastGroup,
                    onCheckedChange = { viewModel.setSyncPlayAutoJoinLastGroup(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Notifications,
                    title = "Join/Leave Notifications",
                    subtitle = "Show when users join or leave",
                    checked = preferences.syncPlayNotifyUserJoinLeave,
                    onCheckedChange = { viewModel.setSyncPlayNotifyUserJoinLeave(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Notifications,
                    title = "Default Ignore Wait",
                    subtitle = "Ignore group waits by default when joining",
                    checked = preferences.syncPlayDefaultIgnoreWait,
                    onCheckedChange = { viewModel.setSyncPlayDefaultIgnoreWait(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Speed,
                    title = "Sync Correction",
                    subtitle = "Automatically correct playback sync drift",
                    checked = preferences.syncPlaySyncCorrection,
                    onCheckedChange = { viewModel.setSyncPlaySyncCorrection(it) },
                )
            }

            if (preferences.syncPlaySyncCorrection) {
                item {
                    SettingToggleItem(
                        icon = Icons.Default.Speed,
                        title = "Speed Correction",
                        subtitle = "Adjust playback speed to fix small sync drift",
                        checked = preferences.syncPlaySpeedToSyncEnabled,
                        onCheckedChange = { viewModel.setSyncPlaySpeedToSyncEnabled(it) },
                    )
                }
            }

            if (preferences.syncPlaySyncCorrection && preferences.syncPlaySpeedToSyncEnabled) {
                item {
                    SettingListItem(
                        icon = Icons.Default.Timer,
                        title = "Speed Correction Min Delay",
                        subtitle = "Minimum delay before speed correction activates",
                        trailingText = "${preferences.syncPlaySpeedToSyncMinDelayMs}ms",
                        onClick = { showSyncPlayMinDelayPicker = true },
                    )
                }

                item {
                    SettingListItem(
                        icon = Icons.Default.Timer,
                        title = "Speed Correction Max Delay",
                        subtitle = "Maximum delay for speed correction (seeks above this)",
                        trailingText = "${preferences.syncPlaySpeedToSyncMaxDelayMs}ms",
                        onClick = { showSyncPlayMaxDelayPicker = true },
                    )
                }

                item {
                    SettingListItem(
                        icon = Icons.Default.Timer,
                        title = "Speed Correction Duration",
                        subtitle = "How long the speed adjustment lasts",
                        trailingText = "${preferences.syncPlaySpeedToSyncDurationMs}ms",
                        onClick = { showSyncPlayDurationPicker = true },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Audio Player")
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Speed,
                    title = "Default Speed",
                    subtitle = if (preferences.audioDefaultSpeed == 1.0f) "Normal playback speed" else "${preferences.audioDefaultSpeed}x playback",
                    trailingText = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                    onClick = { showAudioSpeedPicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.LibraryMusic,
                    title = "Night Mode Volume",
                    subtitle = "Maximum volume level at night",
                    trailingText = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                    onClick = { showNightModeVolumePicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Tune,
                    title = "Night Mode Gain",
                    subtitle = "Loudness compensation",
                    trailingText = "${preferences.audioNightModeGain}",
                    onClick = { showNightModeGainPicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.SkipNext,
                    title = "Skip Prev Threshold",
                    subtitle = "Restart song if past this point",
                    trailingText = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                    onClick = { showSkipPrevThresholdPicker = true },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.QueuePlayNext,
                    title = "Auto-play Next",
                    subtitle = if (preferences.audioAutoplayNext) "Automatically plays next track" else "Manual track selection",
                    checked = preferences.audioAutoplayNext,
                    onCheckedChange = { viewModel.setAudioAutoplayNext(it) },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Tune,
                    title = "Equalizer",
                    subtitle = if (preferences.equalizerEnabled) "10-band equalizer active" else "Equalizer disabled",
                    checked = preferences.equalizerEnabled,
                    onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                    onClick = { showEqualizerEditor = true },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Dialogue Boost",
                    subtitle = if (preferences.dialogueBoostEnabled) preferences.dialogueBoostStrength.displayName else "Off",
                    checked = preferences.dialogueBoostEnabled,
                    onCheckedChange = { viewModel.setDialogueBoostEnabled(it) },
                )
            }

            if (preferences.dialogueBoostEnabled) {
                item {
                    SettingListItem(
                        icon = Icons.Default.Audiotrack,
                        title = "Dialogue Boost Strength",
                        subtitle = preferences.dialogueBoostStrength.displayName,
                        trailingText = preferences.dialogueBoostStrength.displayName,
                        onClick = {
                            val strengths = EffectStrength.entries
                            val currentIndex = strengths.indexOf(preferences.dialogueBoostStrength)
                            val nextIndex = (currentIndex + 1) % strengths.size
                            viewModel.setDialogueBoostStrength(strengths[nextIndex])
                        },
                    )
                }
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Speed,
                    title = "Night Mode",
                    subtitle = if (preferences.nightModeEnabled) preferences.nightModeStrength.displayName else "Off",
                    checked = preferences.nightModeEnabled,
                    onCheckedChange = { viewModel.setNightModeEnabled(it) },
                )
            }

            if (preferences.nightModeEnabled) {
                item {
                    SettingListItem(
                        icon = Icons.Default.Nightlight,
                        title = "Night Mode Strength",
                        subtitle = preferences.nightModeStrength.displayName,
                        trailingText = preferences.nightModeStrength.displayName,
                        onClick = {
                            val strengths = EffectStrength.entries
                            val currentIndex = strengths.indexOf(preferences.nightModeStrength)
                            val nextIndex = (currentIndex + 1) % strengths.size
                            viewModel.setNightModeStrength(strengths[nextIndex])
                        },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Language")
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Language,
                    title = "Audio Language",
                    subtitle = "Preferred audio track language",
                    trailingText = preferences.preferredAudioLanguage ?: "Default",
                    onClick = { showAudioLanguagePicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.ClosedCaption,
                    title = "Subtitle Language",
                    subtitle = "Preferred subtitle language",
                    trailingText = preferences.preferredSubtitleLanguage ?: "Default",
                    onClick = { showSubtitleLanguagePicker = true },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Subtitles")
            }

            item {
                SettingListItem(
                    icon = Icons.Default.TextFields,
                    title = "Font Size",
                    subtitle = "Subtitle text size",
                    trailingText = "${preferences.subtitleStyle.fontSize}sp",
                    onClick = { showSubtitleFontPicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Palette,
                    title = "Text Color",
                    subtitle = "Subtitle font color",
                    trailingText = preferences.subtitleStyle.fontColor.name,
                    onClick = { showSubtitleColorPicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.ClosedCaption,
                    title = "Background",
                    subtitle = "${preferences.subtitleStyle.backgroundColor.name} \u2022 ${(preferences.subtitleStyle.backgroundOpacity * 100).toInt()}%",
                    onClick = { showSubtitleBgColorPicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.TextFields,
                    title = "Edge Style",
                    subtitle = "Text outline effect",
                    trailingText = preferences.subtitleStyle.edgeType.name,
                    onClick = { showSubtitleEdgePicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Timer,
                    title = "Sync Offset",
                    subtitle = "Subtitle timing adjustment",
                    trailingText = "${preferences.subtitleStyle.offsetMs}ms",
                    onClick = { showSubtitleOffsetPicker = true },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.ClosedCaption,
                    title = "Position",
                    subtitle = "Vertical placement on screen",
                    trailingText = "${(preferences.subtitleStyle.verticalPosition * 100).toInt()}%",
                    onClick = { showSubtitlePositionPicker = true },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Storage")
            }

            item {
                SettingInfoItem(
                    icon = Icons.Default.Storage,
                    title = "Cache Used",
                    subtitle = "${viewModel.cacheSizeMb} MB",
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Delete,
                    title = "Clear Cache",
                    subtitle = "Free up storage space",
                    onClick = { viewModel.clearCache() },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.Cached,
                    title = "Auto-delete Cache",
                    subtitle = if (preferences.autoDeleteCache) "Automatically clears on low storage" else "Manual cache management",
                    checked = preferences.autoDeleteCache,
                    onCheckedChange = { viewModel.setAutoDeleteCache(it) },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Storage,
                    title = "Max Cache Size",
                    subtitle = "Maximum disk space for caching",
                    trailingText = "${preferences.maxCacheSizeMb} MB",
                    onClick = {
                        val sizes = listOf(250, 500, 1000, 2000, 5000)
                        val currentIndex = sizes.indexOf(preferences.maxCacheSizeMb)
                        val nextIndex = (currentIndex + 1) % sizes.size
                        viewModel.setMaxCacheSize(sizes[nextIndex])
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Appearance")
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.OndemandVideo,
                    title = "Dynamic Theming",
                    subtitle = "Colors extracted from artwork",
                    checked = preferences.dynamicTheming,
                    onCheckedChange = { viewModel.setDynamicTheming(it) },
                )
            }

            item {
                SettingListItem(
                    icon = Icons.Default.Home,
                    title = "Home Mode",
                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) "Video-focused home screen" else "Music-focused home screen",
                    trailingText = preferences.homeMode.name,
                    onClick = {
                        val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                        viewModel.setHomeMode(next)
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "Security")
            }

            item {
                SettingToggleItem(
                    icon = if (preferences.pinLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                    title = "PIN Lock",
                    subtitle = if (preferences.pinLockEnabled) "App locked with PIN" else "No PIN set",
                    checked = preferences.pinLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) showPinDialog = true
                        else viewModel.clearPin()
                    },
                    onClick = {
                        if (preferences.pinLockEnabled) viewModel.clearPin()
                        else showPinDialog = true
                    },
                )
            }

            item {
                SettingToggleItem(
                    icon = Icons.Default.ChildCare,
                    title = "Kids Mode",
                    subtitle = if (preferences.kidsModeEnabled) "Max rating: ${preferences.kidsModeMaxRating}" else "Restrict content by rating",
                    checked = preferences.kidsModeEnabled,
                    onCheckedChange = { viewModel.setKidsModeEnabled(it) },
                    onClick = {
                        val ratings = listOf("G", "PG", "PG-13", "TV-Y", "TV-Y7", "TV-G", "TV-PG")
                        val nextRating = ratings[(ratings.indexOf(preferences.kidsModeMaxRating) + 1) % ratings.size]
                        viewModel.setKidsModeMaxRating(nextRating)
                    },
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(title = "About")
            }

            item {
                SettingInfoItem(
                    icon = Icons.Default.OndemandVideo,
                    title = "Version",
                    subtitle = "JellyPlay v${viewModel.appVersion}",
                )
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
                        enter = expandVertically(),
                        exit = shrinkVertically(),
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

    if (showPlayerPicker) {
        RadioPickerDialog(
            title = "Preferred Player",
            options = PlayerType.entries.map { RadioOption(it.displayName, it.description, it == preferences.preferredPlayer) },
            onDismiss = { showPlayerPicker = false },
            onSelect = { index ->
                viewModel.setPreferredPlayer(PlayerType.entries[index])
                showPlayerPicker = false
            },
        )
    }

    if (showOrientationPicker) {
        RadioPickerDialog(
            title = "Default Orientation",
            options = OrientationMode.entries.map { RadioOption(it.displayName, it.constant, it == preferences.videoDefaultOrientation) },
            onDismiss = { showOrientationPicker = false },
            onSelect = { index ->
                viewModel.setVideoDefaultOrientation(OrientationMode.entries[index])
                showOrientationPicker = false
            },
        )
    }

    if (showAspectRatioPicker) {
        val aspectRatios = listOf("AUTO", "FIT", "FILL", "CROP", "16:9", "4:3", "21:9")
        RadioPickerDialog(
            title = "Default Aspect Ratio",
            options = aspectRatios.map { RadioOption(it, "", it == preferences.videoDefaultAspectRatio) },
            onDismiss = { showAspectRatioPicker = false },
            onSelect = { index ->
                viewModel.setVideoDefaultAspectRatio(aspectRatios[index])
                showAspectRatioPicker = false
            },
        )
    }

    if (showVideoSpeedPicker) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        RadioPickerDialog(
            title = "Default Video Speed",
            options = speeds.map { RadioOption(if (it == 1.0f) "1x (Normal)" else "${it}x", "", it == preferences.videoDefaultSpeed) },
            onDismiss = { showVideoSpeedPicker = false },
            onSelect = { index ->
                viewModel.setVideoDefaultSpeed(speeds[index])
                showVideoSpeedPicker = false
            },
        )
    }

    if (showAudioSpeedPicker) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        RadioPickerDialog(
            title = "Default Audio Speed",
            options = speeds.map { RadioOption(if (it == 1.0f) "1x (Normal)" else "${it}x", "", it == preferences.audioDefaultSpeed) },
            onDismiss = { showAudioSpeedPicker = false },
            onSelect = { index ->
                viewModel.setAudioDefaultSpeed(speeds[index])
                showAudioSpeedPicker = false
            },
        )
    }

    if (showVideoSeekDurationPicker) {
        val durations = listOf(5_000L, 10_000L, 15_000L, 20_000L, 30_000L, 60_000L)
        RadioPickerDialog(
            title = "Double-Tap Seek Duration",
            options = durations.map { RadioOption("${it / 1000}s", "", it == preferences.videoSeekDurationMs) },
            onDismiss = { showVideoSeekDurationPicker = false },
            onSelect = { index ->
                viewModel.setVideoSeekDurationMs(durations[index])
                showVideoSeekDurationPicker = false
            },
        )
    }

    if (showControlsTimeoutPicker) {
        val timeouts = listOf(3_000L, 5_000L, 10_000L, 15_000L, 20_000L, 30_000L)
        RadioPickerDialog(
            title = "Controls Auto-Hide Timeout",
            options = timeouts.map { RadioOption("${it / 1000}s", "", it == preferences.videoControlsTimeoutMs) },
            onDismiss = { showControlsTimeoutPicker = false },
            onSelect = { index ->
                viewModel.setVideoControlsTimeoutMs(timeouts[index])
                showControlsTimeoutPicker = false
            },
        )
    }

    if (showSwipeSeekPicker) {
        val ranges = listOf(30_000L, 60_000L, 90_000L, 120_000L, 180_000L, 300_000L)
        RadioPickerDialog(
            title = "Swipe Seek Maximum Range",
            options = ranges.map { RadioOption("${it / 1000}s", "", it == preferences.videoSwipeSeekMaxMs) },
            onDismiss = { showSwipeSeekPicker = false },
            onSelect = { index ->
                viewModel.setVideoSwipeSeekMaxMs(ranges[index])
                showSwipeSeekPicker = false
            },
        )
    }

    if (showNightModeVolumePicker) {
        var sliderValue by remember { mutableStateOf(preferences.audioNightModeVolume) }
        AlertDialog(
            onDismissRequest = { showNightModeVolumePicker = false },
            title = { Text("Night Mode Volume") },
            text = {
                Column {
                    Text(
                        "${(sliderValue * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0.1f..0.8f,
                        steps = 6,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("10%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("80%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAudioNightModeVolume(sliderValue)
                    showNightModeVolumePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showNightModeVolumePicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showNightModeGainPicker) {
        var sliderValue by remember { mutableStateOf(preferences.audioNightModeGain.toFloat()) }
        AlertDialog(
            onDismissRequest = { showNightModeGainPicker = false },
            title = { Text("Night Mode Loudness Gain") },
            text = {
                Column {
                    Text(
                        "${sliderValue.toInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..3000f,
                        steps = 29,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("3000", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAudioNightModeGain(sliderValue.toInt())
                    showNightModeGainPicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showNightModeGainPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showSkipPrevThresholdPicker) {
        val thresholds = listOf(1_000L, 2_000L, 3_000L, 5_000L, 7_000L, 10_000L)
        RadioPickerDialog(
            title = "Skip Previous Threshold",
            options = thresholds.map { RadioOption("${it / 1000}s", "Restarts if past this point", it == preferences.audioSkipPreviousThresholdMs) },
            onDismiss = { showSkipPrevThresholdPicker = false },
            onSelect = { index ->
                viewModel.setAudioSkipPreviousThresholdMs(thresholds[index])
                showSkipPrevThresholdPicker = false
            },
        )
    }

    if (showAudioDelayPicker) {
        var sliderValue by remember { mutableStateOf(preferences.audioDelayMs.toFloat()) }
        AlertDialog(
            onDismissRequest = { showAudioDelayPicker = false },
            title = { Text("Audio Delay") },
            text = {
                Column {
                    Text(
                        if (sliderValue.toLong() == 0L) "No delay" else "${sliderValue.toLong()}ms",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = -500f..500f,
                        steps = 99,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("-500ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+500ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAudioDelayMs(sliderValue.toLong())
                    showAudioDelayPicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showAudioDelayPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showSyncPlayMinDelayPicker) {
        val values = listOf(30L, 50L, 60L, 100L, 150L, 200L, 300L, 500L)
        RadioPickerDialog(
            title = "Speed Correction Min Delay",
            options = values.map { RadioOption("${it}ms", "", it == preferences.syncPlaySpeedToSyncMinDelayMs) },
            onDismiss = { showSyncPlayMinDelayPicker = false },
            onSelect = { index ->
                viewModel.setSyncPlaySpeedToSyncMinDelayMs(values[index])
                showSyncPlayMinDelayPicker = false
            },
        )
    }

    if (showSyncPlayMaxDelayPicker) {
        val values = listOf(1000L, 2000L, 3000L, 5000L, 10000L)
        RadioPickerDialog(
            title = "Speed Correction Max Delay",
            options = values.map { RadioOption("${it}ms", "", it == preferences.syncPlaySpeedToSyncMaxDelayMs) },
            onDismiss = { showSyncPlayMaxDelayPicker = false },
            onSelect = { index ->
                viewModel.setSyncPlaySpeedToSyncMaxDelayMs(values[index])
                showSyncPlayMaxDelayPicker = false
            },
        )
    }

    if (showSyncPlayDurationPicker) {
        val values = listOf(300L, 500L, 1000L, 1500L, 2000L, 3000L)
        RadioPickerDialog(
            title = "Speed Correction Duration",
            options = values.map { RadioOption("${it}ms", "", it == preferences.syncPlaySpeedToSyncDurationMs) },
            onDismiss = { showSyncPlayDurationPicker = false },
            onSelect = { index ->
                viewModel.setSyncPlaySpeedToSyncDurationMs(values[index])
                showSyncPlayDurationPicker = false
            },
        )
    }

    if (showSubtitleFontPicker) {
        val sizes = listOf(14, 18, 22, 24, 28, 32, 36, 40)
        RadioPickerDialog(
            title = "Subtitle Font Size",
            options = sizes.map { RadioOption("${it}sp", "", it == preferences.subtitleStyle.fontSize) },
            onDismiss = { showSubtitleFontPicker = false },
            onSelect = { index ->
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontSize = sizes[index]))
                showSubtitleFontPicker = false
            },
        )
    }

    if (showSubtitleColorPicker) {
        RadioPickerDialog(
            title = "Subtitle Text Color",
            options = SubtitleColor.entries.map { RadioOption(it.name, "", it == preferences.subtitleStyle.fontColor) },
            onDismiss = { showSubtitleColorPicker = false },
            onSelect = { index ->
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontColor = SubtitleColor.entries[index]))
                showSubtitleColorPicker = false
            },
        )
    }

    if (showSubtitleBgColorPicker) {
        var bgOpacity by remember { mutableStateOf(preferences.subtitleStyle.backgroundOpacity) }
        AlertDialog(
            onDismissRequest = { showSubtitleBgColorPicker = false },
            title = { Text("Subtitle Background") },
            text = {
                Column {
                    val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.4f
                    LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                        items(SubtitleColor.entries.size, contentType = { "subtitleColor" }) { index ->
                            val color = SubtitleColor.entries[index]
                            val isSelected = color == preferences.subtitleStyle.backgroundColor
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(backgroundColor = color))
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = isSelected, onClick = {
                                    viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(backgroundColor = color))
                                })
                                Spacer(Modifier.size(12.dp))
                                Text(color.name, style = MaterialTheme.typography.bodyLarge)
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
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(backgroundOpacity = bgOpacity))
                    showSubtitleBgColorPicker = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleBgColorPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showSubtitleEdgePicker) {
        RadioPickerDialog(
            title = "Subtitle Edge Style",
            options = SubtitleEdgeType.entries.map { RadioOption(it.name, "", it == preferences.subtitleStyle.edgeType) },
            onDismiss = { showSubtitleEdgePicker = false },
            onSelect = { index ->
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(edgeType = SubtitleEdgeType.entries[index]))
                showSubtitleEdgePicker = false
            },
        )
    }

    if (showSubtitleOffsetPicker) {
        var sliderValue by remember { mutableStateOf(preferences.subtitleStyle.offsetMs.toFloat()) }
        AlertDialog(
            onDismissRequest = { showSubtitleOffsetPicker = false },
            title = { Text("Subtitle Sync Offset") },
            text = {
                Column {
                    Text(
                        "${sliderValue.toLong()}ms",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = -5000f..5000f,
                        steps = 99,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("-5s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+5s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(offsetMs = sliderValue.toLong()))
                    showSubtitleOffsetPicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleOffsetPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showSubtitlePositionPicker) {
        var sliderValue by remember { mutableStateOf(preferences.subtitleStyle.verticalPosition) }
        AlertDialog(
            onDismissRequest = { showSubtitlePositionPicker = false },
            title = { Text("Subtitle Vertical Position") },
            text = {
                Column {
                    Text(
                        "${(sliderValue * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..0.4f,
                        steps = 7,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Bottom", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("40%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(verticalPosition = sliderValue))
                    showSubtitlePositionPicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitlePositionPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showEqualizerEditor) {
        val bandLevels = preferences.equalizerSettings.bandLevels.toMutableStateList()
        AlertDialog(
            onDismissRequest = { showEqualizerEditor = false },
            title = { Text("Equalizer") },
            text = {
                val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
                LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                    items(EqualizerSettings.BAND_FREQUENCIES.size, contentType = { "equalizerBand" }) { i ->
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
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEqualizerSettings(EqualizerSettings(bandLevels.toList()))
                    showEqualizerEditor = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showEqualizerEditor = false }) { Text("Cancel") }
            },
        )
    }

    if (showAudioLanguagePicker) {
        LanguagePickerDialog(
            currentLanguage = preferences.preferredAudioLanguage,
            onDismiss = { showAudioLanguagePicker = false },
            onSelect = { language ->
                viewModel.setPreferredAudioLanguage(language)
                showAudioLanguagePicker = false
            },
        )
    }

    if (showSubtitleLanguagePicker) {
        LanguagePickerDialog(
            currentLanguage = preferences.preferredSubtitleLanguage,
            onDismiss = { showSubtitleLanguagePicker = false },
            onSelect = { language ->
                viewModel.setPreferredSubtitleLanguage(language)
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
            .clip(MaterialTheme.shapes.large)
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
                Icons.Default.Person,
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
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "settingItemScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(100),
        label = "settingItemAlpha",
    )

    val headlineColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val supportingColor = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(if (isTvDevice()) Modifier.tvFocusable() else Modifier)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = headlineColor,
                )
            },
            supportingContent = subtitle.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor,
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingText?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "toggleItemScale",
    )
    val rowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(100),
        label = "toggleItemAlpha",
    )
    val iconColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "toggleIconColor",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = rowAlpha }
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick?.invoke() ?: onCheckedChange(!checked) }
            .then(if (isTvDevice()) Modifier.tvFocusable() else Modifier)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            },
            supportingContent = subtitle.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SettingInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun AnimatedSettingsEntrance(
    index: Int,
    visibleCount: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = index < visibleCount,
        enter = fadeIn(
            animationSpec = tween(250, delayMillis = (index % 8) * 30)
        ) + slideInVertically(
            initialOffsetY = { it / 12 },
            animationSpec = tween(250, delayMillis = (index % 8) * 30, easing = FastOutSlowInEasing),
        ),
    ) {
        content()
    }
}

private data class RadioOption(
    val title: String,
    val subtitle: String,
    val isSelected: Boolean,
)

@Composable
private fun RadioPickerDialog(
    title: String,
    options: List<RadioOption>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
            LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                items(options.size, contentType = { "option" }) { index ->
                    val option = options[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.isSelected, onClick = { onSelect(index) })
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            if (option.subtitle.isNotBlank()) {
                                Text(
                                    option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LanguagePickerDialog(
    currentLanguage: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Language", fontWeight = FontWeight.SemiBold) },
        text = {
            val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
            LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                items(languages, contentType = { "language" }) { (code, name) ->
                    val isSelected = code == currentLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(code) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelect(code) })
                        Spacer(Modifier.size(12.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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

private val languages = listOf(
    null to "Default",
    "aar" to "Afar",
    "abk" to "Abkhazian",
    "afr" to "Afrikaans",
    "aka" to "Akan",
    "alb" to "Albanian",
    "amh" to "Amharic",
    "ara" to "Arabic",
    "arg" to "Aragonese",
    "arm" to "Armenian",
    "asm" to "Assamese",
    "ava" to "Avaric",
    "ave" to "Avestan",
    "aym" to "Aymara",
    "aze" to "Azerbaijani",
    "bak" to "Bashkir",
    "bam" to "Bambara",
    "baq" to "Basque",
    "bel" to "Belarusian",
    "ben" to "Bengali",
    "bih" to "Bihari",
    "bis" to "Bislama",
    "bos" to "Bosnian",
    "bre" to "Breton",
    "bul" to "Bulgarian",
    "bur" to "Burmese",
    "cat" to "Catalan",
    "cha" to "Chamorro",
    "che" to "Chechen",
    "chi" to "Chinese",
    "chu" to "Church Slavic",
    "chv" to "Chuvash",
    "cor" to "Cornish",
    "cos" to "Corsican",
    "cre" to "Cree",
    "cze" to "Czech",
    "dan" to "Danish",
    "div" to "Divehi",
    "dut" to "Dutch",
    "dzo" to "Dzongkha",
    "eng" to "English",
    "epo" to "Esperanto",
    "est" to "Estonian",
    "ewe" to "Ewe",
    "fao" to "Faroese",
    "fij" to "Fijian",
    "fin" to "Finnish",
    "fre" to "French",
    "fry" to "Western Frisian",
    "ful" to "Fulah",
    "geo" to "Georgian",
    "ger" to "German",
    "gla" to "Scottish Gaelic",
    "gle" to "Irish",
    "glg" to "Galician",
    "glv" to "Manx",
    "grn" to "Guarani",
    "guj" to "Gujarati",
    "hat" to "Haitian",
    "hau" to "Hausa",
    "heb" to "Hebrew",
    "her" to "Herero",
    "hin" to "Hindi",
    "hmo" to "Hiri Motu",
    "hrv" to "Croatian",
    "hun" to "Hungarian",
    "ibo" to "Igbo",
    "ice" to "Icelandic",
    "ido" to "Ido",
    "iii" to "Sichuan Yi",
    "iku" to "Inuktitut",
    "ile" to "Interlingue",
    "ina" to "Interlingua",
    "ind" to "Indonesian",
    "ipk" to "Inupiaq",
    "ita" to "Italian",
    "jav" to "Javanese",
    "jpn" to "Japanese",
    "kal" to "Kalaallisut",
    "kan" to "Kannada",
    "kas" to "Kashmiri",
    "kau" to "Kanuri",
    "kaz" to "Kazakh",
    "khm" to "Central Khmer",
    "kik" to "Kikuyu",
    "kin" to "Kinyarwanda",
    "kir" to "Kirghiz",
    "kom" to "Komi",
    "kon" to "Kongo",
    "kor" to "Korean",
    "kua" to "Kuanyama",
    "kur" to "Kurdish",
    "lao" to "Lao",
    "lat" to "Latin",
    "lav" to "Latvian",
    "lim" to "Limburgan",
    "lin" to "Lingala",
    "lit" to "Lithuanian",
    "ltz" to "Luxembourgish",
    "lub" to "Luba-Katanga",
    "lug" to "Ganda",
    "mac" to "Macedonian",
    "mah" to "Marshallese",
    "mal" to "Malayalam",
    "mao" to "Maori",
    "mar" to "Marathi",
    "may" to "Malay",
    "mlg" to "Malagasy",
    "mlt" to "Maltese",
    "mon" to "Mongolian",
    "nau" to "Nauru",
    "nav" to "Navajo",
    "nbl" to "South Ndebele",
    "nde" to "North Ndebele",
    "ndo" to "Ndonga",
    "nep" to "Nepali",
    "nno" to "Norwegian Nynorsk",
    "nob" to "Norwegian Bokm\u00e5l",
    "nor" to "Norwegian",
    "nya" to "Chichewa",
    "oci" to "Occitan",
    "oji" to "Ojibwa",
    "ori" to "Oriya",
    "orm" to "Oromo",
    "oss" to "Ossetian",
    "pan" to "Panjabi",
    "per" to "Persian",
    "pli" to "Pali",
    "pol" to "Polish",
    "por" to "Portuguese",
    "pus" to "Pushto",
    "que" to "Quechua",
    "roh" to "Romansh",
    "rum" to "Romanian",
    "run" to "Rundi",
    "rus" to "Russian",
    "sag" to "Sango",
    "san" to "Sanskrit",
    "sin" to "Sinhala",
    "slo" to "Slovak",
    "slv" to "Slovenian",
    "sme" to "Northern Sami",
    "smo" to "Samoan",
    "sna" to "Shona",
    "snd" to "Sindhi",
    "som" to "Somali",
    "sot" to "Southern Sotho",
    "spa" to "Spanish",
    "srd" to "Sardinian",
    "srp" to "Serbian",
    "ssw" to "Swati",
    "sun" to "Sundanese",
    "swa" to "Swahili",
    "swe" to "Swedish",
    "tah" to "Tahitian",
    "tam" to "Tamil",
    "tat" to "Tatar",
    "tel" to "Telugu",
    "tgk" to "Tajik",
    "tgl" to "Tagalog",
    "tha" to "Thai",
    "tib" to "Tibetan",
    "tir" to "Tigrinya",
    "ton" to "Tonga",
    "tsn" to "Tswana",
    "tso" to "Tsonga",
    "tuk" to "Turkmen",
    "tur" to "Turkish",
    "twi" to "Twi",
    "uig" to "Uighur",
    "ukr" to "Ukrainian",
    "urd" to "Urdu",
    "uzb" to "Uzbek",
    "ven" to "Venda",
    "vie" to "Vietnamese",
    "vol" to "Volap\u00fck",
    "wel" to "Welsh",
    "wln" to "Walloon",
    "wol" to "Wolof",
    "xho" to "Xhosa",
    "yid" to "Yiddish",
    "yor" to "Yoruba",
    "zha" to "Zhuang",
    "zul" to "Zulu",
)
