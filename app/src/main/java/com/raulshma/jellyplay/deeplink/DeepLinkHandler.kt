package com.raulshma.jellyplay.deeplink

import android.content.Intent
import android.net.Uri
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.toRoute

/**
 * Android Intent/Uri glue over the pure [DeepLinkGrammar]. Every link shape
 * is built and parsed by the grammar — the [DeepLinkGrammar] companion is the
 * single emission surface (widgets/notifications/shortcuts call it directly),
 * so :app and the modules that cannot depend on it (core:data,
 * core:notification, shared:feature:details) emit and read exactly the same
 * URLs. Routing rides the shared [DeepLinkTarget.toRoute] fold beside
 * [Route] in core:ui (the one target→destination table the desktop shell
 * consumes too) — this class owns only the Uri→target parsing glue.
 */
class DeepLinkHandler {

    fun parse(intent: Intent): Route? {
        val uri = intent.data ?: return null
        return parseUri(uri)
    }

    private fun parseUri(uri: Uri): Route? {
        return when (uri.scheme) {
            DeepLinkGrammar.SCHEME_CUSTOM -> parseCustomScheme(uri)
            DeepLinkGrammar.SCHEME_HTTPS -> parseHttpsScheme(uri)
            else -> null
        }
    }

    private fun parseCustomScheme(uri: Uri): Route? {
        val host = uri.host ?: return null
        // Decoded name→value map so the grammar stays Android-free.
        val queryParameters: Map<String, String> = buildMap {
            for (name in uri.queryParameterNames) {
                uri.getQueryParameter(name)?.let { put(name, it) }
            }
        }
        return DeepLinkGrammar.parseCustom(host, uri.pathSegments, queryParameters).toRoute()
    }

    private fun parseHttpsScheme(uri: Uri): Route? {
        if (uri.host != DeepLinkGrammar.HOST_WEB) return null
        return DeepLinkGrammar.parseWeb(uri.pathSegments).toRoute()
    }
}
