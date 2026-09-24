package com.raulshma.jellyplay.feature.player.video.components

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrickplayOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun trickplayOverlay_displaysTimeForPosition() {
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    bitmap = null,
                    positionMs = 90_000L,
                )
            }
        }
        // With no bitmap the position renders twice — in the thumbnail
        // placeholder and in the caption label — so disambiguate like
        // AVSyncSheetTest does for its duplicated "0.0s".
        composeTestRule.onAllNodesWithText("01:30")[0].assertIsDisplayed()
    }

    @Test
    fun trickplayOverlay_displaysTimeWithHours() {
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    bitmap = null,
                    positionMs = 3_600_000L,
                )
            }
        }
        composeTestRule.onAllNodesWithText("1:00:00")[0].assertIsDisplayed()
    }

    @Test
    fun trickplayOverlay_zeroPosition_displaysZeroTime() {
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    bitmap = null,
                    positionMs = 0L,
                )
            }
        }
        composeTestRule.onAllNodesWithText("00:00")[0].assertIsDisplayed()
    }

    @Test
    fun trickplayOverlay_withBitmap_displaysTime() {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        composeTestRule.setContent {
            MaterialTheme {
                TrickplayOverlay(
                    bitmap = bitmap,
                    positionMs = 45_000L,
                )
            }
        }
        composeTestRule.onNodeWithText("00:45").assertIsDisplayed()
        bitmap.recycle()
    }
}
