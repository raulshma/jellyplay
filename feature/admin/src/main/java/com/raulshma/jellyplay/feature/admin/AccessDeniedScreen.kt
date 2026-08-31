package com.raulshma.jellyplay.feature.admin

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.LockAccess
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.admin.R

/**
 * Shared "you don't have permission" surface shown by [AdminRouteContainer]
 * when the current user is not an administrator. Matches the defense-in-depth
 * model: the server is the real gatekeeper (401/403), but the client presents
 * a clean denied state instead of an empty screen or a raw error string.
 *
 * Content is centered (both axes) with horizontally-centered text and edge
 * padding so the message reads cleanly at any width.
 */
@Composable
fun AccessDeniedScreen(
    onBack: () -> Unit,
) {
    val backgroundColorState = rememberScreenBackgroundColorState()
    // Static text only — on TV the box itself becomes the focus target so focus never
    // falls through to the navigation drawer rail.
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    if (isTv) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus("access_denied") }
    }
    JellyPlayScreenScaffold(
        title = stringResource(R.string.admin_access_denied_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTv) Modifier.focusRequester(focusRequester).focusable() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Tabler.Outline.LockAccess,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.admin_access_denied_body),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.admin_access_denied_contact),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
