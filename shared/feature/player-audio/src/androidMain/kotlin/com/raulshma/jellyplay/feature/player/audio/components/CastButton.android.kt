package com.raulshma.jellyplay.feature.player.audio.components

import android.app.AlertDialog
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.CastColors
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cancel
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cast
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cast_connected
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cast_no_devices
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cast_ok
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cast_to_device
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource

/**
 * Cast / "Play On" entry point for the audio player. Mirrors the video
 * [com.raulshma.jellyplay.feature.player.video.components.CastButton], but lives
 * in the audio module so it can be wired into [com.raulshma.jellyplay.feature.player.audio.AudioPlayerTopBar].
 *
 * Tapping opens a device picker built from [AudioPlayerCast.discoveredDeviceNames]
 * (merged across all registered cast strategies, including Jellyfin remote
 * sessions; the legacy CastManager reaches this actual app-side through the
 * AudioPlayerCast interop adapter).
 */
@Composable
actual fun CastButton(
    castController: AudioPlayerCast,
) {
    val isConnected by castController.isConnected.collectAsStateWithLifecycle(initialValue = false)
    val discoveredDevices by castController.discoveredDeviceNames.collectAsStateWithLifecycle(initialValue = emptyList())
    val castConnectedDesc = stringResource(Res.string.audio_cast_connected)
    val castDesc = stringResource(Res.string.audio_cast)
    var showDialog by remember { mutableStateOf(false) }
    val castFocusState = rememberTvFocusState(focusedScale = 1.1f)

    DisposableEffect(Unit) {
        castController.startDiscovery()
        onDispose {
            castController.stopDiscovery()
        }
    }

    if (showDialog) {
        CastDeviceDialog(
            devices = discoveredDevices,
            onSelect = { device ->
                castController.connect(device)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }

    IconButton(
        onClick = {
            if (isConnected) {
                castController.disconnect()
            } else {
                showDialog = true
            }
        },
        modifier = Modifier
            .size(40.dp)
            .then(castFocusState.focusModifier)
            .tvFocusIndicator(castFocusState, CircleShape),
    ) {
        Icon(
            imageVector = Tabler.Outline.Cast,
            contentDescription = if (isConnected) castConnectedDesc else castDesc,
            tint = if (isConnected) CastColors.connected else playerOnScrim(),
        )
    }
}

@Composable
private fun CastDeviceDialog(
    devices: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val castTitle = stringResource(Res.string.audio_cast)
    val castNoDevices = stringResource(Res.string.audio_cast_no_devices)
    val castOk = stringResource(Res.string.audio_cast_ok)
    val castToDevice = stringResource(Res.string.audio_cast_to_device)
    val castCancel = stringResource(Res.string.audio_cancel)

    DisposableEffect(devices) {
        val dialog = if (devices.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(castTitle)
                .setMessage(castNoDevices)
                .setPositiveButton(castOk) { dialog, _ -> dialog.dismiss(); onDismiss() }
                .setOnDismissListener { onDismiss() }
                .create()
        } else {
            val deviceNames = devices.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle(castToDevice)
                .setItems(deviceNames) { dialog, which ->
                    onSelect(devices[which])
                    dialog.dismiss()
                }
                .setNegativeButton(castCancel) { dialog, _ -> dialog.dismiss(); onDismiss() }
                .setOnDismissListener { onDismiss() }
                .create()
        }
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}
