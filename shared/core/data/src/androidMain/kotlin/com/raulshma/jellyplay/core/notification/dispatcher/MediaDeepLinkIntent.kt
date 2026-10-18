package com.raulshma.jellyplay.core.notification.dispatcher

import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar

/**
 * The one builder for a media deep-link launch Intent — the mirror-killer of
 * GLOSSARY's "Receiver intake fold": four byte-identical copies
 * (`Intent(ACTION_VIEW, Uri.parse(DeepLinkGrammar.mediaLink(id)))` +
 * NEW_TASK|CLEAR_TOP + setPackage) used to live across
 * [NotificationDispatcher]'s three content-intent sites and
 * NotificationActionReceiver's ACTION_OPEN_DETAIL rebuild. The link string
 * itself still comes from [DeepLinkGrammar.mediaLink] (the canonical
 * grammar); only the Android Intent furniture is centralized here, in the
 * notification family where every consumer lives. PendingIntent flags
 * differ per call site and stay there — this returns a bare, un-wrapped
 * Intent.
 *
 * Setters are chained directly on the Intent expression (not inside an
 * `Intent(...).apply { ... }` block) — CodeQL's implicit-PendingIntent
 * recognition only treats a setter call as making the intent explicit when
 * its receiver is the Intent expression itself; the `setPackage` scoping is
 * what keeps the PendingIntent from being hijacked by another app claiming
 * the scheme.
 */
internal fun mediaDeepLinkIntent(
    context: Context,
    itemId: String,
): Intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(DeepLinkGrammar.mediaLink(itemId)))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    .setPackage(context.packageName)
