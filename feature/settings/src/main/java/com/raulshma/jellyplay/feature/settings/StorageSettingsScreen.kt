package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val showAdvanced = preferences.showAdvancedSettings
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Storage",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Database,
                    title = "Storage",
                    summary = { "Cache: ${viewModel.cacheSizeMb} MB" },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val storageTotal = if (showAdvanced) 5 else 2
                    var storageIdx = 0
                    SettingInfoItem(
                        icon = Tabler.Outline.Database,
                        title = "Cache Used",
                        subtitle = "${viewModel.cacheSizeMb} MB",
                        index = storageIdx++, count = storageTotal,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Trash,
                        title = "Clear Cache",
                        subtitle = "Free up storage space",
                        index = storageIdx++, count = storageTotal,
                        onClick = { viewModel.clearCache() },
                    )
                    if (showAdvanced) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Wifi,
                            title = "WiFi Only",
                            subtitle = if (preferences.wifiOnlyDownloads) "Downloads only on unmetered networks" else "Downloads on any network",
                            checked = preferences.wifiOnlyDownloads,
                            index = storageIdx++, count = storageTotal,
                            onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Refresh,
                            title = "Auto-delete Cache",
                            subtitle = if (preferences.autoDeleteCache) "Automatically clears on low storage" else "Manual cache management",
                            checked = preferences.autoDeleteCache,
                            index = storageIdx++, count = storageTotal,
                            onCheckedChange = { viewModel.setAutoDeleteCache(it) },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Database,
                            title = "Max Cache Size",
                            subtitle = "Maximum disk space for caching",
                            trailingText = if (preferences.maxCacheSizeMb == 0) "Unlimited" else "${preferences.maxCacheSizeMb} MB",
                            index = storageIdx, count = storageTotal,
                            onClick = {
                                val sizes = listOf(0, 250, 500, 1000, 2000, 5000)
                                val currentIndex = sizes.indexOf(preferences.maxCacheSizeMb)
                                val nextIndex = (currentIndex + 1) % sizes.size
                                viewModel.setMaxCacheSize(sizes[nextIndex])
                            },
                        )
                    }
                }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = 3,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
    }
}
