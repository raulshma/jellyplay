package com.raulshma.jellyplay.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.cast.CastDevice
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

/**
 * Pure-state device picker for the global "Play On" entry. Lists Jellyfin
 * remote sessions (other controllable JellyPlay / Jellyfin clients) discovered
 * by [CastManager]. Driven entirely by callbacks; owns no state itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayOnDeviceSheet(
    devices: List<CastDevice>,
    onSelect: (CastDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    com.raulshma.jellyplay.core.ui.components.TvSafeSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            com.raulshma.jellyplay.core.ui.components.SheetHeader(
                title = stringResource(R.string.play_on_title),
                icon = Tabler.Outline.Devices,
                onClose = onDismiss,
            )

            when {
                devices.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.play_on_no_clients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(devices, key = { it.id }, contentType = { "playOnDevice" }) { device ->
                            DeviceRow(device = device, onClick = { onSelect(device) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: CastDevice,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.02f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .tvFocusIndicator(focusState, CircleShape)
            .then(focusState.focusModifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Tabler.Outline.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.play_on_jellyfin_session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Tabler.Outline.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
