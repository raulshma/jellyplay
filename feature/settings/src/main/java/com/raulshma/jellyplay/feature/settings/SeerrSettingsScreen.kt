package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.FancyTransitionEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.feature.settings.SeerrSettingsViewModel.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SeerrSettingsScreen(
    onBack: () -> Unit,
    viewModel: SeerrSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsState()
    val connectionStatus = viewModel.connectionStatus
    val isTesting = viewModel.isTesting
    val isConnected = connectionStatus is ConnectionStatus.Connected

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text("Seerr") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tvFocusable()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = if (LocalTvMode.current) 80.dp else 16.dp,
                end = if (LocalTvMode.current) 80.dp else 16.dp,
                bottom = 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── Connection Section ──
            item {
                SectionHeader(title = "Connection")
            }

            item {
                OutlinedTextField(
                    value = viewModel.serverUrl,
                    onValueChange = viewModel::onServerUrlChanged,
                    label = { Text("Server URL") },
                    placeholder = { Text("http://localhost:5055") },
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting,
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
            }

            item {
                OutlinedTextField(
                    value = viewModel.apiKey,
                    onValueChange = viewModel::onApiKeyChanged,
                    label = { Text("API Key") },
                    placeholder = { Text("Enter your Seerr API key") },
                    leadingIcon = {
                        Icon(Icons.Default.VpnKey, contentDescription = null)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting,
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { viewModel.testConnection() },
                        enabled = !isTesting && viewModel.serverUrl.isNotBlank() && viewModel.apiKey.isNotBlank(),
                        modifier = Modifier.tvFocusable(),
                        shape = ShapeCache.smooth12,
                    ) {
                        if (isTesting) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }

                    if (isConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnect() },
                            modifier = Modifier.tvFocusable(),
                            shape = ShapeCache.smooth12,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            // ── Connection Status ──
            item {
                AnimatedVisibility(
                    visible = connectionStatus is ConnectionStatus.Connected || connectionStatus is ConnectionStatus.Error,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + expandVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) + shrinkVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                ) {
                    when (connectionStatus) {
                        is ConnectionStatus.Connected -> ConnectionStatusCard(
                            icon = Icons.Default.Check,
                            title = "Connected",
                            subtitle = if (connectionStatus.version.isNotBlank()) "Version ${connectionStatus.version}" else "Seerr server reached",
                            color = StatusColors.success,
                        )
                        is ConnectionStatus.Error -> ConnectionStatusCard(
                            icon = Icons.Default.Close,
                            title = "Connection Failed",
                            subtitle = connectionStatus.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> {}
                    }
                }
            }

            // ── Feature Toggles (only when connected) ──
            if (isConnected) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(title = "Features")
                }

                item {
                    FeatureToggle(
                        icon = Icons.Default.Extension,
                        title = "Enable Seerr Integration",
                        subtitle = "Connect your Seerr server with JellyPlay",
                        checked = preferences.enabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }

                item {
                    FeatureToggle(
                        icon = Icons.Default.Search,
                        title = "Search Integration",
                        subtitle = "Request media directly from app search",
                        checked = preferences.searchEnabled,
                        onCheckedChange = viewModel::setSearchEnabled,
                        featureEnabled = preferences.enabled,
                    )
                }

                item {
                    FeatureToggle(
                        icon = Icons.Default.Extension,
                        title = "Recommendations & Similar",
                        subtitle = "Show Seerr recommendations on media detail screens",
                        checked = preferences.recommendationsEnabled,
                        onCheckedChange = viewModel::setRecommendationsEnabled,
                        featureEnabled = preferences.enabled,
                    )
                }

                item {
                    FeatureToggle(
                        icon = Icons.Default.Extension,
                        title = "Advance Discover",
                        subtitle = "Show Trending, Popular & Upcoming content on the home screen",
                        checked = preferences.discoverEnabled,
                        onCheckedChange = viewModel::setDiscoverEnabled,
                        featureEnabled = preferences.enabled,
                    )
                }

                if (preferences.discoverEnabled) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                            .padding(start = 32.dp),
                        ) {
                            FeatureToggle(
                                icon = Icons.Default.Extension,
                                title = "Trending",
                                subtitle = "Trending movies and series",
                                checked = preferences.discoverTrending,
                                onCheckedChange = viewModel::setDiscoverTrending,
                                featureEnabled = preferences.discoverEnabled,
                            )
                            FeatureToggle(
                                icon = Icons.Default.Extension,
                                title = "Popular Movies",
                                subtitle = "Popular movies on TMDB",
                                checked = preferences.discoverPopularMovies,
                                onCheckedChange = viewModel::setDiscoverPopularMovies,
                                featureEnabled = preferences.discoverEnabled,
                            )
                            FeatureToggle(
                                icon = Icons.Default.Extension,
                                title = "Popular Series",
                                subtitle = "Popular TV series on TMDB",
                                checked = preferences.discoverPopularTv,
                                onCheckedChange = viewModel::setDiscoverPopularTv,
                                featureEnabled = preferences.discoverEnabled,
                            )
                            FeatureToggle(
                                icon = Icons.Default.Extension,
                                title = "Upcoming Movies",
                                subtitle = "Movies coming soon to theaters",
                                checked = preferences.discoverUpcomingMovies,
                                onCheckedChange = viewModel::setDiscoverUpcomingMovies,
                                featureEnabled = preferences.discoverEnabled,
                            )
                            FeatureToggle(
                                icon = Icons.Default.Extension,
                                title = "Upcoming Series",
                                subtitle = "TV series coming soon",
                                checked = preferences.discoverUpcomingTv,
                                onCheckedChange = viewModel::setDiscoverUpcomingTv,
                                featureEnabled = preferences.discoverEnabled,
                            )
                        }
                    }
                }

                // ── Region Settings ──
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(title = "Regions")
                }

                item {
                    RegionSelector(
                        label = "Streaming Region",
                        subtitle = "Region used to determine available streaming providers",
                        selectedRegion = preferences.streamingRegion,
                        onRegionChange = viewModel::setStreamingRegion,
                    )
                }

                item {
                    RegionSelector(
                        label = "Discover Region",
                        subtitle = "Region used for release dates and content discovery",
                        selectedRegion = preferences.discoverRegion,
                        onRegionChange = viewModel::setDiscoverRegion,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun ConnectionStatusCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = ShapeCache.smooth12,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.8f),
                )
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionSelector(
    label: String,
    subtitle: String,
    selectedRegion: String,
    onRegionChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = COMMON_REGIONS.find { it.first == selectedRegion }?.second ?: selectedRegion

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .tvFocusable(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                COMMON_REGIONS.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onRegionChange(code)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    featureEnabled: Boolean = true,
) {
    val alpha = if (featureEnabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .tvFocusable(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = featureEnabled,
            modifier = Modifier.tvFocusable(),
        )
    }
}
