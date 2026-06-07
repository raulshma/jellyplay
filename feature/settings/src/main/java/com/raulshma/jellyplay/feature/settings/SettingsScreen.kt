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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ExoAudioOffloadMode
import com.raulshma.jellyplay.core.model.ExoFrameRateStrategy
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.ExoVideoScalingMode
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MpvAudioOutput
import com.raulshma.jellyplay.core.model.MpvDemuxerMaxBytes
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvFrameDrop
import com.raulshma.jellyplay.core.model.MpvHwdec
import com.raulshma.jellyplay.core.model.MpvScaler
import com.raulshma.jellyplay.core.model.MpvSkipLoopFilter
import com.raulshma.jellyplay.core.model.MpvVideoOutput
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.VlcAudioOutput
import com.raulshma.jellyplay.core.model.VlcVideoOutput
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
import com.raulshma.jellyplay.core.ui.components.findFragmentActivity
import com.raulshma.jellyplay.core.ui.components.AccentColorPicker
import com.raulshma.jellyplay.core.ui.components.ColorStylePicker

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onServerManagement: () -> Unit = {},
    onUserManagement: () -> Unit = {},
    onSeerrSettings: () -> Unit = {},
    onAdminDashboard: () -> Unit = {},
    onSetupWizard: () -> Unit = {},
    onNewsletterClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onWatchProgressHeatmapClick: () -> Unit = {},
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

    val currentServerAddress by viewModel.currentServerAddress.collectAsStateWithLifecycle()
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

    var showMpvVideoOutputPicker by remember { mutableStateOf(false) }
    var showMpvScalerPicker by remember { mutableStateOf(false) }
    var showMpvAudioOutputPicker by remember { mutableStateOf(false) }
    var showMpvAudioFallbackPicker by remember { mutableStateOf(false) }
    var showMpvDemuxerPicker by remember { mutableStateOf(false) }
    var showMpvHwdecPicker by remember { mutableStateOf(false) }
    var showMpvSkipLoopFilterPicker by remember { mutableStateOf(false) }
    var showMpvFrameDropPicker by remember { mutableStateOf(false) }

    var showVlcAudioOutputPicker by remember { mutableStateOf(false) }
    var showVlcVideoOutputPicker by remember { mutableStateOf(false) }
    var showVlcNetworkCachingPicker by remember { mutableStateOf(false) }
    var showVlcSkipLoopFilterPicker by remember { mutableStateOf(false) }
    var showVlcSkipFramePicker by remember { mutableStateOf(false) }
    var showVlcDecoderThreadsPicker by remember { mutableStateOf(false) }

    var showExoScalingPicker by remember { mutableStateOf(false) }
    var showExoFrameRatePicker by remember { mutableStateOf(false) }
    var showExoAudioOffloadPicker by remember { mutableStateOf(false) }
    var showExoBackBufferPicker by remember { mutableStateOf(false) }
    var showExoCodecPicker by remember { mutableStateOf(false) }

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

    val settingsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { viewModel.exportSettings(it) }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importSettings(it) }
    }

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = "Settings",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            androidx.compose.material3.IconButton(onClick = onNewsletterClick) {
                Icon(
                    com.composables.icons.tabler.Tabler.Outline.Mail,
                    contentDescription = "Newsletter",
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        val scrollState = rememberScrollState()
        val context = LocalContext.current

        LaunchedEffect(viewModel.messageSentEvent) {
            viewModel.messageSentEvent?.let { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessageEvent()
            }
        }

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
                if (viewModel.currentUser?.isAdmin == true && viewModel.activeSessions.isNotEmpty()) {
                    ActiveDevicesRow(
                        sessions = viewModel.activeSessions,
                        serverAddress = currentServerAddress,
                        onSendMessage = viewModel::sendMessageToSession,
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
                SettingsGroup(
                    icon = Tabler.Outline.Wand,
                    title = "Setup Wizard",
                    summary = { "Re-run the initial setup experience" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.Wand,
                        title = "Re-run Setup",
                        subtitle = "Configure your preferences again",
                        index = 0, count = 1,
                        onClick = onSetupWizard,
                    )
                }
            }

            AnimatedSettingsEntrance(3) {
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
                val notifPrefs = preferences.notificationPreferences
                var showFrequencyPicker by remember { mutableStateOf(false) }
                var showQuietStartPicker by remember { mutableStateOf(false) }
                var showQuietEndPicker by remember { mutableStateOf(false) }

                SettingsGroup(
                    icon = Tabler.Outline.Bell,
                    title = "Notifications",
                    summary = {
                        if (notifPrefs.enabled) "Checking ${notifPrefs.checkFrequency.displayName.lowercase()}"
                        else "Disabled"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingToggleItem(
                        icon = Tabler.Outline.Bell,
                        title = "Enable Notifications",
                        subtitle = "Get notified when new media is added to your server",
                        checked = notifPrefs.enabled,
                        index = 0, count = 9,
                        onCheckedChange = { enabled ->
                            viewModel.updateNotificationPreferences { it.copy(enabled = enabled) }
                        },
                    )
                    if (notifPrefs.enabled) {
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = "Check Frequency",
                            subtitle = "How often to check for new media",
                            trailingText = notifPrefs.checkFrequency.displayName,
                            index = 1, count = 9,
                            onClick = { showFrequencyPicker = true },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Moon,
                            title = "Quiet Hours",
                            subtitle = "Suppress notifications during set hours",
                            checked = notifPrefs.quietHoursEnabled,
                            index = 2, count = 9,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(quietHoursEnabled = enabled) }
                            },
                        )
                        if (notifPrefs.quietHoursEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Sunset,
                                title = "Quiet Start",
                                subtitle = "Begin quiet hours",
                                trailingText = formatMinutes(notifPrefs.quietHoursStart),
                                index = 3, count = 9,
                                onClick = { showQuietStartPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Sunrise,
                                title = "Quiet End",
                                subtitle = "End quiet hours",
                                trailingText = formatMinutes(notifPrefs.quietHoursEnd),
                                index = 4, count = 9,
                                onClick = { showQuietEndPicker = true },
                            )
                        }
                        SettingToggleItem(
                            icon = Tabler.Outline.Volume,
                            title = "Sound",
                            subtitle = "Play notification sound",
                            checked = notifPrefs.soundEnabled,
                            index = if (notifPrefs.quietHoursEnabled) 5 else 3,
                            count = 9,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(soundEnabled = enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.PhoneCall,
                            title = "Vibrate",
                            subtitle = "Vibrate on notification",
                            checked = notifPrefs.vibrateEnabled,
                            index = if (notifPrefs.quietHoursEnabled) 6 else 4,
                            count = 9,
                            onCheckedChange = { enabled ->
                                viewModel.updateNotificationPreferences { it.copy(vibrateEnabled = enabled) }
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.LetterCase,
                            title = "Max Per Check",
                            subtitle = "Maximum items per notification batch",
                            trailingText = "${notifPrefs.maxPerCheck}",
                            index = if (notifPrefs.quietHoursEnabled) 7 else 5,
                            count = 9,
                            onClick = { },
                        )
                        val libraryCount = viewModel.libraryFolders.size
                        val enabledLibraries = viewModel.libraryFolders.count { folder ->
                            notifPrefs.libraryConfigs[folder.id]?.enabled ?: true
                        }
                        SettingListItem(
                            icon = Tabler.Outline.Folders,
                            title = "Libraries",
                            subtitle = "$enabledLibraries of $libraryCount libraries monitored",
                            index = if (notifPrefs.quietHoursEnabled) 8 else 6,
                            count = 9,
                            onClick = { },
                        )
                    }
                }

                if (showFrequencyPicker) {
                    SingleChoicePicker(
                        title = "Check Frequency",
                        options = CheckFrequency.entries.map { it.displayName },
                        selectedIndex = CheckFrequency.entries.indexOf(notifPrefs.checkFrequency),
                        onSelected = { index ->
                            viewModel.updateNotificationPreferences {
                                it.copy(checkFrequency = CheckFrequency.entries[index])
                            }
                            showFrequencyPicker = false
                        },
                        onDismiss = { showFrequencyPicker = false },
                    )
                }

                if (showQuietStartPicker) {
                    TimePicker(
                        title = "Quiet Hours Start",
                        initialMinutes = notifPrefs.quietHoursStart,
                        onSelected = { minutes ->
                            viewModel.updateNotificationPreferences { it.copy(quietHoursStart = minutes) }
                            showQuietStartPicker = false
                        },
                        onDismiss = { showQuietStartPicker = false },
                    )
                }

                if (showQuietEndPicker) {
                    TimePicker(
                        title = "Quiet Hours End",
                        initialMinutes = notifPrefs.quietHoursEnd,
                        onSelected = { minutes ->
                            viewModel.updateNotificationPreferences { it.copy(quietHoursEnd = minutes) }
                            showQuietEndPicker = false
                        },
                        onDismiss = { showQuietEndPicker = false },
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
                    val homeSectionOrder = remember { mutableStateListOf<HomeSectionType>().apply { addAll(preferences.homeSectionOrder) } }
                    val itemHeights = remember { mutableStateMapOf<HomeSectionType, Int>() }
                    var draggingSection by remember { mutableStateOf<HomeSectionType?>(null) }
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }

                    LaunchedEffect(preferences.homeSectionOrder) {
                        if (draggingSection == null) {
                            homeSectionOrder.clear()
                            homeSectionOrder.addAll(preferences.homeSectionOrder)
                        }
                    }

                    fun persistHomeSectionOrder() {
                        val currentOrder = homeSectionOrder.toList()
                        if (currentOrder != preferences.homeSectionOrder) {
                            viewModel.setHomeSectionOrder(currentOrder)
                        }
                    }

                    fun moveSection(type: HomeSectionType, deltaY: Float) {
                        if (draggingSection != type) return
                        dragOffsetY += deltaY

                        while (true) {
                            val currentIndex = homeSectionOrder.indexOf(type)
                            if (currentIndex == -1) return

                            val draggedHeight = itemHeights[type] ?: return

                            if (dragOffsetY > 0f && currentIndex < homeSectionOrder.lastIndex) {
                                val nextType = homeSectionOrder[currentIndex + 1]
                                val nextHeight = itemHeights[nextType] ?: draggedHeight
                                val threshold = (draggedHeight + nextHeight) / 2f
                                if (dragOffsetY > threshold) {
                                    homeSectionOrder.removeAt(currentIndex)
                                    homeSectionOrder.add(currentIndex + 1, type)
                                    dragOffsetY -= nextHeight.toFloat()
                                    continue
                                }
                            }

                            if (dragOffsetY < 0f && currentIndex > 0) {
                                val prevType = homeSectionOrder[currentIndex - 1]
                                val prevHeight = itemHeights[prevType] ?: draggedHeight
                                val threshold = (draggedHeight + prevHeight) / 2f
                                if (-dragOffsetY > threshold) {
                                    homeSectionOrder.removeAt(currentIndex)
                                    homeSectionOrder.add(currentIndex - 1, type)
                                    dragOffsetY += prevHeight.toFloat()
                                    continue
                                }
                            }

                            break
                        }
                    }

                    val totalCount = homeSectionOrder.size + viewModel.libraryFolders.size
                    var idx = 0

                    homeSectionOrder.forEach { type ->
                        val isChecked = type in preferences.enabledHomeSectionTypes
                        val icon = when (type) {
                            HomeSectionType.CONTINUE_WATCHING -> Tabler.Outline.PlayerPlay
                            HomeSectionType.NEXT_UP -> Tabler.Outline.PlayerSkipForward
                            HomeSectionType.RECENTLY_ADDED -> Tabler.Outline.Clock
                            HomeSectionType.LATEST_MEDIA -> Tabler.Outline.LayersLinked
                            else -> Tabler.Outline.LayersLinked
                        }
                        SettingReorderableToggleItem(
                            icon = icon,
                            title = type.displayName,
                            subtitle = if (isChecked) type.description else "Hidden",
                            checked = isChecked,
                            index = idx++, count = totalCount,
                            isDragging = draggingSection == type,
                            modifier = Modifier.onSizeChanged { itemHeights[type] = it.height },
                            onCheckedChange = { viewModel.toggleHomeSectionType(type, it) },
                            onClick = { viewModel.toggleHomeSectionType(type, !isChecked) },
                            onDragStart = {
                                draggingSection = type
                                dragOffsetY = 0f
                            },
                            onDrag = { delta -> moveSection(type, delta) },
                            onDragEnd = {
                                val wasDragging = draggingSection == type
                                draggingSection = null
                                dragOffsetY = 0f
                                if (wasDragging) persistHomeSectionOrder()
                            },
                        )

                        if (type == HomeSectionType.LATEST_MEDIA) {
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
                        }
                    }

                    SettingListItem(
                        icon = Tabler.Outline.Heart,
                        title = "Browse Favorites",
                        subtitle = "View all your favorited items",
                        index = idx, count = totalCount + 2,
                        onClick = onFavoritesClick,
                    )

                    SettingListItem(
                        icon = Tabler.Outline.ChartBar,
                        title = "Watch Progress Heatmap",
                        subtitle = "View your viewing activity over the past year",
                        index = idx + 1, count = totalCount + 2,
                        onClick = onWatchProgressHeatmapClick,
                    )
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
                        add("metadata" to 9)
                        add("swipe" to 10)
                        add("brightness" to 11)
                        add("trickplay" to 12)
                        add("trickplayGesture" to 13)
                        add("preload" to 14)
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
                    SettingToggleItem(
                        icon = Tabler.Outline.InfoCircle,
                        title = "Playback Metadata",
                        subtitle = if (preferences.videoShowPlaybackMetadata) "Show play method, codecs, HDR and Atmos info above seekbar" else "Metadata display hidden",
                        checked = preferences.videoShowPlaybackMetadata,
                        index = 9, count = total,
                        onCheckedChange = { viewModel.setVideoShowPlaybackMetadata(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.HandFinger,
                        title = "Swipe Seek Range",
                        subtitle = "Maximum seek distance",
                        trailingText = "${preferences.videoSwipeSeekMaxMs / 1000}s",
                        index = 10, count = total,
                        onClick = { showSwipeSeekPicker = true },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.BrightnessHalf,
                        title = "Remember Brightness",
                        subtitle = if (preferences.videoRememberBrightness) "Brightness saved between sessions" else "Reset brightness each session",
                        checked = preferences.videoRememberBrightness,
                        index = 11, count = total,
                        onCheckedChange = { viewModel.setVideoRememberBrightness(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Video,
                        title = "Trickplay Preview",
                        subtitle = if (preferences.trickplayEnabled) "Show preview images while scrubbing" else "No preview images on seek bar",
                        checked = preferences.trickplayEnabled,
                        index = 12, count = total,
                        onCheckedChange = { viewModel.setTrickplayEnabled(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.HandClick,
                        title = "Trickplay on Gestures",
                        subtitle = if (preferences.trickplayOnSeekGesture) "Show preview on swipe seek" else "No preview on swipe gestures",
                        checked = preferences.trickplayOnSeekGesture,
                        index = 13, count = total,
                        onCheckedChange = { viewModel.setTrickplayOnSeekGesture(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Refresh,
                        title = "Preload Buffer",
                        subtitle = "Amount to buffer ahead during playback",
                        trailingText = preferences.videoPreloadBufferSize.displayName,
                        index = 14, count = total,
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
                    icon = Tabler.Outline.Settings,
                    title = "Engine Config",
                    summary = { preferences.preferredPlayer.displayName },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    when (preferences.preferredPlayer) {
                        PlayerType.MPV -> {
                            val mpvCfg = preferences.mpvConfig
                            val mpvDefault = MpvEngineConfig()
                            val mpvTotal = 11
                            var mpvIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.Video,
                                title = "Video Output",
                                subtitle = "${mpvCfg.videoOutput.displayName} (${mpvCfg.videoOutput.key})",
                                trailingText = mpvCfg.videoOutput.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvVideoOutputPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowAutofitHeight,
                                title = "Scaler",
                                subtitle = "${mpvCfg.scaler.displayName} (${mpvCfg.scaler.key})",
                                trailingText = mpvCfg.scaler.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvScalerPicker = true },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.ColorFilter,
                                title = "Debanding",
                                subtitle = if (mpvCfg.deband) "GPU debanding enabled" else "No debanding",
                                checked = mpvCfg.deband,
                                index = mpvIdx++, count = mpvTotal,
                                onCheckedChange = { viewModel.setMpvConfig(mpvCfg.copy(deband = it)) },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Clock,
                                title = "Interpolation",
                                subtitle = if (mpvCfg.interpolation) "Smooth motion enabled" else "No frame interpolation",
                                checked = mpvCfg.interpolation,
                                index = mpvIdx++, count = mpvTotal,
                                onCheckedChange = { viewModel.setMpvConfig(mpvCfg.copy(interpolation = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = "Audio Output",
                                subtitle = "${mpvCfg.audioOutput.displayName} (${mpvCfg.audioOutput.key})",
                                trailingText = mpvCfg.audioOutput.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvAudioOutputPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.ArrowBackUp,
                                title = "Audio Fallback",
                                subtitle = mpvCfg.audioFallback?.let { "${it.displayName} (${it.key})" } ?: "None",
                                trailingText = mpvCfg.audioFallback?.key ?: "None",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvAudioFallbackPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = "Buffer Size",
                                subtitle = "${mpvCfg.demuxerMaxBytes.displayName} (${mpvCfg.demuxerMaxBytes.key})",
                                trailingText = mpvCfg.demuxerMaxBytes.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvDemuxerPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.BadgeHd,
                                title = "HW Dec Override",
                                subtitle = mpvCfg.hwdecOverride?.let { "${it.displayName} (${it.key})" } ?: "Use universal setting",
                                trailingText = mpvCfg.hwdecOverride?.key ?: "Auto",
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvHwdecPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = "${mpvCfg.skipLoopFilter.displayName} (${mpvCfg.skipLoopFilter.key})",
                                trailingText = mpvCfg.skipLoopFilter.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvSkipLoopFilterPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.PlayerSkipForward,
                                title = "Frame Drop",
                                subtitle = "${mpvCfg.frameDrop.displayName} (${mpvCfg.frameDrop.key})",
                                trailingText = mpvCfg.frameDrop.key,
                                index = mpvIdx++, count = mpvTotal,
                                onClick = { showMpvFrameDropPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = "Reset to Default",
                                subtitle = if (mpvCfg == mpvDefault) "Already using defaults" else "Restore all MPV settings to default",
                                index = mpvIdx, count = mpvTotal,
                                onClick = { viewModel.setMpvConfig(MpvEngineConfig()) },
                            )
                        }
                        PlayerType.LIBVLC -> {
                            val vlcCfg = preferences.libVlcConfig
                            val vlcDefault = LibVlcEngineConfig()
                            val vlcTotal = 10
                            var vlcIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.Volume,
                                title = "Audio Output",
                                subtitle = "${vlcCfg.audioOutput.displayName} (${vlcCfg.audioOutput.key})",
                                trailingText = vlcCfg.audioOutput.key,
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { showVlcAudioOutputPicker = true },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Clock,
                                title = "Audio Time Stretch",
                                subtitle = if (vlcCfg.audioTimeStretch) "Speed change without pitch shift" else "No time stretching",
                                checked = vlcCfg.audioTimeStretch,
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(audioTimeStretch = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Video,
                                title = "Video Output",
                                subtitle = "${vlcCfg.videoOutput.displayName} (${vlcCfg.videoOutput.key})",
                                trailingText = vlcCfg.videoOutput.key,
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { showVlcVideoOutputPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Wifi,
                                title = "Network Caching",
                                subtitle = if (vlcCfg.networkCaching == 0) "Auto (device-based)" else "${vlcCfg.networkCaching}ms",
                                trailingText = if (vlcCfg.networkCaching == 0) "auto" else "${vlcCfg.networkCaching}ms",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { showVlcNetworkCachingPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Filter,
                                title = "Skip Loop Filter",
                                subtitle = vlcSkipLoopFilterLabel(vlcCfg.skipLoopFilter),
                                trailingText = "${vlcCfg.skipLoopFilter}",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { showVlcSkipLoopFilterPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.PlayerSkipForward,
                                title = "Skip Frames",
                                subtitle = vlcSkipFrameLabel(vlcCfg.skipFrame),
                                trailingText = "${vlcCfg.skipFrame}",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { showVlcSkipFramePicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "Decoder Threads",
                                subtitle = if (vlcCfg.decoderThreads == 0) "Auto" else "${vlcCfg.decoderThreads} threads",
                                trailingText = if (vlcCfg.decoderThreads == 0) "auto" else "${vlcCfg.decoderThreads}",
                                index = vlcIdx++, count = vlcTotal,
                                onClick = { showVlcDecoderThreadsPicker = true },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.PlayerPause,
                                title = "Drop Late Frames",
                                subtitle = if (vlcCfg.dropLateFrames) "Drop late frames" else "Render all frames",
                                checked = vlcCfg.dropLateFrames,
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(dropLateFrames = it)) },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.PlayerSkipForward,
                                title = "Skip Frames Toggle",
                                subtitle = if (vlcCfg.skipFrames) "Enable frame skipping" else "No frame skipping",
                                checked = vlcCfg.skipFrames,
                                index = vlcIdx++, count = vlcTotal,
                                onCheckedChange = { viewModel.setLibVlcConfig(vlcCfg.copy(skipFrames = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = "Reset to Default",
                                subtitle = if (vlcCfg == vlcDefault) "Already using defaults" else "Restore all LibVLC settings to default",
                                index = vlcIdx, count = vlcTotal,
                                onClick = { viewModel.setLibVlcConfig(LibVlcEngineConfig()) },
                            )
                        }
                        PlayerType.EXO_PLAYER -> {
                            val exoCfg = preferences.exoPlayerConfig
                            val exoDefault = ExoPlayerEngineConfig()
                            val exoTotal = 8
                            var exoIdx = 0

                            SettingListItem(
                                icon = Tabler.Outline.ArrowsMaximize,
                                title = "Video Scaling",
                                subtitle = "${exoCfg.videoScalingMode.displayName} (${exoCfg.videoScalingMode.key})",
                                trailingText = exoCfg.videoScalingMode.key,
                                index = exoIdx++, count = exoTotal,
                                onClick = { showExoScalingPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Clock,
                                title = "Frame Rate Strategy",
                                subtitle = "${exoCfg.frameRateStrategy.displayName} (${exoCfg.frameRateStrategy.key})",
                                trailingText = exoCfg.frameRateStrategy.key,
                                index = exoIdx++, count = exoTotal,
                                onClick = { showExoFrameRatePicker = true },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.Volume,
                                title = "Skip Silence",
                                subtitle = if (exoCfg.skipSilence) "Skip silent sections" else "Play all audio",
                                checked = exoCfg.skipSilence,
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.setExoPlayerConfig(exoCfg.copy(skipSilence = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Cpu,
                                title = "Audio Offload",
                                subtitle = "${exoCfg.audioOffloadMode.displayName} (${exoCfg.audioOffloadMode.key})",
                                trailingText = exoCfg.audioOffloadMode.key,
                                index = exoIdx++, count = exoTotal,
                                onClick = { showExoAudioOffloadPicker = true },
                            )
                            SettingToggleItem(
                                icon = Tabler.Outline.BadgeHd,
                                title = "Decoder Fallback",
                                subtitle = if (exoCfg.enableDecoderFallback) "Fallback to secondary decoders" else "Primary decoder only",
                                checked = exoCfg.enableDecoderFallback,
                                index = exoIdx++, count = exoTotal,
                                onCheckedChange = { viewModel.setExoPlayerConfig(exoCfg.copy(enableDecoderFallback = it)) },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Database,
                                title = "Back Buffer",
                                subtitle = if (exoCfg.backBufferDurationMs == 0) "Disabled" else "${exoCfg.backBufferDurationMs / 1000}s",
                                trailingText = if (exoCfg.backBufferDurationMs == 0) "off" else "${exoCfg.backBufferDurationMs / 1000}s",
                                index = exoIdx++, count = exoTotal,
                                onClick = { showExoBackBufferPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Photo,
                                title = "Preferred Codecs",
                                subtitle = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All codecs" else exoCfg.preferredVideoMimeTypes.joinToString { it.removePrefix("video/") },
                                trailingText = if (exoCfg.preferredVideoMimeTypes.isEmpty()) "All" else "Custom",
                                index = exoIdx++, count = exoTotal,
                                onClick = { showExoCodecPicker = true },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Refresh,
                                title = "Reset to Default",
                                subtitle = if (exoCfg == exoDefault) "Already using defaults" else "Restore all ExoPlayer settings to default",
                                index = exoIdx, count = exoTotal,
                                onClick = { viewModel.setExoPlayerConfig(ExoPlayerEngineConfig()) },
                            )
                        }
                        else -> {}
                    }
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
                    val storageTotal = 5
                    SettingInfoItem(
                        icon = Tabler.Outline.Database,
                        title = "Cache Used",
                        subtitle = "${viewModel.cacheSizeMb} MB",
                        index = 0, count = storageTotal,
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Wifi,
                        title = "WiFi Only",
                        subtitle = if (preferences.wifiOnlyDownloads) "Downloads only on unmetered networks" else "Downloads on any network",
                        checked = preferences.wifiOnlyDownloads,
                        index = 1, count = storageTotal,
                        onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Trash,
                        title = "Clear Cache",
                        subtitle = "Free up storage space",
                        index = 2, count = storageTotal,
                        onClick = { viewModel.clearCache() },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Refresh,
                        title = "Auto-delete Cache",
                        subtitle = if (preferences.autoDeleteCache) "Automatically clears on low storage" else "Manual cache management",
                        checked = preferences.autoDeleteCache,
                        index = 3, count = storageTotal,
                        onCheckedChange = { viewModel.setAutoDeleteCache(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Database,
                        title = "Max Cache Size",
                        subtitle = "Maximum disk space for caching",
                        trailingText = if (preferences.maxCacheSizeMb == 0) "Unlimited" else "${preferences.maxCacheSizeMb} MB",
                        index = 4, count = storageTotal,
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
                        parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
                        val accentName = preferences.accentColorSwatch.lowercase().replaceFirstChar { it.uppercase() }
                        parts.add("$accentName accent")
                        parts.add(preferences.colorStyle.displayName)
                        if (preferences.dynamicTheming) parts.add("Artwork dynamic")
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

                    val appearanceItems = buildList {
                        add("theme_mode")
                        add("accent_color")
                        add("color_style")
                        if (isAndroid12) {
                            add("dynamic_theming")
                        }
                        if (isDarkActive) {
                            add("oled_mode")
                        }
                        add("contrast")
                        add("home_mode")
                        add("hero_section")
                        add("nav_labels")
                    }
                    val totalCount = appearanceItems.size
                    var currentIdx = 0

                    appearanceItems.forEach { item ->
                        when (item) {
                            "theme_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = "Theme Mode",
                                    subtitle = when (preferences.themeMode) {
                                        ThemeMode.SYSTEM -> "Follow system setting"
                                        ThemeMode.LIGHT -> "Always light"
                                        ThemeMode.DARK -> "Always dark"
                                    },
                                    trailingText = preferences.themeMode.name,
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        val next = when (preferences.themeMode) {
                                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                            ThemeMode.LIGHT -> ThemeMode.DARK
                                            ThemeMode.DARK -> ThemeMode.SYSTEM
                                        }
                                        viewModel.setThemeMode(next)
                                    },
                                )
                            }
                            "accent_color" -> {
                                AccentColorPicker(
                                    selectedSwatch = preferences.accentColorSwatch,
                                    onSwatchSelected = { viewModel.setAccentColorSwatch(it) },
                                )
                                currentIdx++
                            }
                            "color_style" -> {
                                ColorStylePicker(
                                    selectedStyle = preferences.colorStyle,
                                    onStyleSelected = { viewModel.setColorStyle(it) },
                                )
                                currentIdx++
                            }
                            "dynamic_theming" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Video,
                                    title = "Dynamic Theming",
                                    subtitle = "Colors extracted from artwork",
                                    checked = preferences.dynamicTheming,
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setDynamicTheming(it) },
                                )
                            }
                            "oled_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.BrightnessHalf,
                                    title = "OLED Mode",
                                    subtitle = "Pure black backgrounds for AMOLED displays",
                                    checked = preferences.oledMode,
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setOledMode(it) },
                                )
                            }
                            "contrast" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = "Contrast",
                                    subtitle = when (preferences.contrastLevel) {
                                        ContrastLevel.DEFAULT -> "Standard contrast"
                                        ContrastLevel.MEDIUM -> "Medium contrast"
                                        ContrastLevel.HIGH -> "High contrast"
                                    },
                                    trailingText = preferences.contrastLevel.name,
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        val next = when (preferences.contrastLevel) {
                                            ContrastLevel.DEFAULT -> ContrastLevel.MEDIUM
                                            ContrastLevel.MEDIUM -> ContrastLevel.HIGH
                                            ContrastLevel.HIGH -> ContrastLevel.DEFAULT
                                        }
                                        viewModel.setContrastLevel(next)
                                    },
                                )
                            }
                            "home_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Home,
                                    title = "Home Mode",
                                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) "Video-focused home screen" else "Music-focused home screen",
                                    trailingText = preferences.homeMode.name,
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                                        viewModel.setHomeMode(next)
                                    },
                                )
                            }
                            "hero_section" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.LayersLinked,
                                    title = "Show Hero Section",
                                    subtitle = if (preferences.homeHeroEnabled) "Featured content banner on home" else "Compact home layout",
                                    checked = preferences.homeHeroEnabled,
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setHomeHeroEnabled(it) },
                                )
                            }
                            "nav_labels" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = "Show Navigation Labels",
                                    subtitle = if (preferences.navBarShowLabels) "Icons and text" else "Icons only",
                                    checked = preferences.navBarShowLabels,
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setNavBarShowLabels(it) },
                                )
                            }
                        }
                    }
                }
            }

            AnimatedSettingsEntrance(12) {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = "Performance",
                    summary = {
                        if (preferences.performanceMode) "Reduced animations and effects" else "Standard experience"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Performance Mode",
                        subtitle = "Reduces animations and effects for better performance on lower-end devices",
                        checked = preferences.performanceMode,
                        index = 0, count = 1,
                        onCheckedChange = { viewModel.setPerformanceMode(it) },
                    )
                }
            }

            if (isTv) {
                AnimatedSettingsEntrance(13) {
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

            AnimatedSettingsEntrance(if (isTv) 14 else 13) {
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
                        val bioActivity = remember(bioContext) { bioContext.findFragmentActivity() }
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

            AnimatedSettingsEntrance(if (isTv) 15 else 14) {
                SettingsGroup(
                    icon = Tabler.Outline.DatabaseExport,
                    title = "Backup & Restore",
                    summary = { "Export or import app settings" },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.FileExport,
                        title = "Export Settings",
                        subtitle = "Save current settings to a JSON file",
                        index = 0, count = 2,
                        onClick = {
                            settingsLauncher.launch("jellyplay-settings.json")
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.FileImport,
                        title = "Import Settings",
                        subtitle = "Restore settings from a backup file",
                        index = 1, count = 2,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        },
                    )
                }

                LaunchedEffect(viewModel.backupRestoreStatus) {
                    viewModel.backupRestoreStatus?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearBackupRestoreStatus()
                    }
                }
            }

            AnimatedSettingsEntrance(if (isTv) 16 else 15) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingListItem(
                        icon = Tabler.Outline.InfoCircle,
                        title = "About JellyPlay",
                        subtitle = "Version ${viewModel.appVersion}",
                        index = 0, count = 1,
                        onClick = onAboutClick,
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
        val bandLevels = remember(preferences.equalizerSettings.bandLevels) {
            mutableStateListOf<Int>().apply { addAll(preferences.equalizerSettings.bandLevels) }
        }
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
                    items(EqualizerSettings.BAND_FREQUENCIES.size, key = { it }, contentType = { "band" }) { i ->
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

    if (showMpvVideoOutputPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Video Output (vo)",
            items = MpvVideoOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.videoOutput },
            onDismiss = { showMpvVideoOutputPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(videoOutput = it))
                showMpvVideoOutputPicker = false
            },
        )
    }

    if (showMpvScalerPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Scaler (dscale)",
            items = MpvScaler.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.scaler },
            onDismiss = { showMpvScalerPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(scaler = it))
                showMpvScalerPicker = false
            },
        )
    }

    if (showMpvAudioOutputPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Audio Output (ao)",
            items = MpvAudioOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.audioOutput },
            onDismiss = { showMpvAudioOutputPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(audioOutput = it))
                showMpvAudioOutputPicker = false
            },
        )
    }

    if (showMpvAudioFallbackPicker) {
        val mpvCfg = preferences.mpvConfig
        val options = listOf(null) + MpvAudioOutput.entries
        SettingsListPickerSheet(
            title = "Audio Fallback (ao-fallback)",
            items = options,
            label = { it?.displayName ?: "None" },
            subtitle = { it?.key ?: "no fallback" },
            isSelected = { it == mpvCfg.audioFallback },
            onDismiss = { showMpvAudioFallbackPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(audioFallback = it))
                showMpvAudioFallbackPicker = false
            },
        )
    }

    if (showMpvDemuxerPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Buffer Size (demuxer-max-bytes)",
            items = MpvDemuxerMaxBytes.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.demuxerMaxBytes },
            onDismiss = { showMpvDemuxerPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(demuxerMaxBytes = it))
                showMpvDemuxerPicker = false
            },
        )
    }

    if (showMpvHwdecPicker) {
        val mpvCfg = preferences.mpvConfig
        val options = listOf(null) + MpvHwdec.entries
        SettingsListPickerSheet(
            title = "HW Decoder Override (hwdec)",
            items = options,
            label = { it?.displayName ?: "Use universal setting" },
            subtitle = { it?.key ?: "auto" },
            isSelected = { it == mpvCfg.hwdecOverride },
            onDismiss = { showMpvHwdecPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(hwdecOverride = it))
                showMpvHwdecPicker = false
            },
        )
    }

    if (showMpvSkipLoopFilterPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Skip Loop Filter (vd-lavc-skiploopfilter)",
            items = MpvSkipLoopFilter.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.skipLoopFilter },
            onDismiss = { showMpvSkipLoopFilterPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(skipLoopFilter = it))
                showMpvSkipLoopFilterPicker = false
            },
        )
    }

    if (showMpvFrameDropPicker) {
        val mpvCfg = preferences.mpvConfig
        SettingsListPickerSheet(
            title = "Frame Drop (framedrop)",
            items = MpvFrameDrop.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == mpvCfg.frameDrop },
            onDismiss = { showMpvFrameDropPicker = false },
            onSelect = {
                viewModel.setMpvConfig(mpvCfg.copy(frameDrop = it))
                showMpvFrameDropPicker = false
            },
        )
    }

    if (showVlcAudioOutputPicker) {
        val vlcCfg = preferences.libVlcConfig
        SettingsListPickerSheet(
            title = "Audio Output (aout)",
            items = VlcAudioOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == vlcCfg.audioOutput },
            onDismiss = { showVlcAudioOutputPicker = false },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(audioOutput = it))
                showVlcAudioOutputPicker = false
            },
        )
    }

    if (showVlcVideoOutputPicker) {
        val vlcCfg = preferences.libVlcConfig
        SettingsListPickerSheet(
            title = "Video Output (vout)",
            items = VlcVideoOutput.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == vlcCfg.videoOutput },
            onDismiss = { showVlcVideoOutputPicker = false },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(videoOutput = it))
                showVlcVideoOutputPicker = false
            },
        )
    }

    if (showVlcNetworkCachingPicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = listOf(0, 500, 1000, 1500, 2000, 3000, 5000)
        SettingsListPickerSheet(
            title = "Network Caching (network-caching)",
            items = options,
            label = { if (it == 0) "Auto (device-based)" else "${it}ms" },
            subtitle = { if (it == 0) "auto" else "${it}ms" },
            isSelected = { it == vlcCfg.networkCaching },
            onDismiss = { showVlcNetworkCachingPicker = false },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(networkCaching = it))
                showVlcNetworkCachingPicker = false
            },
        )
    }

    if (showVlcSkipLoopFilterPicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = (0..4).toList()
        SettingsListPickerSheet(
            title = "Skip Loop Filter (skiploopfilter)",
            items = options,
            label = { vlcSkipLoopFilterLabel(it) },
            subtitle = { "level $it" },
            isSelected = { it == vlcCfg.skipLoopFilter },
            onDismiss = { showVlcSkipLoopFilterPicker = false },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(skipLoopFilter = it))
                showVlcSkipLoopFilterPicker = false
            },
        )
    }

    if (showVlcSkipFramePicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = (0..4).toList()
        SettingsListPickerSheet(
            title = "Skip Frames (skip-frames)",
            items = options,
            label = { vlcSkipFrameLabel(it) },
            subtitle = { "level $it" },
            isSelected = { it == vlcCfg.skipFrame },
            onDismiss = { showVlcSkipFramePicker = false },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(skipFrame = it))
                showVlcSkipFramePicker = false
            },
        )
    }

    if (showVlcDecoderThreadsPicker) {
        val vlcCfg = preferences.libVlcConfig
        val options = listOf(0, 1, 2, 4, 6, 8)
        SettingsListPickerSheet(
            title = "Decoder Threads (codec-dr-threads)",
            items = options,
            label = { if (it == 0) "Auto" else "$it threads" },
            subtitle = { if (it == 0) "auto" else "$it" },
            isSelected = { it == vlcCfg.decoderThreads },
            onDismiss = { showVlcDecoderThreadsPicker = false },
            onSelect = {
                viewModel.setLibVlcConfig(vlcCfg.copy(decoderThreads = it))
                showVlcDecoderThreadsPicker = false
            },
        )
    }

    if (showExoScalingPicker) {
        val exoCfg = preferences.exoPlayerConfig
        SettingsListPickerSheet(
            title = "Video Scaling (scalingMode)",
            items = ExoVideoScalingMode.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == exoCfg.videoScalingMode },
            onDismiss = { showExoScalingPicker = false },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(videoScalingMode = it))
                showExoScalingPicker = false
            },
        )
    }

    if (showExoFrameRatePicker) {
        val exoCfg = preferences.exoPlayerConfig
        SettingsListPickerSheet(
            title = "Frame Rate Strategy (setVideoAspectRatio)",
            items = ExoFrameRateStrategy.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == exoCfg.frameRateStrategy },
            onDismiss = { showExoFrameRatePicker = false },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(frameRateStrategy = it))
                showExoFrameRatePicker = false
            },
        )
    }

    if (showExoAudioOffloadPicker) {
        val exoCfg = preferences.exoPlayerConfig
        SettingsListPickerSheet(
            title = "Audio Offload (audioOffloadMode)",
            items = ExoAudioOffloadMode.entries,
            label = { it.displayName },
            subtitle = { it.key },
            isSelected = { it == exoCfg.audioOffloadMode },
            onDismiss = { showExoAudioOffloadPicker = false },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(audioOffloadMode = it))
                showExoAudioOffloadPicker = false
            },
        )
    }

    if (showExoBackBufferPicker) {
        val exoCfg = preferences.exoPlayerConfig
        val options = listOf(0, 5000, 10000, 15000, 20000, 30000)
        SettingsListPickerSheet(
            title = "Back Buffer (backBufferDurationMs)",
            items = options,
            label = { if (it == 0) "Disabled" else "${it / 1000}s" },
            subtitle = { if (it == 0) "off" else "${it}ms" },
            isSelected = { it == exoCfg.backBufferDurationMs },
            onDismiss = { showExoBackBufferPicker = false },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(backBufferDurationMs = it))
                showExoBackBufferPicker = false
            },
        )
    }

    if (showExoCodecPicker) {
        val exoCfg = preferences.exoPlayerConfig
        val presets = listOf(
            emptyList<String>(),
            listOf("video/hevc", "video/avc"),
            listOf("video/av1", "video/hevc", "video/avc"),
            listOf("video/avc"),
        )
        val presetLabels = listOf("All codecs", "HEVC + AVC", "AV1 + HEVC + AVC", "AVC only")
        SettingsListPickerSheet(
            title = "Preferred Codecs (preferredVideoMimeTypes)",
            items = presets,
            label = { presetLabels[presets.indexOf(it)] },
            subtitle = { if (it.isEmpty()) "*" else it.joinToString(", ") },
            isSelected = { it == exoCfg.preferredVideoMimeTypes },
            onDismiss = { showExoCodecPicker = false },
            onSelect = {
                viewModel.setExoPlayerConfig(exoCfg.copy(preferredVideoMimeTypes = it))
                showExoCodecPicker = false
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

private fun vlcSkipLoopFilterLabel(level: Int): String = when (level) {
    0 -> "None (Best Quality)"
    1 -> "Default"
    2 -> "Non-Reference"
    3 -> "Bi-Directional"
    4 -> "All (Fastest)"
    else -> "Level $level"
}

private fun vlcSkipFrameLabel(level: Int): String = when (level) {
    0 -> "None (No Skipping)"
    1 -> "Default"
    2 -> "Non-Reference"
    3 -> "Bi-Directional"
    4 -> "All (Aggressive)"
    else -> "Level $level"
}

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    val amPm = if (hours >= 12) "PM" else "AM"
    val displayHour = when {
        hours == 0 -> 12
        hours > 12 -> hours - 12
        else -> hours
    }
    return "${displayHour}:${mins.toString().padStart(2, '0')} $amPm"
}

@Composable
private fun SingleChoicePicker(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelected(index) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelected(index) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimePicker(
    title: String,
    initialMinutes: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.TimePicker(state = timePickerState)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelected(timePickerState.hour * 60 + timePickerState.minute)
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
