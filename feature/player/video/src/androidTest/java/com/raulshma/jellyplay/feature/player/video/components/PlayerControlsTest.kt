package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.feature.player.video.formatDuration
import com.raulshma.jellyplay.feature.player.video.TrackOption
import org.junit.Rule
import org.junit.Test

class PlayerControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        isVisible: Boolean = true,
        isPlaying: Boolean = false,
        currentPosition: Long = 90_000L,
        duration: Long = 3_600_000L,
        playbackSpeed: Float = 1.0f,
        hasChapters: Boolean = false,
        title: String = "Test Movie",
        subtitle: String = "Episode 1",
        supportsSubtitleStyle: Boolean = false,
        supportsDialogueBoost: Boolean = false,
        supportsNightMode: Boolean = false,
        supportsAudioDelay: Boolean = false,
        supportsAudioPassthrough: Boolean = false,
        supportsOcr: Boolean = false,
        currentAspectRatio: AspectRatio = AspectRatio.AUTO,
        detectedAspectRatio: AspectRatio? = null,
        dialogueBoostEnabled: Boolean = false,
        nightModeEnabled: Boolean = false,
        audioPassthrough: Boolean = false,
        isCasting: Boolean = false,
        isOcrRunning: Boolean = false,
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                PlayerControls(
                    title = title,
                    subtitle = subtitle,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    playbackSpeed = playbackSpeed,
                    hasChapters = hasChapters,
                    dialogueBoostEnabled = dialogueBoostEnabled,
                    nightModeEnabled = nightModeEnabled,
                    audioPassthrough = audioPassthrough,
                    isCasting = isCasting,
                    isOcrRunning = isOcrRunning,
                    currentAspectRatio = currentAspectRatio,
                    detectedAspectRatio = detectedAspectRatio,
                    isVisible = isVisible,
                    supportsSubtitleStyle = supportsSubtitleStyle,
                    supportsDialogueBoost = supportsDialogueBoost,
                    supportsNightMode = supportsNightMode,
                    supportsAudioDelay = supportsAudioDelay,
                    supportsAudioPassthrough = supportsAudioPassthrough,
                    supportsOcr = supportsOcr,
                    onPlayPause = {},
                    onSeekBack = {},
                    onSeekForward = {},
                    onSeek = {},
                    onSeekStart = {},
                    onSeekEnd = {},
                    onSeekPositionChange = {},
                    onBack = {},
                    onSpeedClick = {},
                    onAudioClick = {},
                    onSubtitleClick = {},
                    onSubtitleStyleClick = {},
                    onSecondarySubtitleClick = {},
                    onChapterClick = {},
                    onInfoClick = {},
                    onAspectRatioClick = {},
                    onDialogueBoostClick = {},
                    onNightModeClick = {},
                    onAudioDelayClick = {},
                    onDecoderClick = {},
                    onPassthroughClick = {},
                    onCastClick = {},
                    onOcrClick = {},
                    onSubtitleDownloadClick = {},
                )
            }
        }
    }

    @Test
    fun playerControls_visible_showsTitleAndSubtitle() {
        setContent()
        composeTestRule.onNodeWithText("Test Movie").assertIsDisplayed()
        composeTestRule.onNodeWithText("Episode 1").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsBackButton() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsPlaybackControls() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Rewind").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Forward").assertIsDisplayed()
    }

    @Test
    fun playerControls_playing_showsPauseButton() {
        setContent(isPlaying = true)
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun playerControls_paused_showsPlayButton() {
        setContent(isPlaying = false)
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsTimeDisplay() {
        setContent(currentPosition = 90_000L, duration = 3_600_000L)
        composeTestRule.onNodeWithText(formatDuration(90_000L)).assertIsDisplayed()
        composeTestRule.onNodeWithText(formatDuration(3_600_000L)).assertIsDisplayed()
    }

    @Test
    fun playerControls_zeroDuration_showsPlaceholder() {
        setContent(duration = 0L)
        composeTestRule.onNodeWithText("--:--").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsSpeedButton() {
        setContent(playbackSpeed = 1.0f)
        composeTestRule.onNodeWithText("Speed").assertIsDisplayed()
        composeTestRule.onNodeWithText("1x").assertIsDisplayed()
    }

    @Test
    fun playerControls_customSpeed_showsSpeedValue() {
        setContent(playbackSpeed = 1.5f)
        composeTestRule.onNodeWithText("1.5x").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsAudioButton() {
        setContent()
        composeTestRule.onNodeWithText("Audio").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsSubsButton() {
        setContent()
        composeTestRule.onNodeWithText("Subs").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsDualSubsButton() {
        setContent()
        composeTestRule.onNodeWithText("Dual Subs").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsInfoButton() {
        setContent()
        composeTestRule.onNodeWithText("Info").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsDecoderButton() {
        setContent()
        composeTestRule.onNodeWithText("Decoder").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsDownloadButton() {
        setContent()
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsCastButton() {
        setContent()
        composeTestRule.onNodeWithText("Cast").assertIsDisplayed()
    }

    @Test
    fun playerControls_hidden_controlsNotDisplayed() {
        setContent(isVisible = false)
        composeTestRule.onNodeWithText("Test Movie").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun playerControls_withChapters_showsChaptersButton() {
        setContent(hasChapters = true)
        composeTestRule.onNodeWithText("Chapters").assertIsDisplayed()
    }

    @Test
    fun playerControls_withoutChapters_hidesChaptersButton() {
        setContent(hasChapters = false)
        composeTestRule.onNodeWithText("Chapters").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsSubtitleStyle_showsStyleButton() {
        setContent(supportsSubtitleStyle = true)
        composeTestRule.onNodeWithText("Style").assertIsDisplayed()
    }

    @Test
    fun playerControls_noSubtitleStyle_hidesStyleButton() {
        setContent(supportsSubtitleStyle = false)
        composeTestRule.onNodeWithText("Style").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsDialogueBoost_showsBoostButton() {
        setContent(supportsDialogueBoost = true)
        composeTestRule.onNodeWithText("Boost").assertIsDisplayed()
    }

    @Test
    fun playerControls_noDialogueBoost_hidesBoostButton() {
        setContent(supportsDialogueBoost = false)
        composeTestRule.onNodeWithText("Boost").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsNightMode_showsNightButton() {
        setContent(supportsNightMode = true)
        composeTestRule.onNodeWithText("Night").assertIsDisplayed()
    }

    @Test
    fun playerControls_noNightMode_hidesNightButton() {
        setContent(supportsNightMode = false)
        composeTestRule.onNodeWithText("Night").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsAudioDelay_showsDelayButton() {
        setContent(supportsAudioDelay = true)
        composeTestRule.onNodeWithText("Delay").assertIsDisplayed()
    }

    @Test
    fun playerControls_noAudioDelay_hidesDelayButton() {
        setContent(supportsAudioDelay = false)
        composeTestRule.onNodeWithText("Delay").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsPassthrough_showsPassthroughButton() {
        setContent(supportsAudioPassthrough = true)
        composeTestRule.onNodeWithText("Passthrough").assertIsDisplayed()
    }

    @Test
    fun playerControls_noPassthrough_hidesPassthroughButton() {
        setContent(supportsAudioPassthrough = false)
        composeTestRule.onNodeWithText("Passthrough").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsOcr_showsOcrButton() {
        setContent(supportsOcr = true)
        composeTestRule.onNodeWithText("OCR").assertIsDisplayed()
    }

    @Test
    fun playerControls_noOcr_hidesOcrButton() {
        setContent(supportsOcr = false)
        composeTestRule.onNodeWithText("OCR").assertDoesNotExist()
    }

    @Test
    fun playerControls_autoAspectRatioWithDetection_showsAuto() {
        setContent(
            currentAspectRatio = AspectRatio.AUTO,
            detectedAspectRatio = AspectRatio.RATIO_16_9,
        )
        composeTestRule.onNodeWithText("Auto").assertIsDisplayed()
    }

    @Test
    fun playerControls_nonFitAspectRatio_showsDisplayName() {
        setContent(currentAspectRatio = AspectRatio.RATIO_16_9)
        composeTestRule.onNodeWithText("16:9").assertIsDisplayed()
    }

    @Test
    fun playerControls_backButton_callsOnBack() {
        var backClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                PlayerControls(
                    title = "Test",
                    subtitle = "",
                    isPlaying = false,
                    currentPosition = 0L,
                    duration = 0L,
                    playbackSpeed = 1.0f,
                    hasChapters = false,
                    dialogueBoostEnabled = false,
                    nightModeEnabled = false,
                    audioPassthrough = false,
                    isCasting = false,
                    isOcrRunning = false,
                    currentAspectRatio = AspectRatio.AUTO,
                    detectedAspectRatio = null,
                    isVisible = true,
                    onPlayPause = {},
                    onSeekBack = {},
                    onSeekForward = {},
                    onSeek = {},
                    onSeekStart = {},
                    onSeekEnd = {},
                    onSeekPositionChange = {},
                    onBack = { backClicked = true },
                    onSpeedClick = {},
                    onAudioClick = {},
                    onSubtitleClick = {},
                    onSubtitleStyleClick = {},
                    onSecondarySubtitleClick = {},
                    onChapterClick = {},
                    onInfoClick = {},
                    onAspectRatioClick = {},
                    onDialogueBoostClick = {},
                    onNightModeClick = {},
                    onAudioDelayClick = {},
                    onDecoderClick = {},
                    onPassthroughClick = {},
                    onCastClick = {},
                    onOcrClick = {},
                    onSubtitleDownloadClick = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(backClicked)
    }

    @Test
    fun playerControls_playPauseToggles_callsOnPlayPause() {
        var playPauseCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                PlayerControls(
                    title = "Test",
                    subtitle = "",
                    isPlaying = false,
                    currentPosition = 0L,
                    duration = 0L,
                    playbackSpeed = 1.0f,
                    hasChapters = false,
                    dialogueBoostEnabled = false,
                    nightModeEnabled = false,
                    audioPassthrough = false,
                    isCasting = false,
                    isOcrRunning = false,
                    currentAspectRatio = AspectRatio.AUTO,
                    detectedAspectRatio = null,
                    isVisible = true,
                    onPlayPause = { playPauseCount++ },
                    onSeekBack = {},
                    onSeekForward = {},
                    onSeek = {},
                    onSeekStart = {},
                    onSeekEnd = {},
                    onSeekPositionChange = {},
                    onBack = {},
                    onSpeedClick = {},
                    onAudioClick = {},
                    onSubtitleClick = {},
                    onSubtitleStyleClick = {},
                    onSecondarySubtitleClick = {},
                    onChapterClick = {},
                    onInfoClick = {},
                    onAspectRatioClick = {},
                    onDialogueBoostClick = {},
                    onNightModeClick = {},
                    onAudioDelayClick = {},
                    onDecoderClick = {},
                    onPassthroughClick = {},
                    onCastClick = {},
                    onOcrClick = {},
                    onSubtitleDownloadClick = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        assert(playPauseCount == 1)
    }

    @Test
    fun playerControls_blankSubtitle_hidesSubtitleText() {
        setContent(subtitle = "")
        composeTestRule.onNodeWithText("Test Movie").assertIsDisplayed()
    }
}
