package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val context = LocalContext.current

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(viewModel.isLoading, viewModel.licenses.size) {
        if (isTv && !viewModel.isLoading && viewModel.licenses.isNotEmpty()) {
            for (attempt in 1..3) {
                androidx.compose.runtime.withFrameNanos { }
                if (focusRequester.tryRequestFocus("licenses_init")) break
            }
        }
    }

    JellyPlayScreenScaffold(
        title = "Open Source Licenses",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) {
        when {
            viewModel.isLoading -> LoadingScreen()
            viewModel.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = viewModel.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .tvFocusRestorer()
                        .focusRequester(focusRequester),
                    contentPadding = PaddingValues(
                        start = adaptiveInfo.contentPadding(isTv),
                        end = adaptiveInfo.contentPadding(isTv),
                        top = 8.dp,
                        bottom = adaptiveInfo.bottomPadding(isTv),
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        count = viewModel.licenses.size,
                        key = { viewModel.licenses[it].name },
                    ) { index ->
                        val license = viewModel.licenses[index]
                        LicenseRow(
                            name = license.name,
                            licenseType = license.license,
                            version = license.version,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseRow(
    name: String,
    licenseType: String,
    version: String,
) {
    val isTv = LocalTvMode.current
    val rowModifier = if (isTv) {
        Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth8)
            .clickable(onClick = {})
            .padding(horizontal = 16.dp, vertical = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    }
    Column(
        modifier = rowModifier,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (version.isNotBlank() || licenseType.isNotBlank()) {
            Text(
                text = buildString {
                    if (version.isNotBlank()) append(version)
                    if (version.isNotBlank() && licenseType.isNotBlank()) append(" · ")
                    if (licenseType.isNotBlank()) append(licenseType)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}
