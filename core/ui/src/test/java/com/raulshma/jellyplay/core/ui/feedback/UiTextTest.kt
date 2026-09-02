package com.raulshma.jellyplay.core.ui.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins [UiText.resolve]: the non-Composable resolution path used by the TV
 * Toast host. Both variants must resolve — [UiText.Raw] verbatim, and
 * [UiText.Resource] with zero args, plain printf args, multiple args, and
 * nested [UiText] args (resolved recursively so composed messages mix
 * localizable fragments).
 */
@RunWith(RobolectricTestRunner::class)
class UiTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `raw resolves to its own value`() {
        assertEquals(
            "Server said no",
            UiText.Raw("Server said no").resolve(context),
        )
    }

    @Test
    fun `resource without args resolves the bare string`() {
        assertEquals(
            "Movie",
            UiText.Resource(R.string.core_media_movie).resolve(context),
        )
    }

    @Test
    fun `resource with a plain arg formats it`() {
        assertEquals(
            "Season 4",
            UiText.Resource(R.string.core_ui_seerr_season, listOf(4)).resolve(context),
        )
    }

    @Test
    fun `resource with multiple plain args formats all of them`() {
        assertEquals(
            "3 of 12 episodes selected",
            UiText.Resource(R.string.detail_episodes_selected, listOf(3, 12)).resolve(context),
        )
    }

    @Test
    fun `nested UiText args are resolved recursively`() {
        val text = UiText.Resource(
            R.string.transcode_reason_unknown,
            listOf(UiText.Resource(R.string.core_media_movie)),
        )

        assertEquals("Server reported: Movie", text.resolve(context))
    }

    @Test
    fun `uiTextOf builds a Resource and String asUiText wraps a Raw`() {
        assertEquals(
            UiText.Resource(R.string.core_media_movie),
            uiTextOf(R.string.core_media_movie),
        )
        assertEquals(
            UiText.Raw("hello"),
            "hello".asUiText(),
        )
    }
}
