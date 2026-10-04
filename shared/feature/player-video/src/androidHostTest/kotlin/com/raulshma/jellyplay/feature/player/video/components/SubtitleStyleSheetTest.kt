package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleStyleSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun subtitleStyleSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Settings").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysFontSize() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(fontSize = 24),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Font Size: 24sp").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysFontColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Font Color").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysBackgroundColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Background Color").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysBackgroundOpacity() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(backgroundOpacity = 0.6f),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Background Opacity: 60%").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysEdgeTypeSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(edgeType = SubtitleEdgeType.NONE),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Edge Type").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysAllEdgeTypes() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("None").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shadow").assertIsDisplayed()
        composeTestRule.onNodeWithText("Raised").assertIsDisplayed()
        composeTestRule.onNodeWithText("Depressed").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withEdgeType_showsEdgeColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    // The Edge Color section renders only while the style
                    // override is enabled AND an edge type is active.
                    currentStyle = SubtitleStyle(
                        edgeType = SubtitleEdgeType.OUTLINE,
                        applyCustomStyle = true,
                    ),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Edge Color").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_noneEdgeType_hidesEdgeColorSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(edgeType = SubtitleEdgeType.NONE),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Edge Color").assertDoesNotExist()
    }

    @Test
    fun subtitleStyleSheet_displaysSubtitleOffset() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(offsetMs = 0L),
                    onStyleChange = {},
                    onDismiss = {},
                    // The offset slider is capability-gated (ExoPlayer must
                    // reload the media item to re-parse cues, so engines that
                    // can't delay subtitles hide the control).
                    capabilities = EngineCapabilities(supportsSubtitleDelay = true),
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Offset: 0.0s").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_positiveOffset_displaysPlusSign() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(offsetMs = 2000L),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(supportsSubtitleDelay = true),
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Offset: +2.0s").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_negativeOffset_displaysMinusSign() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(offsetMs = -2000L),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(supportsSubtitleDelay = true),
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Offset: -2.0s").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysVerticalPosition() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(verticalPosition = 0.05f),
                    onStyleChange = {},
                    onDismiss = {},
                    // The vertical-position slider is capability-gated.
                    capabilities = EngineCapabilities(supportsSubtitleVerticalPosition = true),
                )
            }
        }
        composeTestRule.onNodeWithText("Vertical Position: 5%").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_displaysResetButton() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reset").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_reset_callsOnStyleChangeWithDefaults() {
        var receivedStyle: SubtitleStyle? = null
        val modifiedStyle = SubtitleStyle(
            fontSize = 36,
            offsetMs = 5000L,
            edgeType = SubtitleEdgeType.OUTLINE,
            // The reset chip is only enabled while the style override is on.
            applyCustomStyle = true,
        )
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = modifiedStyle,
                    onStyleChange = { receivedStyle = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Reset").performClick()
        // Reset re-enables applyCustomStyle (the button is only enabled when it is on),
        // so the emitted style is the full-default with applyCustomStyle = true.
        assertEquals(SubtitleStyle(applyCustomStyle = true), receivedStyle)
    }

    @Test
    fun subtitleStyleSheet_customFontSize_showsCorrectValue() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(fontSize = 36),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Font Size: 36sp").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withAssOverrideCapability_showsAssOverrideControl() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsAssOverride = true,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("ASS Styling").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withoutAssOverrideCapability_hidesAssOverrideControl() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsAssOverride = false,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("ASS Styling").assertDoesNotExist()
    }

    @Test
    fun subtitleStyleSheet_withBorderStylesCapability_showsBorderSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    // The border-style block needs BOTH border support and the
                    // ability to apply style overrides to ASS tracks (mpv-only):
                    // engines that render ASS as-authored hide the control.
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsBorderStyles = true,
                        supportsAssStyleOverride = true,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Border Style").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_withoutBorderStylesCapability_hidesBorderSection() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(applyCustomStyle = true),
                    onStyleChange = {},
                    onDismiss = {},
                    capabilities = EngineCapabilities(
                        supportsSubtitleStyle = true,
                        supportsBorderStyles = false,
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Border Style").assertDoesNotExist()
    }

    // ─── named style presets: apply / save / delete ─────────────────────

    private fun setContentWith(
        currentStyle: SubtitleStyle = SubtitleStyle(),
        userPresets: List<SubtitleStylePreset> = emptyList(),
        onSavePreset: (String) -> Unit = {},
        onDeletePreset: (String) -> Unit = {},
        onStyleChange: (SubtitleStyle) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = currentStyle,
                    onStyleChange = onStyleChange,
                    onDismiss = {},
                    userPresets = userPresets,
                    onSavePreset = onSavePreset,
                    onDeletePreset = onDeletePreset,
                )
            }
        }
    }

    @Test
    fun subtitleStyleSheet_displaysPresetsSectionWithBuiltIns() {
        setContentWith()
        composeTestRule.onNodeWithText("Presets").assertIsDisplayed()
        composeTestRule.onNodeWithText("Subtle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Big bold").assertIsDisplayed()
        composeTestRule.onNodeWithText("Classic yellow").assertIsDisplayed()
        composeTestRule.onNodeWithText("Netflix-ish").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save current").assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_builtinPresetChip_appliesPresetStyle() {
        var received: SubtitleStyle? = null
        // The current per-item sync delay must survive the apply.
        setContentWith(
            currentStyle = SubtitleStyle(offsetMs = 2000L),
            onStyleChange = { received = it },
        )
        composeTestRule.onNodeWithText("Classic yellow").performClick()
        val applied = received!!
        assertEquals(SubtitleColor.YELLOW, applied.fontColor)
        assertEquals(SubtitleEdgeType.OUTLINE, applied.edgeType)
        assertTrue("applying a preset must force the override on", applied.applyCustomStyle)
        assertEquals("the per-item delay never travels with a look", 2000L, applied.offsetMs)
    }

    @Test
    fun subtitleStyleSheet_userPresetChip_appliesPresetStyle() {
        var received: SubtitleStyle? = null
        setContentWith(
            userPresets = listOf(
                SubtitleStylePreset("Mine", SubtitleStyle(fontSize = 40, bold = true)),
            ),
            onStyleChange = { received = it },
        )
        composeTestRule.onNodeWithText("Mine").performClick()
        val applied = received!!
        assertEquals(40, applied.fontSize)
        assertTrue(applied.bold)
        assertTrue(applied.applyCustomStyle)
    }

    @Test
    fun subtitleStyleSheet_userPresetDelete_callsOnDelete() {
        var deleted: String? = null
        setContentWith(
            userPresets = listOf(
                SubtitleStylePreset("Mine", SubtitleStyle(fontSize = 40)),
            ),
            onDeletePreset = { deleted = it },
        )
        composeTestRule
            .onNodeWithContentDescription("Delete preset Mine")
            .performClick()
        assertEquals("Mine", deleted)
    }

    @Test
    fun subtitleStyleSheet_saveCurrent_opensDialog_andSavesNamedPreset() {
        var saved: String? = null
        setContentWith(onSavePreset = { saved = it })

        composeTestRule.onNodeWithText("Save current").performClick()
        composeTestRule.onNodeWithText("Save preset").assertIsDisplayed()

        // Confirm follows the module's dialog idiom ("Apply"/"Cancel") and is
        // disabled while the name is blank. Exact "Apply" matches only the
        // dialog's confirm button.
        val confirm = composeTestRule.onNodeWithText("Apply")
        confirm.assertIsNotEnabled()

        composeTestRule.onNodeWithTag("subtitle-preset-name").performTextInput("Mine")
        confirm.assertIsEnabled().performClick()

        assertEquals("Mine", saved)
    }

    // ─── custom mpv config ownership notice (issue #165, UI half) ───────────
    // The card spells out every custom-config/sub-style interaction case;
    // these pin each case's visibility so an edit can't silently no-op.

    @Test
    fun subtitleStyleSheet_mpvOwnedKeys_showConfigNotice() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                    subtitleOwnership = MpvSubtitleOwnership(
                        ownedStyleKeys = setOf("sub-color", "sub-pos"),
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("Your mpv config overrides some subtitle settings")
            .assertIsDisplayed()
        // Owned keys render as monospace pills — one node per key, exactly the
        // keys the engine yields.
        composeTestRule.onNodeWithText("sub-color").assertIsDisplayed()
        composeTestRule.onNodeWithText("sub-pos").assertIsDisplayed()
        // SRT/ASS case rows + app-owned functional keys are part of the same card.
        composeTestRule
            .onNodeWithText("Plain text (SRT) subtitles always use the colors", substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("ASS/SSA subtitles keep their embedded styling", substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Subtitle on/off and subtitle delay always follow the app", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_noMpvOwnership_hidesConfigNotice() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Your mpv config overrides some subtitle settings")
            .assertDoesNotExist()
    }

    @Test
    fun subtitleStyleSheet_ownedAssOverride_showsChipsDeadNotice() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                    subtitleOwnership = MpvSubtitleOwnership(
                        ownedStyleKeys = setOf("sub-ass-override"),
                    ),
                )
            }
        }
        composeTestRule
            .onNodeWithText("the Respect/Force choice here has no effect", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun subtitleStyleSheet_droppedConfKeys_showQuotingHint() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleStyleSheet(
                    currentStyle = SubtitleStyle(),
                    onStyleChange = {},
                    onDismiss = {},
                    subtitleOwnership = MpvSubtitleOwnership(
                        confKeysDroppedByParser = setOf("sub-color"),
                    ),
                )
            }
        }
        composeTestRule
            .onNodeWithText("mpv cannot read sub-color from its mpv.conf file", substring = true)
            .assertIsDisplayed()
        // The hint must spell out both surfaces: quoting fixes the conf FILE,
        // while the in-app config box takes the value plain.
        composeTestRule
            .onNodeWithText("In that file, quote the value", substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("the opposite applies", substring = true)
            .assertIsDisplayed()
    }
}
