package com.raulshma.jellyplay.feature.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private val LocalAnimateSettingsEntrance = staticCompositionLocalOf { false }

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
    onAppearanceSettings: () -> Unit = {},
    onPlaybackSettings: () -> Unit = {},
    onAudioSettings: () -> Unit = {},
    onLanguageSettings: () -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    onStorageSettings: () -> Unit = {},
    onSecuritySettings: () -> Unit = {},
    onBackupSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val userName = viewModel.currentUserName
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

    val listFocusRequester = remember { FocusRequester() }

    var animateEntrance by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateEntrance = true
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            kotlinx.coroutines.delay(150)
            try { listFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val currentServerAddress by viewModel.currentServerAddress.collectAsStateWithLifecycle()

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold(
        title = "Settings",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = preferences.showAdvancedSettings,
                onToggle = { viewModel.setShowAdvancedSettings(!preferences.showAdvancedSettings) },
            )
            androidx.compose.material3.IconButton(onClick = onNewsletterClick) {
                Icon(
                    Tabler.Outline.Mail,
                    contentDescription = "Newsletter",
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        val context = LocalContext.current

        LaunchedEffect(viewModel.messageSentEvent) {
            viewModel.messageSentEvent?.let { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessageEvent()
            }
        }

        CompositionLocalProvider(LocalAnimateSettingsEntrance provides animateEntrance) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .then(if (isTv) Modifier
                        .tvFocusRestorer()
                        .focusRequester(listFocusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp) {
                                onBack()
                                true
                            } else false
                        }
                    else Modifier),
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = adaptiveInfo.contentPadding(LocalTvMode.current),
                    end = adaptiveInfo.contentPadding(LocalTvMode.current),
                    bottom = adaptiveInfo.bottomPadding(LocalTvMode.current),
                ),
            ) {
                item {
                    AnimatedSettingsEntrance(0) {
                        if (userName.isNotBlank()) {
                            SettingsProfileBanner(
                                userName = userName,
                            )
                        }
                    }
                }

                item {
                    AnimatedSettingsEntrance(1) {
                        if (viewModel.currentUser?.isAdmin == true && viewModel.activeSessions.isNotEmpty()) {
                            ActiveDevicesRow(
                                sessions = viewModel.activeSessions,
                                serverAddress = currentServerAddress,
                                onSendMessage = viewModel::sendMessageToSession,
                            )
                        }
                    }
                }

                item {
                    AnimatedSettingsEntrance(1) {
                        SettingsGroup(
                            icon = Tabler.Outline.User,
                            title = "Account",
                            summary = { "Signed in as $userName" },
                            initiallyExpanded = true,
                        ) {
                            SettingListItem(
                                icon = Tabler.Outline.Logout,
                                title = "Sign Out",
                                subtitle = "Log out of current account",
                                index = 0, count = 1,
                                isDestructive = true,
                                onClick = {
                                    viewModel.logout()
                                    onLogout()
                                },
                            )
                        }
                    }
                }

                item {
                    AnimatedSettingsEntrance(2) {
                        SettingListItem(
                            icon = Tabler.Outline.Palette,
                            title = "Appearance",
                            subtitle = buildAppearanceSummary(preferences),
                            index = 0, count = 1,
                            onClick = onAppearanceSettings,
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(3) {
                        SettingListItem(
                            icon = Tabler.Outline.PlayerPlay,
                            title = "Playback",
                            subtitle = "Player Engine: ${preferences.preferredPlayer.displayName}",
                            index = 0, count = 1,
                            onClick = onPlaybackSettings,
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(4) {
                        SettingListItem(
                            icon = Tabler.Outline.Music,
                            title = "Audio Player",
                            subtitle = "Default speed: ${if (preferences.audioDefaultSpeed == 1.0f) "1x" else "${preferences.audioDefaultSpeed}x"}",
                            index = 0, count = 1,
                            onClick = onAudioSettings,
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(5) {
                        SettingListItem(
                            icon = Tabler.Outline.Language,
                            title = "Language & Subtitles",
                            subtitle = "Audio: ${preferences.preferredAudioLanguage ?: "Default"}",
                            index = 0, count = 1,
                            onClick = onLanguageSettings,
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(6) {
                        val notifPrefs = preferences.notificationPreferences
                        SettingListItem(
                            icon = Tabler.Outline.Bell,
                            title = "Notifications",
                            subtitle = if (notifPrefs.enabled) "Checking ${notifPrefs.checkFrequency.displayName.lowercase()}" else "Disabled",
                            index = 0, count = 1,
                            onClick = onNotificationSettings,
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(7) {
                        SettingListItem(
                            icon = Tabler.Outline.Database,
                            title = "Storage",
                            subtitle = "Cache: ${viewModel.cacheSizeMb} MB",
                            index = 0, count = 1,
                            onClick = onStorageSettings,
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(8) {
                        SettingListItem(
                            icon = Tabler.Outline.Lock,
                            title = "Security",
                            subtitle = when {
                                preferences.pinLockEnabled && preferences.biometricLockEnabled -> "PIN + Biometric lock: On"
                                preferences.biometricLockEnabled -> "Biometric lock: On"
                                preferences.pinLockEnabled -> "PIN lock: On"
                                else -> "Lock: Off"
                            },
                            index = 0, count = 1,
                            onClick = onSecuritySettings,
                        )
                    }
                }

                item {
                    AnimatedSettingsEntrance(9) {
                        SettingListItem(
                            icon = Tabler.Outline.DatabaseExport,
                            title = "Backup & Restore",
                            subtitle = "Export or import app settings",
                            index = 0, count = 1,
                            onClick = onBackupSettings,
                        )
                    }
                }

                if (isTv) {
                    item {
                        AnimatedSettingsEntrance(10) {
                            SettingsGroup(
                                icon = Tabler.Outline.Moon,
                                title = "Screensaver",
                                summary = {
                                    val cats = preferences.dreamImageCategories.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                                    cats.joinToString(", ")
                                },
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
                                    subtitle = "Time between image changes",
                                    trailingText = "${preferences.dreamSlideshowIntervalMs / 1000}s",
                                    index = 2, count = dreamTotal,
                                    onClick = {
                                        val intervals = listOf(5_000L, 10_000L, 15_000L, 30_000L, 60_000L)
                                        val currentIndex = intervals.indexOf(preferences.dreamSlideshowIntervalMs)
                                        val nextIndex = (currentIndex + 1) % intervals.size
                                        viewModel.setDreamSlideshowIntervalMs(intervals[nextIndex])
                                    },
                                )
                                SettingToggleItem(
                                    icon = Tabler.Outline.Wand,
                                    title = "Ken Burns Effect",
                                    subtitle = if (preferences.dreamKenBurnsEnabled) "Pan and zoom animation" else "Static display",
                                    checked = preferences.dreamKenBurnsEnabled,
                                    index = 3, count = dreamTotal,
                                    onCheckedChange = { viewModel.setDreamKenBurnsEnabled(it) },
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.ArrowRight,
                                    title = "Transition Style",
                                    subtitle = preferences.dreamTransitionStyle.name,
                                    trailingText = preferences.dreamTransitionStyle.name,
                                    index = 4, count = dreamTotal,
                                    onClick = {
                                        val styles = DreamTransitionStyle.entries
                                        val currentIndex = styles.indexOf(preferences.dreamTransitionStyle)
                                        val nextIndex = (currentIndex + 1) % styles.size
                                        viewModel.setDreamTransitionStyle(styles[nextIndex])
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedSettingsEntrance(if (isTv) 11 else 10) {
                        SettingListItem(
                            icon = Tabler.Outline.InfoCircle,
                            title = "About",
                            subtitle = "App version and licenses",
                            index = 0, count = 1,
                            onClick = onAboutClick,
                        )
                    }
                }
            }
        }
    }
}

private fun buildAppearanceSummary(preferences: com.raulshma.jellyplay.core.model.UserPreferences): String {
    val parts = mutableListOf<String>()
    parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
    if (preferences.dynamicTheming) parts.add("Dynamic")
    if (preferences.oledMode) parts.add("OLED")
    if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add("${preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() }} contrast")
    if (preferences.performanceMode) parts.add("Performance")
    return parts.joinToString(", ")
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
    val animate = LocalAnimateSettingsEntrance.current
    var visible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(animate) }

    LaunchedEffect(animate) {
        if (animate && !visible) {
            kotlinx.coroutines.delay(index * 20L)
            visible = true
        }
    }

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
