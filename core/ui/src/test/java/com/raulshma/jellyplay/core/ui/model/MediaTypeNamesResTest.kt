package com.raulshma.jellyplay.core.ui.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the `@StringRes` label table: EVERY [MediaType] entry must resolve to a
 * non-blank string. A new enum constant whose branch was forgotten (or wired
 * to a resource id that does not exist) makes this fail loudly instead of
 * crashing at runtime with `Resources$NotFoundException`.
 */
@RunWith(RobolectricTestRunner::class)
class MediaTypeNamesResTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every media type resolves a non-blank singular display name`() {
        MediaType.entries.forEach { type ->
            val text = context.getString(type.mediaTypeDisplayNameRes())
            assertTrue("blank singular label for $type", text.isNotBlank())
        }
    }

    @Test
    fun `every media type resolves a non-blank plural display name`() {
        MediaType.entries.forEach { type ->
            val text = context.getString(type.mediaTypeDisplayNamePluralRes())
            assertTrue("blank plural label for $type", text.isNotBlank())
        }
    }

    @Test
    fun `singular and plural map to different resource ids per type`() {
        MediaType.entries.forEach { type ->
            // "Live TV" is legitimately identical text in both forms, so compare
            // ids: a copy-pasted id for either form is the failure mode here.
            org.junit.Assert.assertNotEquals(
                "singular and plural share a resource id for $type",
                type.mediaTypeDisplayNameRes(),
                type.mediaTypeDisplayNamePluralRes(),
            )
        }
    }
}
