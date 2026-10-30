package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.model.MediaSegmentType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimedSkipPromptTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val promptKey = mutableStateOf<String?>("episode-1-intro")
    private val canShow = mutableStateOf(true)
    private val controlsVisible = mutableStateOf(false)
    private val interactions = mutableStateOf(0)

    private fun setContent(durationMs: Int = SKIP_PROMPT_DURATION_MS) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                val prompt = rememberTimedSkipPrompt(promptKey.value, canShow.value, interactions.value, durationMs)
                SegmentSkipOverlay(
                    isVisible = promptKey.value != null && canShow.value &&
                        prompt.isVisible(controlsVisible.value, interactions.value),
                    segmentType = MediaSegmentType.INTRO,
                    onSkip = {},
                    initialPromptProgress = prompt.progress.takeIf { prompt.isInitialPromptActive },
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(400)
    }

    private fun progress(): Float = composeTestRule.onNodeWithTag("skip-prompt-progress")
        .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun advanceBy(ms: Long) {
        // Android layout/recomposition must observe state changes before the
        // virtual clock advances the overlay's entrance/exit animations.
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(ms)
        composeTestRule.waitForIdle()
    }

    @Test
    fun firstAppearance_countsDownAndHidesWithControlsClosed() {
        setContent()
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
        val initial = progress()
        advanceBy(4_000)
        val halfway = progress()
        assertTrue(halfway in 0.4f..0.65f && halfway < initial)
        advanceBy(6_500)
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
        composeTestRule.onNodeWithTag("skip-prompt-progress").assertDoesNotExist()
    }

    @Test
    fun expiryHidesEvenWithControlsOpen_thenTouchRestoresWithoutRestartingTimer() {
        controlsVisible.value = true
        setContent()
        advanceBy(10_500)
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
        composeTestRule.runOnIdle { interactions.value++ }
        advanceBy(400)
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
        composeTestRule.onNodeWithTag("skip-prompt-progress").assertDoesNotExist()
        advanceBy(6_000)
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
        composeTestRule.runOnIdle { controlsVisible.value = false }
        advanceBy(1_000)
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
    }

    @Test
    fun nextSegmentGetsANewCountdown() {
        setContent()
        advanceBy(10_500)
        composeTestRule.runOnIdle { promptKey.value = "episode-1-recap" }
        advanceBy(400)
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
        assertTrue(progress() > 0.85f)
    }

    @Test
    fun sheetLockOrPip_preservesCountdownUntilVisible() {
        setContent()
        advanceBy(1_000)
        val beforeHide = progress()
        composeTestRule.runOnIdle { canShow.value = false }
        advanceBy(6_000)
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
        composeTestRule.runOnIdle { canShow.value = true }
        advanceBy(400)
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
        assertTrue(progress() in (beforeHide - 0.12f)..beforeHide)
        advanceBy(10_000)
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
    }

    @Test
    fun customDurationControlsCountdown() {
        setContent(durationMs = 3_000)
        advanceBy(1_000)
        composeTestRule.onNodeWithText("Skip Intro").assertIsDisplayed()
        advanceBy(2_500)
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
    }

    @Test
    fun leavingSegmentDismissesPrompt() {
        setContent()
        composeTestRule.runOnIdle { promptKey.value = null }
        advanceBy(1_000)
        composeTestRule.onNodeWithText("Skip Intro").assertDoesNotExist()
    }
}
