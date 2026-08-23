package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.mikepenz.aboutlibraries.entity.Library
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ErrorScreen
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_license_text_unavailable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ok
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_open_source_licenses_title

@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    viewModel: LicensesViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = if (!viewModel.isLoading) viewModel.libraries.size else 0,
        tag = "licenses_init",
    )

    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_open_source_licenses_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) {
        when {
            viewModel.isLoading -> LoadingScreen()
            viewModel.error != null -> ErrorScreen(message = viewModel.error!!)
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
                        count = viewModel.libraries.size,
                        key = { viewModel.libraries[it].uniqueId },
                        contentType = { "license" },
                    ) { index ->
                        val library = viewModel.libraries[index]
                        LicenseRow(
                            library = library,
                            onClick = { selectedLibrary = library },
                        )
                    }
                }
            }
        }
    }

    selectedLibrary?.let { library ->
        LicenseDetailDialog(
            library = library,
            onDismiss = { selectedLibrary = null },
        )
    }
}

@Composable
private fun LicenseRow(
    library: Library,
    onClick: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val licenseId = library.licenses.firstOrNull()?.spdxId.orEmpty()
    val rowModifier = if (isTv) {
        Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth8)
            .focusIndicator()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    }
    Column(modifier = rowModifier) {
        Text(
            text = library.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!library.artifactVersion.isNullOrBlank() || licenseId.isNotBlank()) {
            Text(
                text = buildString {
                    if (!library.artifactVersion.isNullOrBlank()) append(library.artifactVersion)
                    if (!library.artifactVersion.isNullOrBlank() && licenseId.isNotBlank()) append(" · ")
                    if (licenseId.isNotBlank()) append(licenseId)
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

@Composable
private fun LicenseDetailDialog(
    library: Library,
    onDismiss: () -> Unit,
) {
    val license = library.licenses.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = license?.name ?: license?.spdxId
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            val content = license?.licenseContent
            if (!content.isNullOrBlank()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                Text(
                    text = stringResource(Res.string.settings_license_text_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_ok))
            }
        },
    )
}
