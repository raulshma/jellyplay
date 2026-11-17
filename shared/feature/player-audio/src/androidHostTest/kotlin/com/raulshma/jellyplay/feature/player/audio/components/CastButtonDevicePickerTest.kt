package com.raulshma.jellyplay.feature.player.audio.components

import android.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Robolectric Compose-UI regression for the cast device picker self-dismiss:
 * the platform dialog lived inside a `DisposableEffect(devices)` whose
 * teardown dismissed the superseded dialog on every discovered-devices
 * content change, and the dialog's dismiss listener echoed that teardown back
 * into composition state (`showDialog = false`) — so the picker closed
 * whenever discovery churned, and the "No devices found" dialog dismissed
 * itself the moment the first device arrived (the empty→populated upgrade
 * could never render).
 *
 * The invariant: a devices-list change must REPLACE the dialog in place
 * without propagating onDismiss; only a USER dismissal (Cancel here) may
 * reach composition state. The user path is asserted through the one
 * externally-visible consequence of `showDialog` resetting: a later devices
 * change must not resurrect the closed picker.
 */
@RunWith(AndroidJUnit4::class)
class CastButtonDevicePickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Minimal fake over the [AudioPlayerCast] seam the picker reads. */
    private class FakeAudioPlayerCast : AudioPlayerCast {
        val devices = MutableStateFlow<List<String>>(emptyList())

        override val isConnected: StateFlow<Boolean> = MutableStateFlow(false)
        override val discoveredDeviceNames: StateFlow<List<String>> = devices

        override fun startDiscovery() = Unit
        override fun stopDiscovery() = Unit
        override fun connect(deviceName: String) = Unit
        override fun disconnect() = Unit
        override fun acquireConsumer() = Unit
        override fun releaseConsumer() = Unit
        override fun loadMedia(itemId: String, startPositionMs: Long) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setVolume(volume: Float) = Unit
    }

    @Test
    fun devicesArrivingAfterOpen_replacesDialogInPlace_withoutClosing() {
        val cast = FakeAudioPlayerCast()
        composeTestRule.setContent {
            MaterialTheme { CastButton(cast) }
        }

        // Open the picker before discovery has found anything — the
        // "No devices found" dialog.
        composeTestRule.onNodeWithContentDescription("Cast").performClick()
        composeTestRule.waitForIdle()
        val emptyDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(emptyDialog)
        assertTrue(emptyDialog.isShowing)

        // Discovery emits its first devices: the superseded dialog's teardown
        // must NOT propagate a dismissal — the replacement dialog must be up,
        // listing the devices.
        cast.devices.value = listOf("Living Room TV", "Bedroom Speaker")
        composeTestRule.waitForIdle()

        val populatedDialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(populatedDialog)
        assertTrue(
            populatedDialog.isShowing,
            "picker closed itself when the devices list changed",
        )
        assertEquals(
            listOf("Living Room TV", "Bedroom Speaker"),
            shadowOf(populatedDialog).items.map { it.toString() },
        )
    }

    @Test
    fun userCancel_propagatesDismissIntoCompositionState() {
        val cast = FakeAudioPlayerCast().apply {
            devices.value = listOf("Living Room TV")
        }
        composeTestRule.setContent {
            MaterialTheme { CastButton(cast) }
        }
        composeTestRule.onNodeWithContentDescription("Cast").performClick()
        composeTestRule.waitForIdle()
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)

        ShadowAlertDialog.getLatestAlertDialog()
            .getButton(AlertDialog.BUTTON_NEGATIVE)
            .performClick()
        composeTestRule.waitForIdle()
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)

        // The Cancel dismissal reached composition state (showDialog reset —
        // the picker block unmounted), so a later devices change must not
        // resurrect the picker.
        cast.devices.value = listOf("Kitchen Display")
        composeTestRule.waitForIdle()
        assertFalse(
            ShadowAlertDialog.getLatestAlertDialog().isShowing,
            "picker was resurrected by a devices change after a user dismissal",
        )
    }
}
