package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_arr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_arr_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_subtitles_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_integration
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_integration_subtitle

/**
 * Top-level integrations hub. Lists every third-party service JellyPlay talks
 * to (Seerr, Radarr/Sonarr) as drill-in cards. Each card navigates to its own
 * full settings screen ([SeerrSettingsScreen] / [ArrSettingsScreen]); this
 * screen is purely a directory.
 *
 * Mirrors the [ExperimentalSettingsScreen] layout (scaffold + grouped list) so
 * it reads as a sibling sub-page rather than a bespoke surface.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntegrationsScreen(
    onBack: () -> Unit,
    onSeerrSettings: () -> Unit,
    onArrSettings: () -> Unit,
    onSubtitleProviderSettings: () -> Unit,
    highlightSettingId: String? = null,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Two integrations: Seerr (index 0), Radarr/Sonarr (index 1). The header
    // item sits at index 0, so scroll targets are offset by 1.
    val scrollIndex = remember(highlightSettingId) {
        when (highlightSettingId) {
            "seerr_settings", "arr_settings" -> 2
            else -> -1
        }
    }

    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try { scrollState.animateScrollToItem(scrollIndex) } catch (_: Exception) {}
        }
    }

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "integrations_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_integrations_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { innerPadding ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "integrations_intro") {
                Text(
                    "Connect JellyPlay to your media services to enable requests, " +
                        "discover, and download-queue tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }

            item(key = "integrations_group") {
                SettingsGroup(
                    icon = Tabler.Outline.PlugConnected,
                    title = stringResource(Res.string.settings_integrations_title),
                    initiallyExpanded = true,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    val count = 3
                    SettingListItem(
                        icon = Tabler.Outline.Puzzle,
                        title = stringResource(Res.string.settings_seerr_integration),
                        subtitle = stringResource(Res.string.settings_seerr_integration_subtitle),
                        index = 0,
                        count = count,
                        highlighted = highlightSettingId == "seerr_settings",
                        onClick = onSeerrSettings,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Download,
                        title = stringResource(Res.string.settings_integrations_arr),
                        subtitle = stringResource(Res.string.settings_integrations_arr_subtitle),
                        index = 1,
                        count = count,
                        highlighted = highlightSettingId == "arr_settings",
                        onClick = onArrSettings,
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Subtitles,
                        title = stringResource(Res.string.settings_integrations_subtitles),
                        subtitle = stringResource(Res.string.settings_integrations_subtitles_subtitle),
                        index = 2,
                        count = count,
                        highlighted = highlightSettingId == "subtitle_provider_settings",
                        onClick = onSubtitleProviderSettings,
                    )
                }
            }
        }
    }
}
