package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType

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
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (userName.isNotBlank()) {
                SettingsHeroBanner(userName = userName)
                Spacer(Modifier.height(8.dp))
            }

            SettingsCategoryRow(
                title = "Account",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.Dns,
                        title = "Servers",
                        subtitle = "Manage servers",
                        onClick = onServerManagement,
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Person,
                        title = "Switch User",
                        subtitle = userName.ifBlank { "Manage users" },
                        onClick = onUserManagement,
                    ),
                    SettingsCardData(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Sign Out",
                        subtitle = "Log out of current account",
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        },
                        isDestructive = true,
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "Video Player",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.PlayCircle,
                        title = "Player Engine",
                        subtitle = preferences.preferredPlayer.displayName,
                        valueBadge = preferences.preferredPlayer.displayName,
                        onClick = { showPlayerPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.FastForward,
                        title = "Seek Duration",
                        subtitle = "${preferences.videoSeekDurationMs / 1000}s double-tap",
                        valueBadge = "${preferences.videoSeekDurationMs / 1000}s",
                        onClick = { showVideoSeekDurationPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.ScreenLockLandscape,
                        title = "Orientation",
                        subtitle = preferences.videoDefaultOrientation.displayName,
                        valueBadge = preferences.videoDefaultOrientation.displayName,
                        onClick = { showOrientationPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Timer,
                        title = "Controls Timeout",
                        subtitle = "${preferences.videoControlsTimeoutMs / 1000}s auto-hide",
                        valueBadge = "${preferences.videoControlsTimeoutMs / 1000}s",
                        onClick = { showControlsTimeoutPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Gesture,
                        title = "Gestures",
                        subtitle = if (preferences.videoGesturesEnabled) "Swipe & tap active" else "Disabled",
                        isToggle = true,
                        toggled = preferences.videoGesturesEnabled,
                        onToggle = { viewModel.setVideoGesturesEnabled(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Speed,
                        title = "Default Speed",
                        subtitle = if (preferences.videoDefaultSpeed == 1.0f) "Normal" else "${preferences.videoDefaultSpeed}x",
                        valueBadge = if (preferences.videoDefaultSpeed == 1.0f) "1x" else "${preferences.videoDefaultSpeed}x",
                        onClick = { showVideoSpeedPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.AspectRatio,
                        title = "Default Aspect",
                        subtitle = preferences.videoDefaultAspectRatio,
                        valueBadge = preferences.videoDefaultAspectRatio,
                        onClick = { showAspectRatioPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.QueuePlayNext,
                        title = "Auto-play Next",
                        subtitle = if (preferences.videoAutoplayNext) "Plays next episode" else "Disabled",
                        isToggle = true,
                        toggled = preferences.videoAutoplayNext,
                        onToggle = { viewModel.setVideoAutoplayNext(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Swipe,
                        title = "Swipe Seek Range",
                        subtitle = "Max ${preferences.videoSwipeSeekMaxMs / 1000}s",
                        valueBadge = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                        onClick = { showSwipeSeekPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Brightness6,
                        title = "Remember Brightness",
                        subtitle = if (preferences.videoRememberBrightness) "Saves between sessions" else "Disabled",
                        isToggle = true,
                        toggled = preferences.videoRememberBrightness,
                        onToggle = { viewModel.setVideoRememberBrightness(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Dialogue Boost",
                        subtitle = if (preferences.dialogueBoostEnabled) "Enhanced vocals" else "Off",
                        isToggle = true,
                        toggled = preferences.dialogueBoostEnabled,
                        onToggle = { viewModel.setDialogueBoostEnabled(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.HighQuality,
                        title = "Decoder",
                        subtitle = preferences.decoderMode.displayName,
                        valueBadge = preferences.decoderMode.displayName.split(" ").first(),
                        onClick = {
                            val modes = DecoderMode.entries
                            val currentIndex = modes.indexOf(preferences.decoderMode)
                            val nextIndex = (currentIndex + 1) % modes.size
                            viewModel.setDecoderMode(modes[nextIndex])
                        },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Movie,
                        title = "Audio Passthrough",
                        subtitle = if (preferences.audioPassthrough) "Direct to receiver" else "Off",
                        isToggle = true,
                        toggled = preferences.audioPassthrough,
                        onToggle = { viewModel.setAudioPassthrough(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Fullscreen,
                        title = "Frame Rate Match",
                        subtitle = if (preferences.frameRateMatching) "Matches display" else "Off",
                        isToggle = true,
                        toggled = preferences.frameRateMatching,
                        onToggle = { viewModel.setFrameRateMatching(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.HighQuality,
                        title = "Streaming Quality",
                        subtitle = streamingQualityLabel(preferences.streamingQuality),
                        valueBadge = streamingQualityShort(preferences.streamingQuality),
                        onClick = {
                            val qualities = StreamingQuality.entries
                            val currentIndex = qualities.indexOf(preferences.streamingQuality)
                            val nextIndex = (currentIndex + 1) % qualities.size
                            viewModel.setStreamingQuality(qualities[nextIndex])
                        },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Audiotrack,
                        title = "Audio Delay",
                        subtitle = if (preferences.audioDelayMs == 0L) "None" else "${preferences.audioDelayMs}ms",
                        valueBadge = "${preferences.audioDelayMs}ms",
                        onClick = { showAudioDelayPicker = true },
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "Audio Player",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.Speed,
                        title = "Default Speed",
                        subtitle = if (preferences.audioDefaultSpeed == 1.0f) "Normal" else "${preferences.audioDefaultSpeed}x",
                        valueBadge = if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x",
                        onClick = { showAudioSpeedPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.LibraryMusic,
                        title = "Night Mode Volume",
                        subtitle = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                        valueBadge = "${(preferences.audioNightModeVolume * 100).toInt()}%",
                        onClick = { showNightModeVolumePicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Tune,
                        title = "Night Mode Gain",
                        subtitle = "${preferences.audioNightModeGain}",
                        valueBadge = "${preferences.audioNightModeGain}",
                        onClick = { showNightModeGainPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.SkipNext,
                        title = "Skip Prev Threshold",
                        subtitle = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                        valueBadge = "${preferences.audioSkipPreviousThresholdMs / 1000}s",
                        onClick = { showSkipPrevThresholdPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.QueuePlayNext,
                        title = "Auto-play Next",
                        subtitle = if (preferences.audioAutoplayNext) "Automatic" else "Manual",
                        isToggle = true,
                        toggled = preferences.audioAutoplayNext,
                        onToggle = { viewModel.setAudioAutoplayNext(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Tune,
                        title = "Equalizer",
                        subtitle = if (preferences.equalizerEnabled) "10-band active" else "Off",
                        isToggle = true,
                        toggled = preferences.equalizerEnabled,
                        onToggle = { viewModel.setEqualizerEnabled(it) },
                        onClick = { showEqualizerEditor = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Dialogue Boost",
                        subtitle = if (preferences.dialogueBoostEnabled) "Enhanced vocals" else "Off",
                        isToggle = true,
                        toggled = preferences.dialogueBoostEnabled,
                        onToggle = { viewModel.setDialogueBoostEnabled(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Speed,
                        title = "Night Mode",
                        subtitle = if (preferences.nightModeEnabled) "Compression active" else "Off",
                        isToggle = true,
                        toggled = preferences.nightModeEnabled,
                        onToggle = { viewModel.setNightModeEnabled(it) },
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "Language",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.Language,
                        title = "Audio Language",
                        subtitle = preferences.preferredAudioLanguage ?: "Default",
                        onClick = { showAudioLanguagePicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.ClosedCaption,
                        title = "Subtitle Language",
                        subtitle = preferences.preferredSubtitleLanguage ?: "Default",
                        onClick = { showSubtitleLanguagePicker = true },
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "Subtitles",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.TextFields,
                        title = "Font Size",
                        subtitle = "${preferences.subtitleStyle.fontSize}sp",
                        valueBadge = "${preferences.subtitleStyle.fontSize}",
                        onClick = { showSubtitleFontPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Palette,
                        title = "Text Color",
                        subtitle = preferences.subtitleStyle.fontColor.name,
                        valueBadge = preferences.subtitleStyle.fontColor.name,
                        onClick = { showSubtitleColorPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.ClosedCaption,
                        title = "Background",
                        subtitle = "${preferences.subtitleStyle.backgroundColor.name} ${(preferences.subtitleStyle.backgroundOpacity * 100).toInt()}%",
                        onClick = { showSubtitleBgColorPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.TextFields,
                        title = "Edge Style",
                        subtitle = preferences.subtitleStyle.edgeType.name,
                        valueBadge = preferences.subtitleStyle.edgeType.name,
                        onClick = { showSubtitleEdgePicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Timer,
                        title = "Sync Offset",
                        subtitle = "${preferences.subtitleStyle.offsetMs}ms",
                        valueBadge = "${preferences.subtitleStyle.offsetMs}ms",
                        onClick = { showSubtitleOffsetPicker = true },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.ClosedCaption,
                        title = "Position",
                        subtitle = "${(preferences.subtitleStyle.verticalPosition * 100).toInt()}%",
                        valueBadge = "${(preferences.subtitleStyle.verticalPosition * 100).toInt()}%",
                        onClick = { showSubtitlePositionPicker = true },
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "Storage",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.Storage,
                        title = "Cache Used",
                        subtitle = "${viewModel.cacheSizeMb} MB",
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Delete,
                        title = "Clear Cache",
                        subtitle = "Free up space",
                        onClick = { viewModel.clearCache() },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Cached,
                        title = "Auto-delete",
                        subtitle = if (preferences.autoDeleteCache) "On low storage" else "Off",
                        isToggle = true,
                        toggled = preferences.autoDeleteCache,
                        onToggle = { viewModel.setAutoDeleteCache(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Storage,
                        title = "Max Cache",
                        subtitle = "${preferences.maxCacheSizeMb} MB",
                        valueBadge = "${preferences.maxCacheSizeMb} MB",
                        onClick = {
                            val sizes = listOf(250, 500, 1000, 2000, 5000)
                            val currentIndex = sizes.indexOf(preferences.maxCacheSizeMb)
                            val nextIndex = (currentIndex + 1) % sizes.size
                            viewModel.setMaxCacheSize(sizes[nextIndex])
                        },
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "Appearance",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.OndemandVideo,
                        title = "Dynamic Theming",
                        subtitle = "Colors from artwork",
                        isToggle = true,
                        toggled = preferences.dynamicTheming,
                        onToggle = { viewModel.setDynamicTheming(it) },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.Home,
                        title = "Home Mode",
                        subtitle = if (preferences.homeMode == HomeMode.VIDEO) "Video focus" else "Music focus",
                        valueBadge = preferences.homeMode.name,
                        onClick = {
                            val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                            viewModel.setHomeMode(next)
                        },
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "Security",
                cards = listOf(
                    SettingsCardData(
                        icon = if (preferences.pinLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                        title = "PIN Lock",
                        subtitle = if (preferences.pinLockEnabled) "Enabled" else "Disabled",
                        isToggle = true,
                        toggled = preferences.pinLockEnabled,
                        onToggle = { enabled ->
                            if (enabled) showPinDialog = true
                            else viewModel.clearPin()
                        },
                        onClick = {
                            if (preferences.pinLockEnabled) viewModel.clearPin()
                            else showPinDialog = true
                        },
                    ),
                    SettingsCardData(
                        icon = Icons.Default.ChildCare,
                        title = "Kids Mode",
                        subtitle = if (preferences.kidsModeEnabled) "Max: ${preferences.kidsModeMaxRating}" else "Disabled",
                        isToggle = true,
                        toggled = preferences.kidsModeEnabled,
                        onToggle = { viewModel.setKidsModeEnabled(it) },
                        onClick = {
                            val ratings = listOf("G", "PG", "PG-13", "TV-Y", "TV-Y7", "TV-G", "TV-PG")
                            val nextRating = ratings[(ratings.indexOf(preferences.kidsModeMaxRating) + 1) % ratings.size]
                            viewModel.setKidsModeMaxRating(nextRating)
                        },
                    ),
                ),
            )

            SettingsCategoryRow(
                title = "About",
                cards = listOf(
                    SettingsCardData(
                        icon = Icons.Default.OndemandVideo,
                        title = "Version",
                        subtitle = "JellyPlay v1.0.0",
                    ),
                ),
            )

            Spacer(Modifier.height(32.dp))
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
            title = { Text("Set PIN") },
            text = {
                Column {
                    TextField(
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
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
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
                    )
                    if (pinError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                }) { Text("Set") }
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
                    Text("${(sliderValue * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0.1f..0.8f,
                        steps = 6,
                    )
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
                    Text("${sliderValue.toInt()}", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..3000f,
                        steps = 29,
                    )
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
                    val displayMs = sliderValue.toLong()
                    Text(
                        if (displayMs == 0L) "No delay" else "${displayMs}ms",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = -500f..500f,
                        steps = 99,
                    )
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
                        items(SubtitleColor.entries.size) { index ->
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
                    Text("Opacity: ${(bgOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
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
                    Text("${sliderValue.toLong()}ms", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = -5000f..5000f,
                        steps = 99,
                    )
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
                    Text("${(sliderValue * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..0.4f,
                        steps = 7,
                    )
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
                    items(EqualizerSettings.BAND_FREQUENCIES.size) { i ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${EqualizerSettings.BAND_FREQUENCIES[i]} Hz",
                                style = MaterialTheme.typography.bodySmall,
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
private fun SettingsHeroBanner(userName: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Signed in as",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

data class SettingsCardData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val valueBadge: String? = null,
    val isToggle: Boolean = false,
    val toggled: Boolean = false,
    val onToggle: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val isDestructive: Boolean = false,
)

@Composable
private fun SettingsCategoryRow(
    title: String,
    cards: List<SettingsCardData>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards) { card ->
                SettingsCard(card)
            }
        }
    }
}

@Composable
private fun SettingsCard(data: SettingsCardData) {
    val cardColor = if (data.isDestructive) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .width(160.dp)
            .then(
                if (data.onClick != null || data.isToggle) {
                    Modifier.clickable {
                        if (data.isToggle && data.onToggle != null) {
                            data.onToggle(!data.toggled)
                        }
                        data.onClick?.invoke()
                    }
                } else Modifier
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    data.icon,
                    contentDescription = null,
                    tint = if (data.isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                if (data.isToggle && data.onToggle != null) {
                    Switch(
                        checked = data.toggled,
                        onCheckedChange = data.onToggle,
                        modifier = Modifier.height(24.dp),
                    )
                } else if (data.valueBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            data.valueBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                data.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (data.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                data.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
        }
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
        title = { Text(title) },
        text = {
            val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
            LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                items(options.size) { index ->
                    val option = options[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.isSelected, onClick = { onSelect(index) })
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.bodyLarge)
                            if (option.subtitle.isNotBlank()) {
                                Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        title = { Text("Select Language") },
        text = {
            val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
            LazyColumn(modifier = Modifier.heightIn(max = maxListHeight)) {
                items(languages) { (code, name) ->
                    val isSelected = code == currentLanguage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code) }
                            .padding(vertical = 4.dp),
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
    "nob" to "Norwegian Bokmål",
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
    "vol" to "Volapük",
    "wel" to "Welsh",
    "wln" to "Walloon",
    "wol" to "Wolof",
    "xho" to "Xhosa",
    "yid" to "Yiddish",
    "yor" to "Yoruba",
    "zha" to "Zhuang",
    "zul" to "Zulu",
)
