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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.settings.SeerrSettingsViewModel.ConnectionStatus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

sealed class SeerrSettingsDialog {
    object None : SeerrSettingsDialog()
    object StreamingRegionPicker : SeerrSettingsDialog()
    object DiscoverRegionPicker : SeerrSettingsDialog()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeerrSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SeerrSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val connectionStatus = viewModel.connectionStatus
    val isTesting = viewModel.isTesting
    val isConnected = connectionStatus is ConnectionStatus.Connected

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    var activeDialog by remember { mutableStateOf<SeerrSettingsDialog>(SeerrSettingsDialog.None) }
    var animateEntrance by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateEntrance = true
    }

    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId) {
        when (highlightSettingId) {
            "seerr_settings" -> 2
            else -> -1
        }
    }

    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {}
        }
    }

    JellyPlayScreenScaffold(
        title = "Seerr Settings",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = contentPad,
                end = contentPad,
                top = 16.dp,
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            item {
                AnimatedSettingsEntrance(index = 0, visible = animateEntrance) {
                    SeerrHeader()
                }
            }

            // Connection Group
            item {
                AnimatedSettingsEntrance(index = 1, visible = animateEntrance) {
                    SettingsGroup(
                        icon = Tabler.Outline.Server,
                        title = "Server Connection",
                        summary = {
                            when (connectionStatus) {
                                is ConnectionStatus.Connected -> {
                                    val versionText = if (connectionStatus.version.isNotBlank()) " (v${connectionStatus.version})" else ""
                                    "Connected to ${viewModel.serverUrl}$versionText"
                                }
                                is ConnectionStatus.Error -> "Connection failed"
                                else -> {
                                    if (isTesting) "Connecting..."
                                    else if (viewModel.serverUrl.isNotBlank()) "Credentials configured"
                                    else "Configure server address"
                                }
                            }
                        },
                        initiallyExpanded = !isConnected,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = viewModel.serverUrl,
                                onValueChange = viewModel::onServerUrlChanged,
                                label = { Text("Server URL") },
                                placeholder = { Text("http://localhost:5055") },
                                leadingIcon = {
                                    Icon(Tabler.Outline.Globe, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (viewModel.serverUrl.isNotBlank() && !isTesting) {
                                        IconButton(onClick = { viewModel.onServerUrlChanged("") }) {
                                            Icon(Tabler.Outline.X, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isTesting,
                                shape = ShapeCache.smooth16,
                            )

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                    onClick = { viewModel.onAuthMethodChanged(SeerrAuthMethod.API_KEY) },
                                    selected = viewModel.authMethod == SeerrAuthMethod.API_KEY,
                                    icon = {},
                                ) {
                                    Text("API Key")
                                }
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                    onClick = { viewModel.onAuthMethodChanged(SeerrAuthMethod.JELLYFIN) },
                                    selected = viewModel.authMethod == SeerrAuthMethod.JELLYFIN,
                                    icon = {},
                                ) {
                                    Text("Jellyfin")
                                }
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                    onClick = { viewModel.onAuthMethodChanged(SeerrAuthMethod.LOCAL) },
                                    selected = viewModel.authMethod == SeerrAuthMethod.LOCAL,
                                    icon = {},
                                ) {
                                    Text("Local")
                                }
                            }

                            when (viewModel.authMethod) {
                                SeerrAuthMethod.API_KEY -> ApiKeyFields(viewModel)
                                SeerrAuthMethod.JELLYFIN -> JellyfinAuthFields(viewModel)
                                SeerrAuthMethod.LOCAL -> LocalAuthFields(viewModel)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = { viewModel.testConnection() },
                                    enabled = !isTesting && viewModel.serverUrl.isNotBlank() && when (viewModel.authMethod) {
                                        SeerrAuthMethod.API_KEY -> viewModel.apiKey.isNotBlank()
                                        SeerrAuthMethod.JELLYFIN -> viewModel.username.isNotBlank() && viewModel.password.isNotBlank()
                                        SeerrAuthMethod.LOCAL -> viewModel.email.isNotBlank() && viewModel.password.isNotBlank()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = ShapeCache.smooth16,
                                ) {
                                    if (isTesting) {
                                        JellyPlayCircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        when (viewModel.authMethod) {
                                            SeerrAuthMethod.API_KEY -> "Test Connection"
                                            SeerrAuthMethod.JELLYFIN,
                                            SeerrAuthMethod.LOCAL -> "Sign In"
                                        }
                                    )
                                }

                                if (isConnected) {
                                    OutlinedButton(
                                        onClick = { viewModel.disconnect() },
                                        shape = ShapeCache.smooth16,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Text("Disconnect")
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = connectionStatus is ConnectionStatus.Connected || connectionStatus is ConnectionStatus.Error,
                                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                            ) {
                                ConnectionStatusBanner(connectionStatus)
                            }
                        }
                    }
                }
            }

            // Features Group
            if (isConnected) {
                item {
                    AnimatedSettingsEntrance(index = 2, visible = animateEntrance) {
                        SettingsGroup(
                            icon = Tabler.Outline.Puzzle,
                            title = "Integration Features",
                            summary = {
                                if (preferences.enabled) {
                                    val count = listOf(
                                        preferences.searchEnabled,
                                        preferences.recommendationsEnabled,
                                        preferences.discoverEnabled
                                    ).count { it } + 1
                                    "$count of 4 features active"
                                } else {
                                    "Disabled"
                                }
                            },
                            modifier = Modifier.padding(vertical = 4.dp),
                            initiallyExpanded = preferences.enabled || highlightSettingId == "seerr_settings",
                        ) {
                            val showSubFeatures = preferences.enabled
                            val featTotal = if (showSubFeatures) 4 else 1

                            SettingToggleItem(
                                icon = Tabler.Outline.Puzzle,
                                title = "Enable Seerr Integration",
                                subtitle = "Connect your Seerr server with JellyPlay",
                                checked = preferences.enabled,
                                index = 0,
                                count = featTotal,
                                highlighted = highlightSettingId == "seerr_settings",
                                onCheckedChange = viewModel::setEnabled,
                            )

                            AnimatedVisibility(
                                visible = showSubFeatures,
                                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Search,
                                        title = "Search Integration",
                                        subtitle = "Request media directly from app search",
                                        checked = preferences.searchEnabled,
                                        index = 1,
                                        count = 4,
                                        onCheckedChange = viewModel::setSearchEnabled,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Sparkles,
                                        title = "Recommendations & Similar",
                                        subtitle = "Show Seerr recommendations on media detail screens",
                                        checked = preferences.recommendationsEnabled,
                                        index = 2,
                                        count = 4,
                                        onCheckedChange = viewModel::setRecommendationsEnabled,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Compass,
                                        title = "Advance Discover",
                                        subtitle = "Show Trending, Popular & Upcoming content on the home screen",
                                        checked = preferences.discoverEnabled,
                                        index = 3,
                                        count = 4,
                                        onCheckedChange = viewModel::setDiscoverEnabled,
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showSubFeatures && preferences.discoverEnabled,
                                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val discTotal = 5
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Flame,
                                        title = "Trending",
                                        subtitle = "Trending movies and series",
                                        checked = preferences.discoverTrending,
                                        index = 0,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverTrending,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Movie,
                                        title = "Popular Movies",
                                        subtitle = "Popular movies on TMDB",
                                        checked = preferences.discoverPopularMovies,
                                        index = 1,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverPopularMovies,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.DeviceTv,
                                        title = "Popular Series",
                                        subtitle = "Popular TV series on TMDB",
                                        checked = preferences.discoverPopularTv,
                                        index = 2,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverPopularTv,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.CalendarTime,
                                        title = "Upcoming Movies",
                                        subtitle = "Movies coming soon to theaters",
                                        checked = preferences.discoverUpcomingMovies,
                                        index = 3,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverUpcomingMovies,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.CalendarEvent,
                                        title = "Upcoming Series",
                                        subtitle = "TV series coming soon",
                                        checked = preferences.discoverUpcomingTv,
                                        index = 4,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverUpcomingTv,
                                    )
                                }
                            }
                        }
                    }
                }

                // Regions Group
                item {
                    AnimatedSettingsEntrance(index = 3, visible = animateEntrance) {
                        SettingsGroup(
                            icon = Tabler.Outline.World,
                            title = "Content Regions",
                            summary = {
                                val streamingLabel = COMMON_REGIONS.find { it.first == preferences.streamingRegion }?.second?.substringAfter(" ") ?: preferences.streamingRegion
                                val discoverLabel = COMMON_REGIONS.find { it.first == preferences.discoverRegion }?.second?.substringAfter(" ") ?: preferences.discoverRegion
                                "Streaming: $streamingLabel, Discover: $discoverLabel"
                            },
                            modifier = Modifier.padding(vertical = 4.dp),
                            initiallyExpanded = true,
                        ) {
                            val streamingFlagAndName = COMMON_REGIONS.find { it.first == preferences.streamingRegion }?.second ?: preferences.streamingRegion
                            val discoverFlagAndName = COMMON_REGIONS.find { it.first == preferences.discoverRegion }?.second ?: preferences.discoverRegion

                            SettingListItem(
                                icon = Tabler.Outline.DeviceTv,
                                title = "Streaming Region",
                                subtitle = "Region used to determine available streaming providers",
                                trailingText = streamingFlagAndName,
                                index = 0,
                                count = 2,
                                onClick = { activeDialog = SeerrSettingsDialog.StreamingRegionPicker },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Compass,
                                title = "Discover Region",
                                subtitle = "Region used for release dates and content discovery",
                                trailingText = discoverFlagAndName,
                                index = 1,
                                count = 2,
                                onClick = { activeDialog = SeerrSettingsDialog.DiscoverRegionPicker },
                            )
                        }
                    }
                }
            }
        }
    }

    if (activeDialog is SeerrSettingsDialog.StreamingRegionPicker) {
        SettingsListPickerSheet(
            title = "Streaming Region",
            items = COMMON_REGIONS.map { it.first },
            label = { code -> COMMON_REGIONS.find { it.first == code }?.second ?: code },
            isSelected = { it == preferences.streamingRegion },
            onDismiss = { activeDialog = SeerrSettingsDialog.None },
            onSelect = {
                viewModel.setStreamingRegion(it)
                activeDialog = SeerrSettingsDialog.None
            },
        )
    }

    if (activeDialog is SeerrSettingsDialog.DiscoverRegionPicker) {
        SettingsListPickerSheet(
            title = "Discover Region",
            items = COMMON_REGIONS.map { it.first },
            label = { code -> COMMON_REGIONS.find { it.first == code }?.second ?: code },
            isSelected = { it == preferences.discoverRegion },
            onDismiss = { activeDialog = SeerrSettingsDialog.None },
            onSelect = {
                viewModel.setDiscoverRegion(it)
                activeDialog = SeerrSettingsDialog.None
            },
        )
    }
}

