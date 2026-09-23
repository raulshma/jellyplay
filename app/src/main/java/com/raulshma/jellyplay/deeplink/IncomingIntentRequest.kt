package com.raulshma.jellyplay.deeplink

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.data.shortcuts.AppShortcutManager
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * The pure fold over MainActivity's incoming [Intent]: it classifies an
 * intent into the [IncomingIntentDisposition] the Activity dispatches on —
 * the launcher-shortcut prefix, the ACTION_VIEW deep-link arm, the
 * ACTION_SEND shared-text arm, the ACTION_SEARCH / GLOBAL_SEARCH /
 * ACTION_ASSIST search arms, or [IncomingIntentDisposition.None] — and owns
 * the launcher-shortcut action → [Route] table (formerly inlined in
 * `MainViewModel.handleShortcutIntent`) plus the string-literal action
 * vocabulary that used to live in `MainActivity.handleIncomingIntent`.
 *
 * Same split as [DeepLinkHandler]: Android types only travel in as
 * already-decoded values (`action`/`type`/`data` plus the decoded
 * name→value string-extra snapshot, built by [from]), so the classification
 * and the shortcut table stay plain Kotlin and unit-testable — the
 * Activity keeps a single when-dispatch and hands the already-classified
 * [IncomingIntentDisposition] to the ViewModel, so Android types never
 * travel past this fold.
 *
 * The arm order below is load-bearing and mirrors the former when-chain:
 * the shortcut prefix is checked FIRST (a shortcut action wins over every
 * standard action), then VIEW/SEND/SEARCH/ASSIST, each falling through to
 * [IncomingIntentDisposition.None] when its payload is missing.
 */
