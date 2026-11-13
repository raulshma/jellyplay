package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric Compose-UI regression for issue #213 — the crash when opening /
 * using the Audio Language or Subtitle Language picker from the settings
 * menu: `java.lang.IllegalArgumentException: Type of the key java.lang.Object@…
 * is not supported. On Android you can only use types which can be stored
 * inside the Bundle.`
 *
 * The real picker lists ([languages]) lead with a `null` option ("Default"),
 * and [SettingsListPickerSheet] keys that row on an identity sentinel. A lazy
 * item key flows into `SaveableStateHolder.SaveableStateProvider`, which
 * requires the key to be Bundle-saveable — a plain `Any()` sentinel fails that
 * check. The failure is not on the first composition (the lazy saveable-state
 * holder composes never-before-seen keys directly) but on RE-composition of a
 * previously-composed key: scroll the row out of the viewport and back (or
 * filter it out and in). That round trip is exactly what this test drives.
 */
@RunWith(AndroidJUnit4::class)
class SettingsListPickerSheetNullOptionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nullDefaultOption_rowSurvivesScrollRoundTrip() {
        // The exact item lists the Audio/Subtitle Language rows build:
        // `langs.map { it.first }` over the real language catalog, which
        // leads with the null "Default" option.
        val items: List<String?> = languages.map { it.first }

        composeTestRule.setContent {
            MaterialTheme {
                SettingsListPickerSheet(
                    title = "Audio Language",
                    items = items,
                    label = { code -> languageNameByCode[code] ?: code ?: "Default" },
                    isSelected = { it == null },
                    onDismiss = {},
                    onSelect = {},
                )
            }
        }

        // First composition of the null-key row — the direct-compose path.
        composeTestRule.onNodeWithText("Default").assertIsDisplayed()

        // Scroll the "Default" row out of the viewport (dispose records the
        // key into previouslyComposedKeys), then scroll back — re-composition
        // of a previously-composed key routes through SaveableStateProvider,
        // which throws for non-Bundle keys.
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Zulu"))
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Default"))
        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
    }
}
