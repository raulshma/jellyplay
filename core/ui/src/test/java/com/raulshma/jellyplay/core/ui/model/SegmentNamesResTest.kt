package com.raulshma.jellyplay.core.ui.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.SegmentBehavior
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the segment label tables: EVERY [MediaSegmentType] entry must resolve
 * non-blank display / description / skip-label strings, and EVERY
 * [SegmentBehavior] entry a non-blank display / description pair. A new enum
 * branch wired to a missing resource id fails here instead of crashing the
 * player UI at runtime.
 */
@RunWith(RobolectricTestRunner::class)
class SegmentNamesResTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every segment type resolves a non-blank display name`() {
        MediaSegmentType.entries.forEach { type ->
            assertTrue("blank display name for $type", context.getString(type.displayNameRes()).isNotBlank())
        }
    }

    @Test
    fun `every segment type resolves a non-blank description`() {
        MediaSegmentType.entries.forEach { type ->
            assertTrue("blank description for $type", context.getString(type.descriptionRes()).isNotBlank())
        }
    }

    @Test
    fun `every segment type resolves a non-blank skip label`() {
        MediaSegmentType.entries.forEach { type ->
            assertTrue("blank skip label for $type", context.getString(type.skipLabelRes()).isNotBlank())
        }
    }

    @Test
    fun `every segment behavior resolves non-blank display name and description`() {
        SegmentBehavior.entries.forEach { behavior ->
            assertTrue(
                "blank display name for $behavior",
                context.getString(behavior.displayNameRes()).isNotBlank(),
            )
            assertTrue(
                "blank description for $behavior",
                context.getString(behavior.descriptionRes()).isNotBlank(),
            )
        }
    }
}
