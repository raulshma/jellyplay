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
 * Robolectric Compose-UI regression for issue #224 — opening the downloads
 * storage-location picker hard-crashed the app:
 *
 * `java.lang.IllegalArgumentException: Type of the key StorageMount(…) is not
 * supported. On Android you can only use types which can be stored inside the
 * Bundle.`
 *
 * The picker keyed each lazy row on the option value itself, which works for
 * primitives / Strings / enums but not for a custom data class. The fix keys
 * non-Bundle-saveable option types on their original-list index (see
 * [IndexedPickerOption.lazyRowKey]); this test drives both the first
 * composition and a scroll round trip, so the value-keyed regression would
 * throw in either path.
 */
@RunWith(AndroidJUnit4::class)
class SettingsListPickerSheetCustomOptionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Deliberately plain: not Parcelable/Serializable, like `StorageMount`. */
    private data class StorageMount(val prefValue: String, val availableBytes: Long)

    @Test
    fun customNonBundleSaveableOption_opensAndSurvivesScrollRoundTrip() {
        val mounts = (0 until 30).map { StorageMount("EXTERNAL_$it", it.toLong()) }

        composeTestRule.setContent {
            MaterialTheme {
                SettingsListPickerSheet(
                    title = "Download Storage Location",
                    items = mounts,
                    label = { it.prefValue },
                    isSelected = { it == mounts.first() },
                    onDismiss = {},
                    onSelect = {},
                )
            }
        }

        composeTestRule.onNodeWithText("EXTERNAL_0").assertIsDisplayed()

        // Dispose the first row, then bring a keyed row back into composition:
        // SaveableStateHolder validates the lazy-row key on both paths.
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("EXTERNAL_29"))
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("EXTERNAL_0"))
        composeTestRule.onNodeWithText("EXTERNAL_0").assertIsDisplayed()
    }
}
