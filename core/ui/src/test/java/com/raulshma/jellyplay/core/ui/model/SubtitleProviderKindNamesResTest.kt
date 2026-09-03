package com.raulshma.jellyplay.core.ui.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the subtitle-provider label table: EVERY [SubtitleProviderKind] entry
 * must resolve to a non-blank display name, so adding a provider without its
 * string resource fails here rather than crashing the subtitle search sheet.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleProviderKindNamesResTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every subtitle provider resolves a non-blank display name`() {
        SubtitleProviderKind.entries.forEach { provider ->
            assertTrue(
                "blank display name for $provider",
                context.getString(provider.displayNameRes()).isNotBlank(),
            )
        }
    }
}
