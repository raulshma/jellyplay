package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.media3.common.util.UnstableApi
import com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue
import org.junit.Rule
import org.junit.Test

@OptIn(UnstableApi::class)
class AVSyncSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val previewCues = listOf(
        TimedCue(startTimeUs = 0L, endTimeUs = 1_000_000L, text = "Cue A"),
        TimedCue(startTimeUs = 2_000_000L, endTimeUs = 3_000_000L, text = "Cue B"),
        TimedCue(startTimeUs = 4_000_000L, endTimeUs = 5_000_000L, text = "Cue C"),
    )

    @Test
    fun avSyncSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("A/V Sync").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_showsBothSectionLabels() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Audio Delay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cue preview sync").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_zeroDelay_showsNeutralLabels() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        // "0.0s" appears on the audio delay row and the cue-preview offset
        // label — assert at least one rendered instance.
        composeTestRule.onAllNodesWithText("0.0s")[0].assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_positiveAudioDelay_showsPlusLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 1000L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("+1.0s").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_negativeSubtitleDelay_showsMinusLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = -1500L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        // "-1.5s" appears on the cue-preview offset label.
        composeTestRule.onAllNodesWithText("-1.5s")[0].assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_preview_showsUnavailableWhenNoCues() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Cue preview sync").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Subtitle preview is unavailable for this track (embedded or image-based subtitles can't be previewed here).")
            .assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_preview_showsCueStackWithPrevActiveNext() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                    activeSubtitleCues = previewCues,
                    playbackPositionMs = { 4_500L }, // 4.5s → Cue C active, B prev, next null
                )
            }
        }
        composeTestRule.onNodeWithTag("prev_cue").assertTextEquals("Cue B")
        composeTestRule.onNodeWithTag("active_cue").assertTextEquals("Cue C")
        composeTestRule.onNodeWithTag("next_cue").assertTextEquals("—")
    }

    @Test
    fun avSyncSheet_preview_timestampDefaultsToCurrentPosition() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                    activeSubtitleCues = previewCues,
                    playbackPositionMs = { 65_250L },
                )
            }
        }
        composeTestRule.onNodeWithText("01:05.250").assertIsDisplayed()
    }

    @Test
    fun avSyncSheet_preview_sliderDragUpdatesActiveCue() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                    activeSubtitleCues = listOf(
                        TimedCue(startTimeUs = 0L, endTimeUs = 1_000_000L, text = "Cue A"),
                        TimedCue(startTimeUs = 2_000_000L, endTimeUs = 3_000_000L, text = "Cue B"),
                        TimedCue(startTimeUs = 10_000_000L, endTimeUs = 11_000_000L, text = "Cue C"),
                        TimedCue(startTimeUs = 30_000_000L, endTimeUs = 31_000_000L, text = "Cue D"),
                    ),
                    playbackPositionMs = { 5_000L },
                )
            }
        }
        composeTestRule.onNodeWithTag("active_cue").assertTextEquals("—")

        // Fraction 0.925 of -30000..30000 → +25500 ms; adjusted position
        // 5s + 25.5s = 30.5s → Cue D becomes active.
        composeTestRule
            .onNodeWithTag("preview_offset_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.925f) }

        composeTestRule.onNodeWithTag("active_cue").assertTextEquals("Cue D")
        composeTestRule.onNodeWithTag("prev_cue").assertTextEquals("Cue C")
    }

    @Test
    fun avSyncSheet_preview_hiddenWhenSubtitleDelayUnsupported() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 0L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                    subtitleDelaySupported = false,
                )
            }
        }
        composeTestRule.onNodeWithText("Cue preview sync").assertDoesNotExist()
    }

    @Test
    fun avSyncSheet_showsResetBothButton() {
        composeTestRule.setContent {
            MaterialTheme {
                AVSyncSheet(
                    currentAudioDelayMs = 500L,
                    currentSubtitleDelayMs = 0L,
                    onAudioDelayChange = {},
                    onSubtitleDelayChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reset both").assertIsDisplayed()
    }
}
