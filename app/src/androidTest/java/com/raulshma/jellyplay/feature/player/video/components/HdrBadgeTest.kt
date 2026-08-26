package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HdrBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hdrBadge_nullType_notDisplayed() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = null)
            }
        }
        composeTestRule.onNodeWithText("HDR10").assertDoesNotExist()
    }

    @Test
    fun hdrBadge_hdr10_showsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "HDR10")
            }
        }
        composeTestRule.onNodeWithText("HDR10").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_hdr_showsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "HDR")
            }
        }
        composeTestRule.onNodeWithText("HDR").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_hlg_showsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "HLG")
            }
        }
        composeTestRule.onNodeWithText("HLG").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_dolbyVision_showsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "DolbyVision")
            }
        }
        composeTestRule.onNodeWithText("Dolby Vision").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_dolbyVisionUnderscore_showsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "dolby_vision")
            }
        }
        composeTestRule.onNodeWithText("Dolby Vision").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_dovi_showsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "dovi")
            }
        }
        composeTestRule.onNodeWithText("Dolby Vision").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_hdr10Plus_showsLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "HDR10Plus")
            }
        }
        composeTestRule.onNodeWithText("HDR10+").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_unknownType_showsUppercase() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = "something_custom")
            }
        }
        composeTestRule.onNodeWithText("SOMETHING_CUSTOM").assertIsDisplayed()
    }

    @Test
    fun hdrBadge_sdrNull_skipsRender() {
        composeTestRule.setContent {
            MaterialTheme {
                HdrBadge(hdrType = null)
            }
        }
        composeTestRule.onNodeWithText("SDR").assertDoesNotExist()
    }
}
