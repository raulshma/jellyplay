package com.raulshma.jellyplay.feature.player.audio.components

import android.app.AlertDialog
import android.content.DialogInterface
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
            showDialog = showDialog,
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

/**
 * The Android platform (AlertDialog.Builder) device picker. The dialog is a
 * pure render of `(showDialog, devices)`: a USER dismissal (device tap,
 * Cancel / OK, back / outside touch) writes back into composition state via
 * [onSelect]/[onDismiss], but an EFFECT-TEARDOWN dismissal — the
 * [DisposableEffect] restarting because `devices` changed, or the dialog
 * leaving composition — must never write one. Discovery rewrites
 * [AudioPlayerCast.discoveredDeviceNames] on every mDNS/SSDP scan cycle and
 * sessions push, so propagating a teardown as a user dismissal would close
 * the picker mid-browse (and permanently: the empty→populated upgrade would
 * dismiss the picker before the first device ever rendered). The
 * `programmaticDismiss` flag separates the two: only a dismissal that did
 * not come from effect teardown may call [onDismiss].
 *
 * [showDialog] is passed solely as an effect key (belt-and-braces — the
 * caller composes this only under its `if (showDialog)`, so leaving
 * composition already disposes the effect; keying on it pins the effect
 * lifecycle to the wanted-state even if that guard is ever refactored).
 */
@Composable
private fun CastDeviceDialog(
    showDialog: Boolean,
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

    DisposableEffect(showDialog, devices) {
        var programmaticDismiss = false

        // The arms dismiss in place before reporting; the listener must not
        // echo that dismissal back as a user one.
        fun dismissDialog(dialog: DialogInterface) {
            programmaticDismiss = true
            dialog.dismiss()
        }

        val dialog = if (devices.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(castTitle)
                .setMessage(castNoDevices)
                .setPositiveButton(castOk) { dialog, _ -> dismissDialog(dialog); onDismiss() }
                .setOnDismissListener { if (!programmaticDismiss) onDismiss() }
                .create()
        } else {
            val deviceNames = devices.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle(castToDevice)
                .setItems(deviceNames) { dialog, which ->
                    onSelect(devices[which])
                    dismissDialog(dialog)
                }
                .setNegativeButton(castCancel) { dialog, _ -> dismissDialog(dialog); onDismiss() }
                .setOnDismissListener { if (!programmaticDismiss) onDismiss() }
                .create()
        }
        dialog.show()
        onDispose {
            programmaticDismiss = true
            dialog.dismiss()
        }
    }
}
