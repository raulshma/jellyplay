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
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Moon
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
 * The two reveal arms differ in who scrolls:
 *  - the SUB-SCREEN arm: `HighlightScrollEffect` scrolls the LazyColumn to the
 *    group slot (the group index), which brings the card — and its rows —
 *    into the composition window;
 *  - the ROOT-screen arm: NO screen-level scroll exists; the row's own
 *    bring-into-view is the only pull-in.
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
     * ROOT-screen arm: the browse pane's real reveal choreography — the real
     * section emission order (the pane's first seven sections, each its own
     * lazy item), the real [resolveRootRevealItemIndex] over the real
     * [SettingsScreenGroups.jellyplayFeatures] declaration and Android-shaped
     * capabilities, and the real [HighlightScrollEffect] the pane gained with
     * the reveal-scroll fix.
     *
     * A jellyplay feature row is the reveal target (the user's original
     * failing class): with the composite sections before it tall enough to
     * push the group below the fold, the search-reveal tap must scroll the
     * pane to the group's lazy item — the item can then compose, the card
     * expands, and the row's own focus choreography completes. Without the
     * scroll the row never composes and nothing is displayed (the red state
     * this test was written in).
     */
    @Test
    fun rootArm_highlightedRowInBelowFoldGroup_isDisplayedAndFocused() {
        val revealId = SettingsScreenGroups.jellyplayFeatures.itemIdSet.first()
        val androidCaps = SettingsCapabilities(
            supportsDynamicColor = false,
            supportsNotifications = true,
            supportsAppLocaleOverride = true,
            supportsAudioCache = true,
            supportsScreenOrientation = true,
            supportsTouchGestures = true,
            supportsAppLock = true,
            supportsBiometric = true,
            supportsSystemNotificationSettings = true,
            supportsLogSharing = true,
            supportsAudioDeviceSelection = false,
            supportsMpvRenderProfiles = false,
            supportsVolumeMemory = false,
            supportsIdleAmbientScreen = false,
            supportsDiscordPresence = false,
            supportsShellHooks = false,
            supportsPip = true,
        )
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                // The root screen sets this true after entry; without it the
                // entrance stagger never reveals the sections.
                CompositionLocalProvider(LocalAnimateSettingsEntrance provides true) {
                    val scrollState = rememberLazyListState()
                    HighlightScrollEffect(
                        scrollState = scrollState,
                        scrollIndex = resolveRootRevealItemIndex(revealId, isTv = false, capabilities = androidCaps),
                    )
                    LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize()) {
                        // The pane's first six sections (profile … system) —
                        // never gated, tall enough to hold the reveal group
                        // below the fold.
                        settingsSection("profile", isTv = false) { Box(Modifier.fillMaxWidth().height(300.dp)) }
                        settingsSection("power_user_mode", isTv = false) { Box(Modifier.fillMaxWidth().height(300.dp)) }
                        settingsSection("active_devices", isTv = false) { Box(Modifier.fillMaxWidth().height(300.dp)) }
                        settingsSection("account", isTv = false) { Box(Modifier.fillMaxWidth().height(300.dp)) }
                        settingsSection("activity", isTv = false) { Box(Modifier.fillMaxWidth().height(300.dp)) }
                        settingsSection("system", isTv = false) { Box(Modifier.fillMaxWidth().height(300.dp)) }
                        settingsSection("group_jellyplay", isTv = false) {
                            // The real card + row idioms: the group expands from
                            // the declaration, the row highlights from the id.
                            SettingsGroup(
                                icon = Tabler.Outline.Moon,
                                title = "Server plugin",
                                initiallyExpanded = revealId in SettingsScreenGroups.jellyplayFeatures.itemIdSet,
                            ) {
                                Column { revealedGroupBody(lastClickedSettingId = revealId) }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_500)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(targetTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(targetTag).assertIsFocused()
    }

    /**
     * SUB-SCREEN arm at full fidelity: the REAL [PreferenceScreenScaffold]
     * over the REAL playback declaration shape — the media-segments slot is
     * `advancedOnly`, and the deep-link target is
     * `PlaybackRows.SkipPromptDuration.id` (the user's "skip button
     * duration").
     *
     * This models the enableAdvanced race the search click performs: the
     * navigation lands FIRST (showAdvanced still false — the advanced-only
     * slot does not compose and the scroll index resolves to -1), and the
     * toggle flips true only afterwards (the search click's async
     * `setShowAdvancedSettings(true)` edit). The card must still expand,
     * scroll into view, and the target row must end up displayed + focused.
     */
    @Test
    fun playbackScreen_skipButtonDurationRow_revealsFocusedAfterAdvancedFlip() {
        var showAdvanced by mutableStateOf(false)
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
                        // Geometry proxy for the real player group (~600dp of
                        // card above the advanced tier).
                        SettingsScreenGroups.playbackPlayer.asScreenGroup(
                            content = { _ -> Box(Modifier.fillMaxWidth().height(600.dp)) },
                        ),
                        SettingsScreenGroups.playbackAdvancedVideo.asScreenGroup(),
                        SettingsScreenGroups.playbackEngine.asScreenGroup(),
                        SettingsScreenGroups.playbackMediaSegments.asScreenGroup(
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
                        SettingsScreenGroups.playbackSyncPlay.asScreenGroup(),
                        SettingsScreenGroups.playbackCasting.asScreenGroup(),
                        SettingsScreenGroups.playbackDvr.asScreenGroup(),
                    ),
                    advancedToggle = PreferenceAdvancedToggle(
                        showAdvanced = showAdvanced,
                        onToggle = { showAdvanced = !showAdvanced },
                    ),
                )
            }
        }
        // First composition settles with showAdvanced=false (the advanced-only
        // slot absent, the scroll resolving to -1)…
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()
        // …then the search click's async advanced-enable lands.
        composeTestRule.runOnUiThread { showAdvanced = true }
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Skip Button Duration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip Button Duration").assertIsFocused()
    }
}
