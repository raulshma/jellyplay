package com.raulshma.jellyplay.deeplink

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the incoming-intent classification fold — the when-chain that used to
 * live inline in `MainActivity.handleIncomingIntent` plus the shortcut
 * action → Route table that used to live in `MainViewModel.handleShortcutIntent`:
 *
 *  - the launcher-shortcut prefix wins over every standard action, and each
 *    known shortcut action maps 1:1 to its route (PLAY_AUDIO only with an
 *    item id; SURPRISE_ME additionally arms the surprise-on-launch signal);
 *    an unknown shortcut action classifies to a null-route Shortcut — it
 *    must NOT fall through to the other arms;
 *  - ACTION_VIEW (with data), ACTION_SEND text/plain (with EXTRA_TEXT),
 *    ACTION_SEARCH / the GLOBAL_SEARCH alias / ACTION_ASSIST (with a
 *    non-blank query, ASSIST falling back from its hidden input extra to
 *    SearchManager.QUERY) each classify to their disposition, and a missing
 *    or blank payload classifies to None rather than a half-armed dispatch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IncomingIntentRequestTest {

    private fun classify(intent: Intent): IncomingIntentDisposition =
        IncomingIntentRequest.from(intent).classify()

    private fun shortcutIntent(action: String, build: Intent.() -> Unit = {}): Intent =
        Intent(action).apply(build)

    // ── launcher shortcut routing (ported from MainViewModelTest) ─────────

    @Test
    fun `shortcut actions route to their routes`() {
        assertEquals(
            Route.NewsletterSectionList("CONTINUE_WATCHING"),
            (classify(shortcutIntent(AppShortcutManager.ACTION_CONTINUE_WATCHING)) as IncomingIntentDisposition.Shortcut).route,
        )
        assertEquals(
            Route.Search,
            (classify(shortcutIntent(AppShortcutManager.ACTION_SEARCH)) as IncomingIntentDisposition.Shortcut).route,
        )
        assertEquals(
            Route.MusicBrowse,
            (classify(shortcutIntent(AppShortcutManager.ACTION_PLAY_MUSIC)) as IncomingIntentDisposition.Shortcut).route,
        )
        assertEquals(
            Route.Downloads,
            (classify(shortcutIntent(AppShortcutManager.ACTION_DOWNLOADS)) as IncomingIntentDisposition.Shortcut).route,
        )
        assertEquals(
            Route.Settings,
            (classify(shortcutIntent(AppShortcutManager.ACTION_SETTINGS)) as IncomingIntentDisposition.Shortcut).route,
        )
    }

    @Test
    fun `play-audio shortcut with an item id routes to the audio player`() {
        val disposition = classify(
            shortcutIntent(AppShortcutManager.ACTION_PLAY_AUDIO) {
                putExtra(AppShortcutManager.EXTRA_ITEM_ID, "track-7")
            },
        )
        assertEquals(Route.AudioPlayer("track-7"), (disposition as IncomingIntentDisposition.Shortcut).route)
    }

    @Test
    fun `play-audio shortcut without an item id is a null-route shortcut`() {
        val disposition = classify(shortcutIntent(AppShortcutManager.ACTION_PLAY_AUDIO))
        assertEquals(IncomingIntentDisposition.Shortcut(route = null), disposition)
    }

    @Test
    fun `unknown shortcut action is a null-route shortcut`() {
        // Still a Shortcut — the prefix is checked first, so an unknown
        // member of the family must never fall through to the search or
        // shared-text arms.
        val disposition = classify(shortcutIntent("com.raulshma.jellyplay.action.UNKNOWN"))
        assertEquals(IncomingIntentDisposition.Shortcut(route = null), disposition)
    }

    @Test
    fun `surprise-me shortcut routes home and arms the one-shot launch flag`() {
        val disposition = classify(shortcutIntent(AppShortcutManager.ACTION_SURPRISE_ME))
        assertEquals(IncomingIntentDisposition.Shortcut(Route.Home, armsSurpriseOnLaunch = true), disposition)
    }

    @Test
    fun `other shortcuts do not arm the surprise launch flag`() {
        val disposition = classify(shortcutIntent(AppShortcutManager.ACTION_SEARCH))
        assertFalse((disposition as IncomingIntentDisposition.Shortcut).armsSurpriseOnLaunch)
    }

    @Test
    fun `shortcut prefix wins over a standard action carrying data`() {
        // The prefix check precedes the ACTION_VIEW arm — a shortcut intent
        // that happens to carry data is still a shortcut.
        val intent = Intent(AppShortcutManager.ACTION_SEARCH, Uri.parse("jellyplay://media/abc123"))
        assertEquals(
            IncomingIntentDisposition.Shortcut(Route.Search),
            classify(intent),
        )
    }

    // ── deep links ────────────────────────────────────────────────────────

    @Test
    fun `action view with data classifies as a deep link`() {
        assertEquals(
            IncomingIntentDisposition.DeepLink,
            classify(Intent(Intent.ACTION_VIEW, Uri.parse("jellyplay://media/abc123"))),
        )
    }

    @Test
    fun `action view without data classifies as none`() {
        assertEquals(IncomingIntentDisposition.None, classify(Intent(Intent.ACTION_VIEW)))
    }

    // ── shared text ───────────────────────────────────────────────────────

    @Test
    fun `action send text plain with extra text classifies as shared text`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "interstellar")
        assertEquals(IncomingIntentDisposition.SharedText("interstellar"), classify(intent))
    }

    @Test
    fun `action send text plain without extra text classifies as none`() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        assertEquals(IncomingIntentDisposition.None, classify(intent))
    }

    @Test
    fun `action send with a foreign mime type classifies as none`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TEXT, "interstellar")
        assertEquals(IncomingIntentDisposition.None, classify(intent))
    }

    // ── search arms ───────────────────────────────────────────────────────

    @Test
    fun `action search with a query classifies as search`() {
        val intent = shortcutIntent(Intent.ACTION_SEARCH) {
            putExtra(SearchManager.QUERY, "dune")
        }
        assertEquals(IncomingIntentDisposition.Search("dune"), classify(intent))
    }

    @Test
    fun `global search alias classifies as search`() {
        val intent = shortcutIntent("android.search.action.GLOBAL_SEARCH") {
            putExtra(SearchManager.QUERY, "dune")
        }
        assertEquals(IncomingIntentDisposition.Search("dune"), classify(intent))
    }

    @Test
    fun `blank search queries classify as none`() {
        val intent = shortcutIntent(Intent.ACTION_SEARCH) {
            putExtra(SearchManager.QUERY, "   ")
        }
        assertEquals(IncomingIntentDisposition.None, classify(intent))
    }

    @Test
    fun `search actions without a query classify as none`() {
        assertEquals(IncomingIntentDisposition.None, classify(shortcutIntent(Intent.ACTION_SEARCH)))
    }

    @Test
    fun `action assist reads its hidden input extra first`() {
        val intent = shortcutIntent(Intent.ACTION_ASSIST) {
            putExtra("android.intent.extra.ASSIST_INPUT", "play jazz")
            putExtra(SearchManager.QUERY, "ignored")
        }
        assertEquals(IncomingIntentDisposition.Search("play jazz"), classify(intent))
    }

    @Test
    fun `action assist falls back to the search query extra`() {
        val intent = shortcutIntent(Intent.ACTION_ASSIST) {
            putExtra(SearchManager.QUERY, "play jazz")
        }
        assertEquals(IncomingIntentDisposition.Search("play jazz"), classify(intent))
    }

    @Test
    fun `action assist without any query extra classifies as none`() {
        assertEquals(IncomingIntentDisposition.None, classify(shortcutIntent(Intent.ACTION_ASSIST)))
    }

    // ── no match ──────────────────────────────────────────────────────────

    @Test
    fun `null action classifies as none`() {
        assertEquals(IncomingIntentDisposition.None, classify(Intent()))
    }

    @Test
    fun `unrelated action classifies as none`() {
        assertEquals(IncomingIntentDisposition.None, classify(shortcutIntent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `sealed disposition vocabulary has exactly the five dispatched arms`() {
        // Reflective exhaustiveness guard: the dispositions MainActivity
        // dispatches on are exactly these five — a new sealed subclass must
        // come with a classifier arm, a dispatch arm, and a row here.
        val sealed = IncomingIntentDisposition::class.sealedSubclasses
            .mapNotNull { it.simpleName }
            .sorted()
        assertEquals(
            listOf("DeepLink", "None", "Search", "SharedText", "Shortcut"),
            sealed,
        )
    }
}
