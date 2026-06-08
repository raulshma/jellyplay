package com.raulshma.jellyplay.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.deeplink.DeepLinkHandler

/**
 * Centralised deep-link construction for the recommendations widgets.
 *
 * Two flavours:
 *   * [buildMediaDeepLink] — for the library widget, opens a Jellyfin
 *     media detail screen for the given item id.
 *   * [buildSeerrDeepLink] — for the Seerr widget, opens the
 *     `Route.SeerrDetail` screen for the given TMDB id.
 */
object WidgetDeepLinks {

    fun buildMediaDeepLink(itemId: String): String =
        "${DeepLinkHandler.SCHEME_CUSTOM}://media/$itemId"

    fun buildSeerrDeepLink(tmdbId: Int, mediaType: String): String =
        "${DeepLinkHandler.SCHEME_CUSTOM}://seerr/$tmdbId/$mediaType"

    fun openAppIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    fun openUriIntent(context: Context, uri: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(uri)
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
