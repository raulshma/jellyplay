package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.formatDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        chapters: List<ChapterInfo> = emptyList(),
        title: String = "Test Movie",
        subtitle: String = "Episode 1",
        supportsSubtitleStyle: Boolean = false,
        supportsDialogueBoost: Boolean = false,
        supportsNightMode: Boolean = false,
        supportsAudioDelay: Boolean = false,
        supportsAudioPassthrough: Boolean = false,
        currentAspectRatio: AspectRatio = AspectRatio.AUTO,
        detectedAspectRatio: AspectRatio? = null,
        dialogueBoostEnabled: Boolean = false,
        dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
        nightModeEnabled: Boolean = false,
        nightModeStrength: EffectStrength = EffectStrength.MODERATE,
        audioPassthrough: Boolean = false,
    ) {
        val currentPositionFlow: StateFlow<Long> = MutableStateFlow(currentPosition)
        val bufferedPositionFlow: StateFlow<Long> = MutableStateFlow(currentPosition)
        val videoStatsFlow: StateFlow<EngineVideoStats> = MutableStateFlow(EngineVideoStats())
        composeTestRule.setContent {
            MaterialTheme {
                PlayerControls(
                    title = title,
                    subtitle = subtitle,
                    isPlaying = isPlaying,
                    currentPositionFlow = currentPositionFlow,
                    duration = duration,
                    bufferedPositionFlow = bufferedPositionFlow,
                    videoStatsFlow = videoStatsFlow,
                    playbackSpeed = playbackSpeed,
                    chapters = chapters,
                    dialogueBoostEnabled = dialogueBoostEnabled,
                    dialogueBoostStrength = dialogueBoostStrength,
                    nightModeEnabled = nightModeEnabled,
                    nightModeStrength = nightModeStrength,
                    audioPassthrough = audioPassthrough,
                    currentAspectRatio = currentAspectRatio,
                    detectedAspectRatio = detectedAspectRatio,
                    isVisible = isVisible,
                    supportsSubtitleStyle = supportsSubtitleStyle,
                    supportsDialogueBoost = supportsDialogueBoost,
                    supportsNightMode = supportsNightMode,
                    supportsAudioDelay = supportsAudioDelay,
                    supportsAudioPassthrough = supportsAudioPassthrough,
                    onPlayPause = {},
                    onSeekStart = {},
                    onSeekEnd = {},
                    onSeekPositionChange = {},
                    onBack = {},
                    onSpeedClick = {},
                    onAudioClick = {},
                    onSubtitleClick = {},
                    onSubtitleHubClick = {},
                    onChapterClick = {},
                    onInfoClick = {},
                    onAspectRatioClick = {},
                    onDialogueBoostClick = {},
                    onDialogueBoostStrengthChange = {},
                    onNightModeClick = {},
                    onNightModeStrengthChange = {},
                    onAVSyncClick = {},
                    onDecoderClick = {},
                    onPassthroughClick = {},
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
        composeTestRule.onNodeWithContentDescription("Previous episode").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next episode").assertIsDisplayed()
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
        composeTestRule.onNodeWithContentDescription("Audio").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsSubsButton() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Subtitles").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsInfoButton() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Info").assertIsDisplayed()
    }

    @Test
    fun playerControls_visible_showsCastButton() {
        setContent()
        composeTestRule.onNodeWithContentDescription("Cast").assertIsDisplayed()
    }

    @Test
    fun playerControls_hidden_controlsNotDisplayed() {
        setContent(isVisible = false)
        composeTestRule.onNodeWithText("Test Movie").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test
    fun playerControls_withChapters_showsChaptersButton() {
        setContent(chapters = listOf(ChapterInfo("Chapter 1", 0L)))
        composeTestRule.onNodeWithContentDescription("Chapters").assertIsDisplayed()
    }

    @Test
    fun playerControls_withoutChapters_hidesChaptersButton() {
        setContent(chapters = emptyList())
        composeTestRule.onNodeWithContentDescription("Chapters").assertDoesNotExist()
    }

    @Test
    fun playerControls_visible_showsMoreOptionsButton() {
        setContent()
        composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun playerControls_supportsSubtitleStyle_showsStyleInOverflow() {
        setContent(supportsSubtitleStyle = true)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) { composeTestRule.onAllNodesWithText("Subtitle Style").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Subtitle Style").assertIsDisplayed()
    }

    @Test
    fun playerControls_noSubtitleStyle_hidesStyleFromOverflow() {
        setContent(supportsSubtitleStyle = false)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Subtitle Style").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsDialogueBoost_showsBoostInOverflow() {
        setContent(supportsDialogueBoost = true)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) { composeTestRule.onAllNodesWithText("Dialogue Boost").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Dialogue Boost").assertIsDisplayed()
    }

    @Test
    fun playerControls_noDialogueBoost_hidesBoostFromOverflow() {
        setContent(supportsDialogueBoost = false)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Dialogue Boost").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsNightMode_showsNightInOverflow() {
        setContent(supportsNightMode = true)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) { composeTestRule.onAllNodesWithText("Night Mode").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Night Mode").assertIsDisplayed()
    }

    @Test
    fun playerControls_noNightMode_hidesNightFromOverflow() {
        setContent(supportsNightMode = false)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Night Mode").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsAudioDelay_showsDelayInOverflow() {
        setContent(supportsAudioDelay = true)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) { composeTestRule.onAllNodesWithText("Audio Delay").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Audio Delay").assertIsDisplayed()
    }

    @Test
    fun playerControls_noAudioDelay_hidesDelayFromOverflow() {
        setContent(supportsAudioDelay = false)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Audio Delay").assertDoesNotExist()
    }

    @Test
    fun playerControls_supportsPassthrough_showsPassthroughInOverflow() {
        setContent(supportsAudioPassthrough = true)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000L) { composeTestRule.onAllNodesWithText("Passthrough").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Passthrough").assertIsDisplayed()
    }

    @Test
    fun playerControls_noPassthrough_hidesPassthroughFromOverflow() {
        setContent(supportsAudioPassthrough = false)
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Passthrough").assertDoesNotExist()
    }

    @Test
    fun playerControls_backButton_callsOnBack() {
        var backClicked = false
        val currentPositionFlow: StateFlow<Long> = MutableStateFlow(0L)
        val bufferedPositionFlow: StateFlow<Long> = MutableStateFlow(0L)
        val videoStatsFlow: StateFlow<EngineVideoStats> = MutableStateFlow(EngineVideoStats())
        composeTestRule.setContent {
            MaterialTheme {
                PlayerControls(
                    title = "Test",
                    subtitle = "",
                    isPlaying = false,
                    currentPositionFlow = currentPositionFlow,
                    duration = 0L,
                    bufferedPositionFlow = bufferedPositionFlow,
                    videoStatsFlow = videoStatsFlow,
                    playbackSpeed = 1.0f,
                    chapters = emptyList(),
                    dialogueBoostEnabled = false,
                    dialogueBoostStrength = EffectStrength.MODERATE,
                    nightModeEnabled = false,
                    nightModeStrength = EffectStrength.MODERATE,
                    audioPassthrough = false,
                    currentAspectRatio = AspectRatio.AUTO,
                    detectedAspectRatio = null,
                    isVisible = true,
                    onPlayPause = {},
                    onSeekStart = {},
                    onSeekEnd = {},
                    onSeekPositionChange = {},
                    onBack = { backClicked = true },
                    onSpeedClick = {},
                    onAudioClick = {},
                    onSubtitleClick = {},
                    onSubtitleHubClick = {},
                    onChapterClick = {},
                    onInfoClick = {},
                    onAspectRatioClick = {},
                    onDialogueBoostClick = {},
                    onDialogueBoostStrengthChange = {},
                    onNightModeClick = {},
                    onNightModeStrengthChange = {},
                    onAVSyncClick = {},
                    onDecoderClick = {},
                    onPassthroughClick = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assert(backClicked)
    }

    @Test
    fun playerControls_playPauseToggles_callsOnPlayPause() {
        var playPauseCount = 0
        val currentPositionFlow: StateFlow<Long> = MutableStateFlow(0L)
        val bufferedPositionFlow: StateFlow<Long> = MutableStateFlow(0L)
        val videoStatsFlow: StateFlow<EngineVideoStats> = MutableStateFlow(EngineVideoStats())
        composeTestRule.setContent {
            MaterialTheme {
                PlayerControls(
                    title = "Test",
                    subtitle = "",
                    isPlaying = false,
                    currentPositionFlow = currentPositionFlow,
                    duration = 0L,
                    bufferedPositionFlow = bufferedPositionFlow,
                    videoStatsFlow = videoStatsFlow,
                    playbackSpeed = 1.0f,
                    chapters = emptyList(),
                    dialogueBoostEnabled = false,
                    dialogueBoostStrength = EffectStrength.MODERATE,
                    nightModeEnabled = false,
                    nightModeStrength = EffectStrength.MODERATE,
                    audioPassthrough = false,
                    currentAspectRatio = AspectRatio.AUTO,
                    detectedAspectRatio = null,
                    isVisible = true,
                    onPlayPause = { playPauseCount++ },
                    onSeekStart = {},
                    onSeekEnd = {},
                    onSeekPositionChange = {},
                    onBack = {},
                    onSpeedClick = {},
                    onAudioClick = {},
                    onSubtitleClick = {},
                    onSubtitleHubClick = {},
                    onChapterClick = {},
                    onInfoClick = {},
                    onAspectRatioClick = {},
                    onDialogueBoostClick = {},
                    onDialogueBoostStrengthChange = {},
                    onNightModeClick = {},
                    onNightModeStrengthChange = {},
                    onAVSyncClick = {},
                    onDecoderClick = {},
                    onPassthroughClick = {},
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