@Composable
private fun AnimatedSettingsEntrance(
    index: Int,
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    var itemVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(index * 35L)
            itemVisible = true
        }
    }
    AnimatedVisibility(
        visible = itemVisible,
        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        content()
    }
}

@Composable
private fun SeerrHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth24)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(ShapeCache.smooth16)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Tabler.Outline.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = "Seerr Request Hub",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Connect Jellyseerr or Overseerr to search, discover, and request content directly from within JellyPlay.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyFields(viewModel: SeerrSettingsViewModel) {
    var apiKeyVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = viewModel.apiKey,
        onValueChange = viewModel::onApiKeyChanged,
        label = { Text("API Key") },
        placeholder = { Text("Enter your Seerr API key") },
        leadingIcon = {
            Icon(Tabler.Outline.Key, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                Icon(
                    imageVector = if (apiKeyVisible) Tabler.Outline.EyeOff else Tabler.Outline.Eye,
                    contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key"
                )
            }
        },
        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !viewModel.isTesting,
        shape = ShapeCache.smooth16,
    )
}

@Composable
private fun JellyfinAuthFields(viewModel: SeerrSettingsViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = viewModel.username,
            onValueChange = viewModel::onUsernameChanged,
            label = { Text("Username") },
            placeholder = { Text("Jellyfin username") },
            leadingIcon = {
                Icon(Tabler.Outline.User, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isTesting,
            shape = ShapeCache.smooth16,
        )
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("Password") },
            placeholder = { Text("Jellyfin password") },
            leadingIcon = {
                Icon(Tabler.Outline.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Tabler.Outline.EyeOff else Tabler.Outline.Eye,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isTesting,
            shape = ShapeCache.smooth16,
        )
    }
}

