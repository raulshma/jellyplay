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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator
import com.raulshma.jellyplay.core.ui.components.PasswordTextField
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.settings.SeerrSettingsViewModel.ConnectionStatus
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_discover
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_advanced_discover_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_api_key_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_api_key_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connected
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connected_to
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connecting
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connection_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_connection_failed_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_content_regions
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_content_regions_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_server_address
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_credentials_configured
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_region
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_region_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disconnect
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_email_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_seerr_integration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_seerr_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_features_active
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integration_features
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_arr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyfin_password_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyfin_username_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_password_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_popular_movies
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_popular_movies_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_popular_series
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_popular_series_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_recommendations_similar
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_recommendations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_integration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_search_integration_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_api_key_method
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_email_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_header_description
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_jellyfin_method
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_local_method
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_password_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_request_hub
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_server_reached
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_sign_in
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_connection
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_reached_version
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_url
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_url_placeholder
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_region
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_streaming_region_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_test_connection
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trending
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_trending_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming_movies
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming_movies_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming_series
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_upcoming_series_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_username_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_clear_cd

// Region pickers flow through the shared `PickerState` dispatcher; no screen-local
// sealed dialog enum is needed.

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeerrSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SeerrSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val connectionStatus = viewModel.connectionStatus
    val isTesting = viewModel.isTesting
    val isConnected = connectionStatus is ConnectionStatus.Connected

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    var activeDialog by remember { mutableStateOf<PickerState<*>?>(null) }
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

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "seerr_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_seerr_settings),
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .tvFocusRestorer()
                .focusRequester(focusRequester),
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
                        title = stringResource(Res.string.settings_server_connection),
                        summary = {
                            when (connectionStatus) {
                                is ConnectionStatus.Connected -> {
                                    val versionText = if (connectionStatus.version.isNotBlank()) " (v${connectionStatus.version})" else ""
                                    stringResource(Res.string.settings_connected_to, "${viewModel.serverUrl}$versionText")
                                }
                                is ConnectionStatus.Error -> stringResource(Res.string.settings_connection_failed)
                                else -> {
                                    if (isTesting) stringResource(Res.string.settings_connecting)
                                    else if (viewModel.serverUrl.isNotBlank()) stringResource(Res.string.settings_credentials_configured)
                                    else stringResource(Res.string.settings_configure_server_address)
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
                                label = { Text(stringResource(Res.string.settings_server_url)) },
                                placeholder = { Text(stringResource(Res.string.settings_server_url_placeholder)) },
                                leadingIcon = {
                                    Icon(Tabler.Outline.Globe, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (viewModel.serverUrl.isNotBlank() && !isTesting) {
                                        IconButton(
                                            onClick = { viewModel.onServerUrlChanged("") },
                                            modifier = Modifier.focusIndicator(CircleShape),
                                        ) {
                                            Icon(Tabler.Outline.X, contentDescription = stringResource(Res.string.settings_clear_cd))
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
                                    Text(stringResource(Res.string.settings_seerr_api_key_method))
                                }
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                    onClick = { viewModel.onAuthMethodChanged(SeerrAuthMethod.JELLYFIN) },
                                    selected = viewModel.authMethod == SeerrAuthMethod.JELLYFIN,
                                    icon = {},
                                ) {
                                    Text(stringResource(Res.string.settings_seerr_jellyfin_method))
                                }
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                    onClick = { viewModel.onAuthMethodChanged(SeerrAuthMethod.LOCAL) },
                                    selected = viewModel.authMethod == SeerrAuthMethod.LOCAL,
                                    icon = {},
                                ) {
                                    Text(stringResource(Res.string.settings_seerr_local_method))
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
                                    modifier = Modifier.weight(1f).focusIndicator(),
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
                                            SeerrAuthMethod.API_KEY -> stringResource(Res.string.settings_test_connection)
                                            SeerrAuthMethod.JELLYFIN,
                                            SeerrAuthMethod.LOCAL -> stringResource(Res.string.settings_seerr_sign_in)
                                        }
                                    )
                                }

                                if (isConnected) {
                                    OutlinedButton(
                                        onClick = { viewModel.disconnect() },
                                        modifier = Modifier.focusIndicator(),
                                        shape = ShapeCache.smooth16,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Text(stringResource(Res.string.settings_disconnect))
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
                            title = stringResource(Res.string.settings_integration_features),
                            summary = {
                                if (preferences.enabled) {
                                    val count = listOf(
                                        preferences.searchEnabled,
                                        preferences.recommendationsEnabled,
                                        preferences.discoverEnabled
                                    ).count { it } + 1
                                    stringResource(Res.string.settings_features_active, count)
                                } else {
                                    stringResource(Res.string.settings_disabled)
                                }
                            },
                            modifier = Modifier.padding(vertical = 4.dp),
                            initiallyExpanded = preferences.enabled || highlightSettingId == "seerr_settings",
                        ) {
                            val showSubFeatures = preferences.enabled
                            val featTotal = if (showSubFeatures) 4 else 1

                            SettingToggleItem(
                                icon = Tabler.Outline.Puzzle,
                                title = stringResource(Res.string.settings_enable_seerr_integration),
                                subtitle = stringResource(Res.string.settings_enable_seerr_subtitle),
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
                                        title = stringResource(Res.string.settings_search_integration),
                                        subtitle = stringResource(Res.string.settings_search_integration_subtitle),
                                        checked = preferences.searchEnabled,
                                        index = 1,
                                        count = 4,
                                        onCheckedChange = viewModel::setSearchEnabled,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Sparkles,
                                        title = stringResource(Res.string.settings_recommendations_similar),
                                        subtitle = stringResource(Res.string.settings_recommendations_subtitle),
                                        checked = preferences.recommendationsEnabled,
                                        index = 2,
                                        count = 4,
                                        onCheckedChange = viewModel::setRecommendationsEnabled,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Compass,
                                        title = stringResource(Res.string.settings_advanced_discover),
                                        subtitle = stringResource(Res.string.settings_advanced_discover_subtitle),
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
                                        title = stringResource(Res.string.settings_trending),
                                        subtitle = stringResource(Res.string.settings_trending_subtitle),
                                        checked = preferences.discoverTrending,
                                        index = 0,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverTrending,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.Movie,
                                        title = stringResource(Res.string.settings_popular_movies),
                                        subtitle = stringResource(Res.string.settings_popular_movies_subtitle),
                                        checked = preferences.discoverPopularMovies,
                                        index = 1,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverPopularMovies,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.DeviceTv,
                                        title = stringResource(Res.string.settings_popular_series),
                                        subtitle = stringResource(Res.string.settings_popular_series_subtitle),
                                        checked = preferences.discoverPopularTv,
                                        index = 2,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverPopularTv,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.CalendarTime,
                                        title = stringResource(Res.string.settings_upcoming_movies),
                                        subtitle = stringResource(Res.string.settings_upcoming_movies_subtitle),
                                        checked = preferences.discoverUpcomingMovies,
                                        index = 3,
                                        count = discTotal,
                                        onCheckedChange = viewModel::setDiscoverUpcomingMovies,
                                    )
                                    SettingToggleItem(
                                        icon = Tabler.Outline.CalendarEvent,
                                        title = stringResource(Res.string.settings_upcoming_series),
                                        subtitle = stringResource(Res.string.settings_upcoming_series_subtitle),
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
                            title = stringResource(Res.string.settings_content_regions),
                            summary = {
                                val streamingLabel = regionNameByCode[preferences.streamingRegion]?.substringAfter(" ") ?: preferences.streamingRegion
                                val discoverLabel = regionNameByCode[preferences.discoverRegion]?.substringAfter(" ") ?: preferences.discoverRegion
                                stringResource(Res.string.settings_content_regions_summary, streamingLabel, discoverLabel)
                            },
                            modifier = Modifier.padding(vertical = 4.dp),
                            initiallyExpanded = true,
                        ) {
                            val streamingFlagAndName = regionNameByCode[preferences.streamingRegion] ?: preferences.streamingRegion
                            val discoverFlagAndName = regionNameByCode[preferences.discoverRegion] ?: preferences.discoverRegion
                            // Resolve region picker titles in composable scope; the
                            // onClick lambdas below are not composable.
                            val streamingRegionTitle = stringResource(Res.string.settings_streaming_region)
                            val discoverRegionTitle = stringResource(Res.string.settings_discover_region)

                            SettingListItem(
                                icon = Tabler.Outline.DeviceTv,
                                title = streamingRegionTitle,
                                subtitle = stringResource(Res.string.settings_streaming_region_subtitle),
                                trailingText = streamingFlagAndName,
                                index = 0,
                                count = 2,
                                onClick = {
                                    activeDialog = PickerState.List(
                                        title = streamingRegionTitle,
                                        items = regionCodes,
                                        label = { code -> regionNameByCode[code] ?: code },
                                        isSelected = { it == preferences.streamingRegion },
                                        onSelect = { viewModel.setStreamingRegion(it) },
                                    )
                                },
                            )
                            SettingListItem(
                                icon = Tabler.Outline.Compass,
                                title = discoverRegionTitle,
                                subtitle = stringResource(Res.string.settings_discover_region_subtitle),
                                trailingText = discoverFlagAndName,
                                index = 1,
                                count = 2,
                                onClick = {
                                    activeDialog = PickerState.List(
                                        title = discoverRegionTitle,
                                        items = regionCodes,
                                        label = { code -> regionNameByCode[code] ?: code },
                                        isSelected = { it == preferences.discoverRegion },
                                        onSelect = { viewModel.setDiscoverRegion(it) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    SettingsPickerDialog(
        state = activeDialog,
        onDismiss = { activeDialog = null },
    )
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
                text = stringResource(Res.string.settings_seerr_request_hub),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.settings_seerr_header_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyFields(viewModel: SeerrSettingsViewModel) {
    PasswordTextField(
        value = viewModel.apiKey,
        onValueChange = viewModel::onApiKeyChanged,
        label = { Text(stringResource(Res.string.settings_api_key_label)) },
        placeholder = { Text(stringResource(Res.string.settings_api_key_placeholder)) },
        leadingIcon = { Icon(Tabler.Outline.Key, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !viewModel.isTesting,
        shape = ShapeCache.smooth16,
    )
}

@Composable
private fun JellyfinAuthFields(viewModel: SeerrSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = viewModel.username,
            onValueChange = viewModel::onUsernameChanged,
            label = { Text(stringResource(Res.string.settings_username_label)) },
            placeholder = { Text(stringResource(Res.string.settings_jellyfin_username_placeholder)) },
            leadingIcon = {
                Icon(Tabler.Outline.User, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isTesting,
            shape = ShapeCache.smooth16,
        )
        PasswordTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text(stringResource(Res.string.settings_password_label)) },
            placeholder = { Text(stringResource(Res.string.settings_jellyfin_password_placeholder)) },
            leadingIcon = { Icon(Tabler.Outline.Lock, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isTesting,
            shape = ShapeCache.smooth16,
        )
    }
}

@Composable
private fun LocalAuthFields(viewModel: SeerrSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text(stringResource(Res.string.settings_email_label)) },
            placeholder = { Text(stringResource(Res.string.settings_seerr_email_placeholder)) },
            leadingIcon = {
                Icon(Tabler.Outline.Mail, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isTesting,
            shape = ShapeCache.smooth16,
        )
        PasswordTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text(stringResource(Res.string.settings_password_label)) },
            placeholder = { Text(stringResource(Res.string.settings_seerr_password_placeholder)) },
            leadingIcon = { Icon(Tabler.Outline.Lock, contentDescription = null) },
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
            stringResource(Res.string.settings_connected),
            if (status.version.isNotBlank()) stringResource(Res.string.settings_server_reached_version, status.version) else stringResource(Res.string.settings_seerr_server_reached),
            StatusColors.success
        )
        is ConnectionStatus.Error -> Quadruple(
            Tabler.Outline.AlertTriangle,
            stringResource(Res.string.settings_connection_failed_title),
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

private val regionNameByCode: Map<String, String> = COMMON_REGIONS.associate { it.first to it.second }
private val regionCodes: List<String> = COMMON_REGIONS.map { it.first }