data class IncomingIntentRequest(
    val action: String?,
    val type: String?,
    val data: Uri?,
    val extras: Map<String, String?>,
) {

    /**
     * Classifies this request. [IncomingIntentDisposition.Shortcut.route] is
     * `null` for an unknown shortcut action or a PLAY_AUDIO without an item
     * id — the intent is still a shortcut (it must not fall through to the
     * search/shared-text arms), the shell just has nowhere to send it.
     */
    fun classify(): IncomingIntentDisposition {
        val action = action ?: return IncomingIntentDisposition.None
        if (action.startsWith(SHORTCUT_ACTION_PREFIX)) {
            val itemId = extras[AppShortcutManager.EXTRA_ITEM_ID]
            return IncomingIntentDisposition.Shortcut(
                route = shortcutActionRoute(action, itemId),
                armsSurpriseOnLaunch = action == AppShortcutManager.ACTION_SURPRISE_ME,
            )
        }
        if (action == Intent.ACTION_VIEW && data != null) {
            return IncomingIntentDisposition.DeepLink
        }
        if (action == Intent.ACTION_SEND && type == "text/plain") {
            val sharedText = extras[Intent.EXTRA_TEXT] ?: return IncomingIntentDisposition.None
            return IncomingIntentDisposition.SharedText(sharedText)
        }
        if (action == Intent.ACTION_SEARCH || action == GLOBAL_SEARCH_ACTION) {
            val query = extras[SearchManager.QUERY]
            if (!query.isNullOrBlank()) return IncomingIntentDisposition.Search(query)
            return IncomingIntentDisposition.None
        }
        if (action == Intent.ACTION_ASSIST) {
            // ACTION_ASSIST uses hidden extras (android.intent.extra.ASSIST_INPUT); fall back to
            // SearchManager.QUERY for some launchers.
            val query = extras[ASSIST_INPUT_EXTRA] ?: extras[SearchManager.QUERY]
            if (!query.isNullOrBlank()) return IncomingIntentDisposition.Search(query)
            return IncomingIntentDisposition.None
        }
        return IncomingIntentDisposition.None
    }

    companion object {
        /**
         * Decodes an [Intent] into the pure request: the action/type/data
         * triple plus a string-extra snapshot over the fixed vocabulary this
         * fold reads (the same values `getStringExtra` would return, absent
         * keys included as `null`).
         */
        fun from(intent: Intent): IncomingIntentRequest = IncomingIntentRequest(
            action = intent.action,
            type = intent.type,
            data = intent.data,
            extras = buildMap {
                for (key in STRING_EXTRA_KEYS) put(key, intent.getStringExtra(key))
            },
        )

        // The shortcut family prefix — every AppShortcutManager action
        // constant starts with it; the dispatch must not depend on the
        // (growing) list of known shortcuts.
        private const val SHORTCUT_ACTION_PREFIX = "com.raulshma.jellyplay.action."

        // Assistant/launcher search arms: the documented alias some launchers
        // fire instead of ACTION_SEARCH, and ACTION_ASSIST's hidden input extra.
        private const val GLOBAL_SEARCH_ACTION = "android.search.action.GLOBAL_SEARCH"
        private const val ASSIST_INPUT_EXTRA = "android.intent.extra.ASSIST_INPUT"

        private val STRING_EXTRA_KEYS = setOf(
            AppShortcutManager.EXTRA_ITEM_ID,
            Intent.EXTRA_TEXT,
            SearchManager.QUERY,
            ASSIST_INPUT_EXTRA,
        )

        /**
         * The launcher-shortcut action → Route table. Kept 1:1 with the
         * former `MainViewModel.handleShortcutIntent` when-expression
         * (SURPRISE_ME routes home and additionally arms the one-shot
         * surprise-on-launch signal, surfaced as
         * [IncomingIntentDisposition.Shortcut.armsSurpriseOnLaunch]).
         */
        private fun shortcutActionRoute(action: String, itemId: String?): Route? = when (action) {
            AppShortcutManager.ACTION_CONTINUE_WATCHING ->
                Route.NewsletterSectionList(DeepLinkGrammar.NEWSLETTER_SECTION_CONTINUE_WATCHING)
            AppShortcutManager.ACTION_SEARCH -> Route.Search
            AppShortcutManager.ACTION_PLAY_MUSIC -> Route.MusicBrowse
            AppShortcutManager.ACTION_DOWNLOADS -> Route.Downloads
            AppShortcutManager.ACTION_PLAY_AUDIO -> {
                if (!itemId.isNullOrBlank()) Route.AudioPlayer(itemId) else null
            }
            // Static launcher shortcuts
            AppShortcutManager.ACTION_SETTINGS -> Route.Settings
            AppShortcutManager.ACTION_SURPRISE_ME -> Route.Home
            else -> null
        }
    }
}

/**
 * What [IncomingIntentRequest.classify] decided an incoming intent wants.
 * The Activity maps each case onto exactly one shell entry point; [None]
 * means dispatch nothing.
 */
sealed interface IncomingIntentDisposition {
    /**
     * A launcher-shortcut intent (`com.raulshma.jellyplay.action.*`). [route]
     * is the table lookup result — `null` when the shell has nowhere to send
     * it. [armsSurpriseOnLaunch] is set by the SURPRISE_ME shortcut so the
     * shell also arms the Home hero's surprise signal.
     */
    data class Shortcut(
        val route: Route?,
        val armsSurpriseOnLaunch: Boolean = false,
    ) : IncomingIntentDisposition

    /** ACTION_VIEW with data — the [DeepLinkHandler] resolves the route. */
    data object DeepLink : IncomingIntentDisposition

    /** ACTION_SEND text/plain with an EXTRA_TEXT payload. */
    data class SharedText(val sharedText: String) : IncomingIntentDisposition

    /** ACTION_SEARCH / GLOBAL_SEARCH / ACTION_ASSIST with a non-blank query. */
    data class Search(val query: String) : IncomingIntentDisposition

    /** No arm matched (or the matched arm's payload was missing/blank). */
    data object None : IncomingIntentDisposition
}
