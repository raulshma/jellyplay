package com.raulshma.jellyplay.core.notification.receiver

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.repository.SeenMediaRecord
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Pins [NotificationActionReceiver]'s intent contract, invoked the way the
 * system delivers it: one `onReceive` per crafted action intent.
 *
 * Invariants:
 * - `ACTION_MARK_SEEN` records exactly one seen row with the extras' identity
 *   triple; a missing extra aborts before touching the repository.
 * - `ACTION_MARK_ALL_SEEN` zips the three parallel arrays into one bulk
 *   seen write and rejects mismatched/empty arrays without writing anything.
 * - `ACTION_OPEN_DETAIL` starts the `jellyplay://media/<id>` deep link scoped
 *   to this package — and never writes to the repository.
 * - Malformed or unknown intents are silent no-ops (no crash, no writes).
 *
 * The receiver is constructed fresh per dispatch: its internal scope cancels
 * itself after the first pending broadcast completes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NotificationActionReceiverTest {

    private val seenMediaRepository: SeenMediaRepository = mockk(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The receiver resolves the repository lazily from the Koin container
        // (the app composition root owns it in production).
        startKoin {
            modules(module { single<SeenMediaRepository> { seenMediaRepository } })
        }
        coEvery { seenMediaRepository.markAsSeen(itemId = any(), libraryId = any(), mediaType = any(), seenAtEpochMs = any()) } returns Unit
        coEvery { seenMediaRepository.markAsSeen(records = any()) } returns Unit
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun markSeenIntent(
        itemId: String? = "i1",
        libraryId: String? = "lib1",
        mediaType: String? = "MOVIE",
    ) = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_MARK_SEEN
        itemId?.let { putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, it) }
        libraryId?.let { putExtra(NotificationActionReceiver.EXTRA_LIBRARY_ID, it) }
        mediaType?.let { putExtra(NotificationActionReceiver.EXTRA_MEDIA_TYPE, it) }
    }

    private fun markAllSeenIntent(
        itemIds: Array<String> = arrayOf("a", "b"),
        libraryIds: Array<String> = arrayOf("libA", "libB"),
        mediaTypes: Array<String> = arrayOf("MOVIE", "EPISODE"),
        notificationIds: IntArray? = intArrayOf(11, 12, 13),
    ) = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_MARK_ALL_SEEN
        putExtra(NotificationActionReceiver.EXTRA_ITEM_IDS, itemIds)
        putExtra(NotificationActionReceiver.EXTRA_LIBRARY_IDS, libraryIds)
        putExtra(NotificationActionReceiver.EXTRA_MEDIA_TYPES, mediaTypes)
        notificationIds?.let { putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_IDS, it) }
    }

    private fun openDetailIntent(itemId: String? = "i1") =
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_OPEN_DETAIL
            itemId?.let { putExtra(NotificationActionReceiver.EXTRA_ITEM_ID, it) }
        }

    @Test
    fun `mark seen records one row built from the intent extras`() {
        NotificationActionReceiver().onReceive(context, markSeenIntent())

        coVerify(timeout = 5000, exactly = 1) {
            seenMediaRepository.markAsSeen(
                itemId = "i1",
                libraryId = "lib1",
                mediaType = "MOVIE",
                seenAtEpochMs = any(),
            )
        }
    }

    @Test
    fun `mark seen without a media type extra writes nothing`() {
        NotificationActionReceiver().onReceive(context, markSeenIntent(mediaType = null))

        coVerify(timeout = 5000, exactly = 0) { seenMediaRepository.markAsSeen(itemId = any(), libraryId = any(), mediaType = any(), seenAtEpochMs = any()) }
        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(records = any()) }
    }

    @Test
    fun `mark all seen zips the parallel arrays into one bulk write`() {
        NotificationActionReceiver().onReceive(context, markAllSeenIntent(notificationIds = null))

        coVerify(timeout = 5000, exactly = 1) {
            seenMediaRepository.markAsSeen(
                records = match<Iterable<SeenMediaRecord>> { actual ->
                    // Field-wise compare: seenAtEpochMs is stamped from the
                    // wall clock at write time, so record equality never holds.
                    val written = actual.toList()
                    written.map { Triple(it.itemId, it.libraryId, it.mediaType) } ==
                        listOf(
                            Triple("a", "libA", "MOVIE"),
                            Triple("b", "libB", "EPISODE"),
                        )
                },
            )
        }
    }

    @Test
    fun `mark all seen with mismatched parallel arrays writes nothing`() {
        NotificationActionReceiver().onReceive(
            context,
            markAllSeenIntent(itemIds = arrayOf("a", "b"), libraryIds = arrayOf("libA")),
        )

        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(records = any()) }
        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(itemId = any(), libraryId = any(), mediaType = any(), seenAtEpochMs = any()) }
    }

    @Test
    fun `mark all seen with empty item ids writes nothing`() {
        NotificationActionReceiver().onReceive(context, markAllSeenIntent(itemIds = emptyArray()))

        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(records = any()) }
    }

    @Test
    fun `mark all seen without library ids writes nothing`() {
        val intent = markAllSeenIntent()
        intent.removeExtra(NotificationActionReceiver.EXTRA_LIBRARY_IDS)

        NotificationActionReceiver().onReceive(context, intent)

        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(records = any()) }
    }

    @Test
    fun `open detail starts the package-scoped deep link for the item`() {
        NotificationActionReceiver().onReceive(context, openDetailIntent("item42"))

        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(android.net.Uri.parse("jellyplay://media/item42"), started.data)
        assertEquals(context.packageName, started.`package`)
        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(itemId = any(), libraryId = any(), mediaType = any(), seenAtEpochMs = any()) }
        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(records = any()) }
    }

    @Test
    fun `open detail without an item id starts nothing`() {
        NotificationActionReceiver().onReceive(context, openDetailIntent(itemId = null))

        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun `unknown action is a silent no-op`() {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.example.NOT_OUR_ACTION"
        }

        NotificationActionReceiver().onReceive(context, intent)

        assertNull(shadowOf(context as Application).nextStartedActivity)
        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(itemId = any(), libraryId = any(), mediaType = any(), seenAtEpochMs = any()) }
        coVerify(exactly = 0) { seenMediaRepository.markAsSeen(records = any()) }
    }
}
