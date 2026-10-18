package com.raulshma.jellyplay.core.data.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [downloadEntry] — the GLOSSARY "Receiver intake fold" decode of a
 * download-notification broadcast — on the pure JVM (no Robolectric). The
 * Robolectric end-to-end ([DownloadActionReceiverTest]) keeps pinning the
 * dispatch; the decode contract lives here:
 *
 * - Each known action decodes to its verb carrying the opaque `download_id`.
 * - The id is read BEFORE the action match (the receiver's original
 *   `?: return`), so a known action without an id is
 *   [DownloadEntry.Ignored] — and so is an unknown action with a valid id
 *   (the old `when` fell through).
 */
class DownloadEntryFoldTest {

    @Test
    fun `known actions decode to their verb with the id`() {
        assertEquals(
            DownloadEntry.Pause("dl-1"),
            downloadEntry(DownloadActionReceiver.ACTION_PAUSE, "dl-1"),
        )
        assertEquals(
            DownloadEntry.Resume("dl-1"),
            downloadEntry(DownloadActionReceiver.ACTION_RESUME, "dl-1"),
        )
        assertEquals(
            DownloadEntry.Cancel("dl-1"),
            downloadEntry(DownloadActionReceiver.ACTION_CANCEL, "dl-1"),
        )
    }

    @Test
    fun `a known action without an id is ignored`() {
        assertEquals(
            DownloadEntry.Ignored,
            downloadEntry(DownloadActionReceiver.ACTION_PAUSE, null),
        )
        assertEquals(
            DownloadEntry.Ignored,
            downloadEntry(DownloadActionReceiver.ACTION_RESUME, null),
        )
        assertEquals(
            DownloadEntry.Ignored,
            downloadEntry(DownloadActionReceiver.ACTION_CANCEL, null),
        )
    }

    @Test
    fun `an unknown or absent action with a valid id is ignored`() {
        assertEquals(
            DownloadEntry.Ignored,
            downloadEntry("com.example.NOT_OUR_ACTION", "dl-1"),
        )
        assertEquals(DownloadEntry.Ignored, downloadEntry(null, "dl-1"))
    }
}