@Composable
private fun LocalAuthFields(viewModel: SeerrSettingsViewModel) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email") },
            placeholder = { Text("Seerr account email") },
            leadingIcon = {
                Icon(Tabler.Outline.Mail, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isTesting,
            shape = ShapeCache.smooth16,
        )
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("Password") },
            placeholder = { Text("Seerr account password") },
            leadingIcon = {
                Icon(Tabler.Outline.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Tabler.Outline.EyeOff else Tabler.Outline.Eye,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isTesting,
            shape = ShapeCache.smooth16,
        )
    }
}

@Composable
private fun ConnectionStatusBanner(status: ConnectionStatus) {
    val (icon, title, subtitle, color) = when (status) {
        is ConnectionStatus.Connected -> Quadruple(
            Tabler.Outline.CircleCheck,
            "Connected",
            if (status.version.isNotBlank()) "Server reached (v${status.version})" else "Seerr server reached successfully",
            StatusColors.success
        )
        is ConnectionStatus.Error -> Quadruple(
            Tabler.Outline.AlertTriangle,
            "Connection Failed",
            status.message,
            MaterialTheme.colorScheme.error
        )
        else -> return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private val COMMON_REGIONS = listOf(
    "US" to "\uD83C\uDDFA\uD83C\uDDF8 United States",
    "GB" to "\uD83C\uDDEC\uD83C\uDDE7 United Kingdom",
    "CA" to "\uD83C\uDDE8\uD83C\uDDE6 Canada",
    "AU" to "\uD83C\uDDE6\uD83C\uDDFA Australia",
    "DE" to "\uD83C\uDDE9\uD83C\uDDEA Germany",
    "FR" to "\uD83C\uDDEB\uD83C\uDDF7 France",
    "JP" to "\uD83C\uDDEF\uD83C\uDDF5 Japan",
    "KR" to "\uD83C\uDDF0\uD83C\uDDF7 South Korea",
    "BR" to "\uD83C\uDDE7\uD83C\uDDF7 Brazil",
    "IN" to "\uD83C\uDDEE\uD83C\uDDF3 India",
    "ES" to "\uD83C\uDDEA\uD83C\uDDF8 Spain",
    "IT" to "\uD83C\uDDEE\uD83C\uDDF9 Italy",
    "MX" to "\uD83C\uDDF2\uD83C\uDDFD Mexico",
    "NL" to "\uD83C\uDDF3\uD83C\uDDF1 Netherlands",
    "SE" to "\uD83C\uDDF8\uD83C\uDDEA Sweden",
)
