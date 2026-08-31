package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.isExperimentalEnabled
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExperimentalSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: ExperimentalSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = rememberScreenBackgroundColorState()

    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    val features = ExperimentalFeatures.all
    val scrollIndex = remember(highlightSettingId, features) {
        features.indexOfFirst { it.feature.name == highlightSettingId }.let { if (it >= 0) it + 1 else -1 }
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
        tag = "experimental_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_experimental_title),
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
            item(key = "experimental_disclaimer") {
                ExperimentalDisclaimer(modifier = Modifier.padding(top = 8.dp))
            }

            item(key = "experimental_features_group") {
                SettingsGroup(
                    icon = Tabler.Outline.Flask,
                    title = stringResource(R.string.settings_features),
                    summary = {
                        val enabled = preferences.enabledExperimentalFeatures.size
                        if (enabled == 0) stringResource(R.string.settings_no_experimental_enabled) else stringResource(R.string.settings_experimental_count, enabled)
                    },
                    initiallyExpanded = true,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    features.forEachIndexed { index, info ->
                        val enabled = preferences.isExperimentalEnabled(info.feature)
                        SettingToggleItem(
                            icon = info.icon,
                            title = stringResource(info.titleRes),
                            subtitle = stringResource(info.subtitleRes),
                            checked = enabled,
                            index = index,
                            count = features.size,
                            highlighted = highlightSettingId == info.feature.name,
                            onCheckedChange = { viewModel.setExperimentalFeatureEnabled(info.feature, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExperimentalDisclaimer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Tabler.Outline.InfoCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.settings_features_in_development),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.settings_experimental_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
