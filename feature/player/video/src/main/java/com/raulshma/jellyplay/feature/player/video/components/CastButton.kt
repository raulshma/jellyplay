package com.raulshma.jellyplay.feature.player.video.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.designsystem.theme.CastColors
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@Composable
internal fun CastButton(
    castManager: CastManager,
) {
    val context = LocalContext.current
    val isConnected by castManager.isConnectedFlow.collectAsStateWithLifecycle(initialValue = false)
    val discoveredDevices by castManager.discoveredDevices.collectAsStateWithLifecycle(initialValue = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    val castFocusState = rememberTvFocusState(focusedScale = 1.1f)

    DisposableEffect(Unit) {
        castManager.startDiscovery(context)
        onDispose {
            castManager.stopDiscovery()
        }
    }

    if (showDialog) {
        CastDeviceDialog(
            devices = discoveredDevices,
            onSelect = { device ->
                castManager.connect(context, device)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }

    IconButton(
        onClick = {
            if (isConnected) {
                castManager.disconnect(context)
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
            contentDescription = if (isConnected) "Cast connected" else "Cast",
            tint = if (isConnected) CastColors.connected else playerOnScrim(),
        )
    }
}

@Composable
private fun CastDeviceDialog(
    devices: List<com.raulshma.jellyplay.core.data.cast.CastDevice>,
    onSelect: (com.raulshma.jellyplay.core.data.cast.CastDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    DisposableEffect(devices) {
        val dialog = if (devices.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Cast")
                .setMessage("No cast devices found. Make sure your device is on the same network.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss(); onDismiss() }
                .setOnDismissListener { onDismiss() }
                .create()
        } else {
            val deviceNames = devices.map { it.name }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Cast to device")
                .setItems(deviceNames) { dialog, which ->
                    onSelect(devices[which])
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss(); onDismiss() }
                .setOnDismissListener { onDismiss() }
                .create()
        }
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}
