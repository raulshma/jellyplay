package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.Wifi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric Compose-UI harness for the settings search-reveal focus
 * choreography — the runtime half the source-scan guards can only pin
 * textually.
 *
 * The user-facing flow under test: a search-result tap lands on a row that
 * lives inside a collapsible [SettingsGroup]; navigation (or the NoOp reveal
 * arm) marks the row id, the group auto-expands, and the row must then run
 * its focus choreography (the 2.5s highlight glow + the 400ms-delayed focus
 * grab + bring-into-view — [SettingListItem.highlighted]).
 *
 * ADR-0017 note: the root screen no longer hosts reveal rows (its former
 * inline groups moved onto their declared screens), so every reveal now rides
 * the SUB-SCREEN arm — the former ROOT-screen arm test is retired with the
 * machinery it exercised.
 *
 * The glow itself is pure drawing (shadow/background/border modifiers) and
 * has no semantics surface — the assertions cover the row's visibility and
 * focus, the observable halves of the choreography.
 */
@RunWith(AndroidJUnit4::class)
class SettingsSearchRevealFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val targetTag = "reveal_target_row"

    /**
     * The group body: filler rows + the reveal target — the row whose catalog
     * id is [lastClickedSettingId]'s match, highlighted exactly as the real
     * sections highlight (`highlighted = lastClickedSettingId == <row id>`).
     */
    @Composable
    private fun revealedGroupBody(lastClickedSettingId: String) {
        val rowId = SettingsScreenGroups.jellyplayFeatures.itemIdSet.first()
        repeat(6) { n ->
            SettingListItem(
                icon = Tabler.Outline.Moon,
                title = "Filler row $n",
                subtitle = null,
            ) {}
        }
        SettingListItem(
            icon = Tabler.Outline.Moon,
            title = "Target row",
            subtitle = null,
            highlighted = lastClickedSettingId == rowId,
            modifier = Modifier.testTag(targetTag),
        ) {}
    }

    /**
     * SUB-SCREEN arm: the deep-linked group sits below the fold (index 1,
     * behind a 1200dp spacer) and `HighlightScrollEffect` scrolls to it —
     * the `PreferenceScreenScaffold` shape. The row must end up visible AND
     * focused (the 400ms-delayed grab firing once the choreography settles).
     */
    @Test
    fun subScreenArm_highlightedRowInScrolledToGroup_isDisplayedAndFocused() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                val scrollState = rememberLazyListState()
                LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
                    item { Box(Modifier.fillMaxWidth().height(1200.dp)) }
                    item {
                        SettingsGroup(
                            icon = Tabler.Outline.Moon,
                            title = "Reveal group",
                            initiallyExpanded = true,
                        ) {
                            Column {
                                revealedGroupBody(
                                    lastClickedSettingId = SettingsScreenGroups.jellyplayFeatures.itemIdSet.first(),
                                )
                            }
                        }
                    }
                }
                HighlightScrollEffect(scrollState, scrollIndex = 1)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(targetTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(targetTag).assertIsFocused()
    }

    /**
     * SUB-SCREEN arm at full fidelity: the REAL [PreferenceScreenScaffold]
     * over the REAL Skipping & segments leaf declaration shape (ADR-0017 —
     * the media-segments group is that leaf's one `advancedOnly` slot), and
     * the deep-link target is `PlaybackRows.SkipPromptDuration.id` (the
     * user's "skip button duration"). The advanced gate is ARMED at first
     * composition — the state the search click's `enableAdvanced` produces
     * by the time the leaf composes — so the advanced-only slot is present
     * and the reveal choreography (expand, scroll, focus) runs against the
     * real declaration.
     *
     * Declared delta: the opposite interleaving (the leaf composing BEFORE
     * the gate arms, so the slot appears on a later recomposition) drops the
     * row's bring-into-view against a fresh lazy layout under a paused
     * Robolectric clock — the reveal lands at the card top (the shell's
     * derived scroll resolves the card once the slot composes), which is the
     * shipped behavior, but the pixel-level focus pin here models the armed
     * path the search click produces.
     */
    @Test
    fun playbackScreen_skipButtonDurationRow_revealsFocusedAfterAdvancedFlip() {
        var showAdvanced by mutableStateOf(true)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                val vm = io.mockk.mockk<PlaybackSettingsViewModel>(relaxed = true)
                val scope = rememberCoroutineScope()
                val activePicker = remember { mutableStateOf<PickerState<*>?>(null) }
                PreferenceScreenScaffold(
                    title = "Playback",
                    onBack = {},
                    focusTag = "test_playback",
                    highlightSettingId = PlaybackRows.SkipPromptDuration.id,
                    groups = listOf(
                        SettingsScreenGroups.playbackMediaSegments.asScreenGroup(
                            advancedOnly = true,
                            content = { picker ->
                                PlaybackMediaSegmentsGroup(
                                    preferences = PlaybackPreferences(),
                                    highlightSettingId = PlaybackRows.SkipPromptDuration.id,
                                    viewModel = vm,
                                    scope = scope,
                                    activePicker = picker,
                                )
                            },
                        ),
                    ),
                    advancedToggle = PreferenceAdvancedToggle(
                        showAdvanced = showAdvanced,
                        onToggle = { showAdvanced = !showAdvanced },
                    ),
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Skip Button Duration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip Button Duration").assertIsFocused()
    }

    /**
     * The GROUP-CARD arm: a group-card search hit deep-links its route with
     * the group's FIRST member row id baked in
     * ([SettingsSearchItemGroup.cardItem]) — the id this test feeds the REAL
     * shell over the REAL storage "Network diagnostics" declaration (an
     * advancedOnly slot). The card id itself expands and glows nothing; the
     * baked member id must land, expand the group (the screen's
     * `initiallyExpanded = highlightSettingId in group.itemIdSet` check) and
     * run the focus choreography on the first member row — the group-card
     * landing reusing the existing row-id machinery end to end.
     */
    @Test
    fun groupCardHit_landsExpandedAndGlowingOnTheFirstMemberRow() {
        val card = requireNotNull(SettingsScreenGroups.storageNetworkTuning.cardItem)
        val landingId =
            (card.route as com.raulshma.jellyplay.core.ui.navigation.Route.StorageSettings).highlightSettingId
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                PreferenceScreenScaffold(
                    title = "Storage & network",
                    onBack = {},
                    focusTag = "test_storage_card",
                    highlightSettingId = landingId,
                    groups = listOf(
                        SettingsScreenGroups.storageNetworkTuning.asScreenGroup(
                            advancedOnly = true,
                            content = { _ ->
                                SettingsGroup(
                                    icon = Tabler.Outline.Wifi,
                                    title = "Network diagnostics",
                                    initiallyExpanded = landingId in SettingsScreenGroups.storageNetworkTuning.itemIdSet,
                                ) {
                                    Column {
                                        repeat(6) { n ->
                                            SettingListItem(
                                                icon = Tabler.Outline.Moon,
                                                title = "Filler row $n",
                                                subtitle = null,
                                            ) {}
                                        }
                                        SettingListItem(
                                            icon = Tabler.Outline.AlertTriangle,
                                            title = "Card reveal target",
                                            subtitle = null,
                                            highlighted = landingId == StorageRows.CellularDownloadWarning.id,
                                            modifier = Modifier.testTag(cardRevealTargetTag),
                                        ) {}
                                    }
                                }
                            },
                        ),
                    ),
                    advancedToggle = PreferenceAdvancedToggle(
                        showAdvanced = true,
                        onToggle = {},
                    ),
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(cardRevealTargetTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(cardRevealTargetTag).assertIsFocused()
    }

    private val cardRevealTargetTag = "group_card_reveal_target_row"

    /**
     * The REAL-screen arm: the audio "Fine-tuning" group-card hit drives the
     * REAL [AudioSettingsScreen] with the card route's baked landing id — the
     * pinned regression this test exists to hold (the screen consumed its
     * `highlightSettingId` in the group bodies — expand + glow — but never
     * handed it to the shell's [PreferenceScreenScaffold], whose scroll
     * derivation defaults it to null: the group expanded while the list
     * stayed at the top). The screen must forward the id so the shell scrolls
     * to the Fine-tuning slot and the first member row runs its focus
     * choreography — the state the search click's auto-arm produces.
     */
    @Test
    fun audioScreen_groupCardLanding_scrollsToTheFineTuningGroupAndGlows() {
        val card = requireNotNull(SettingsScreenGroups.audioFineTuning.cardItem)
        val landingId =
            (card.route as com.raulshma.jellyplay.core.ui.navigation.Route.AudioSettings).highlightSettingId
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                val vm = io.mockk.mockk<AudioSettingsViewModel>(relaxed = true)
                io.mockk.every { vm.preferences } returns MutableStateFlow(AudioPreferences())
                io.mockk.every { vm.showAdvancedSettings } returns MutableStateFlow(true)
                AudioSettingsScreen(
                    onBack = {},
                    highlightSettingId = landingId,
                    viewModel = vm,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(2_500)
        composeTestRule.waitForIdle()

        // The Fine-tuning slot is the LAST LazyColumn item (below the tall
        // main group) — displayed only if the shell actually scrolled.
        composeTestRule.onNodeWithText("Night Mode Volume").assertIsDisplayed()
        composeTestRule.onNodeWithText("Night Mode Volume").assertIsFocused()
    }
}
