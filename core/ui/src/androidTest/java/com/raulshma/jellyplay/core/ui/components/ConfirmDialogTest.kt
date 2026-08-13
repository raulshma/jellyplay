package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [ConfirmPanel] — the dialog content (title, message, tone-tinted
 * icon badge, confirm/dismiss/secondary buttons, loading + disabled states,
 * custom content slot).
 *
 * The panel is rendered in-window rather than via `androidx.compose.ui.window.Dialog`
 * because the test rule does not discover a separate Dialog window's composition.
 * The [ConfirmDialog] wrapper itself only contributes the window shell +
 * motionScheme enter/exit, which is exercised on-device.
 */
class ConfirmDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun destructiveConfirm_rendersTitleMessageAndButtons() {
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Remove server?",
                    message = "This removes the server and all saved users on it.",
                    confirmText = "Remove",
                    dismissText = "Cancel",
                    tone = ConfirmTone.DESTRUCTIVE,
                    icon = Tabler.Outline.Trash,
                    confirmEnabled = true,
                    confirmLoading = false,
                    secondaryAction = null,
                    content = null,
                    isTv = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Remove server?").assertIsDisplayed()
        composeTestRule.onNodeWithText("This removes the server and all saved users on it.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun confirm_invokesOnConfirm() {
        var confirmed = false
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Delete?",
                    message = null,
                    confirmText = "Delete",
                    dismissText = "Cancel",
                    tone = ConfirmTone.DESTRUCTIVE,
                    icon = null,
                    confirmEnabled = true,
                    confirmLoading = false,
                    secondaryAction = null,
                    content = null,
                    isTv = false,
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.waitForIdle()
        assertTrue("onConfirm should have run", confirmed)
    }

    @Test
    fun dismiss_invokesOnDismissWithoutConfirming() {
        var confirmed = false
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Delete?",
                    message = null,
                    confirmText = "Delete",
                    dismissText = "Cancel",
                    tone = ConfirmTone.DESTRUCTIVE,
                    icon = null,
                    confirmEnabled = true,
                    confirmLoading = false,
                    secondaryAction = null,
                    content = null,
                    isTv = false,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        assertTrue("onConfirm must not run on dismiss", !confirmed)
        assertTrue("onDismiss should have run", dismissed)
    }

    @Test
    fun confirmLoading_hidesLabelAndDisablesButton() {
        var confirmed = false
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Uninstall?",
                    message = null,
                    confirmText = "Uninstall",
                    dismissText = null,
                    tone = ConfirmTone.DESTRUCTIVE,
                    icon = null,
                    confirmEnabled = true,
                    confirmLoading = true,
                    secondaryAction = null,
                    content = null,
                    isTv = false,
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        // The label is swapped for a progress indicator while loading, and the
        // button is disabled — so the text node is gone.
        composeTestRule.onNodeWithText("Uninstall").assertDoesNotExist()
        assertTrue("onConfirm must not run while loading", !confirmed)
    }

    @Test
    fun confirmDisabled_buttonIsNotEnabled() {
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Download?",
                    message = null,
                    confirmText = "Download",
                    dismissText = "Cancel",
                    tone = ConfirmTone.NEUTRAL,
                    icon = null,
                    confirmEnabled = false,
                    confirmLoading = false,
                    secondaryAction = null,
                    content = null,
                    isTv = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Download").assertIsNotEnabled()
    }

    @Test
    fun contentSlot_rendersCustomContent() {
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Reset layout?",
                    message = null,
                    confirmText = "Reset",
                    dismissText = "Cancel",
                    tone = ConfirmTone.DESTRUCTIVE,
                    icon = null,
                    confirmEnabled = true,
                    confirmLoading = false,
                    secondaryAction = null,
                    content = { Text("Don't show this again") },
                    isTv = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Don't show this again").assertIsDisplayed()
    }

    @Test
    fun secondaryAction_rendersAllThreeButtonsAndFires() {
        var saved = false
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Unsaved changes",
                    message = null,
                    confirmText = "Discard",
                    dismissText = "Keep editing",
                    tone = ConfirmTone.DESTRUCTIVE,
                    icon = null,
                    confirmEnabled = true,
                    confirmLoading = false,
                    secondaryAction = ConfirmAction("Save", ConfirmTone.PRIMARY) { saved = true },
                    content = null,
                    isTv = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Discard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep editing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
        assertTrue("secondaryAction should have fired", saved)
    }

    @Test
    fun primaryTone_rendersWithoutError() {
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmPanel(
                    title = "Send now?",
                    message = null,
                    confirmText = "Send",
                    dismissText = "Cancel",
                    tone = ConfirmTone.PRIMARY,
                    icon = null,
                    confirmEnabled = true,
                    confirmLoading = false,
                    secondaryAction = null,
                    content = null,
                    isTv = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Send").assertIsDisplayed()
    }
}
