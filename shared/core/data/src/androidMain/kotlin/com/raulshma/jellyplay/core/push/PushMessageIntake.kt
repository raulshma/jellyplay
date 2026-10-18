package com.raulshma.jellyplay.core.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The decoded verdict of a UnifiedPush message — the entry fold of
 * GLOSSARY's "Receiver intake fold" for [JellyPlayUnifiedPushReceiver]: the
 * sync-nudge/gate decision that used to live inline in onMessage, lifted
 * into pure Kotlin so it is JVM-testable without Robolectric.
 *
 * The verdict, in the receiver's documented order:
 *  - a SILENT `sync-nudge` payload (either spelling — see
 *    [isSyncNudge]) is [PushMessageVerdict.SyncNudge] REGARDLESS of
 *    [pushGateOpen]: a nudge targets the SYNC toggle, never the push one
 *    (registry v7's caps-gated data-only push);
 *  - any other payload rides the `push` gate (ADR 0010 — the registry is
 *    the ONLY gate): gate open → [PushMessageVerdict.SurfaceInTray], gate
 *    closed → [PushMessageVerdict.GateClosed] (a stale endpoint must keep
 *    notifying nobody).
 *
 * The gate input is a resolved Boolean, not a probe: the fold stays pure.
 * The receiver resolves it with a short-circuit so a nudge never pays the
 * (DataStore-reading) `push` gate probe — the fold re-checks the nudge
 * question first, so the two agree by construction.
 */
sealed interface PushMessageVerdict {
    /** Silent sync nudge — fold into the sync engine, never a notification. */
    data object SyncNudge : PushMessageVerdict

    /** Ordinary push payload while the user's `push` toggle reads off. */
    data object GateClosed : PushMessageVerdict

    /** Ordinary push payload past the gate — hand to the tray dispatcher. */
    data class SurfaceInTray(val payloadJson: String) : PushMessageVerdict
}

/**
 * Pure intake for the UnifiedPush payload. Owns the sync-nudge detection the
 * NotificationDispatcher used to re-implement on its instance (the delegate
 * keeps that member's signature; this is now the single implementation).
 */
object PushMessageIntake {

    /** The silent push kind (registry v7) — data-only, never a tray notification. */
    internal const val KIND_SYNC_NUDGE = "sync-nudge"

    /** The ntfy per-message header the kind rides in the ntfy JSON publish body. */
    internal const val NTFY_KIND_HEADER = "X-JellyPlay-Kind"

    // Same configuration the dispatcher's payload parsing uses; element
    // parsing never surfaces it, but the instance stays identical so the
    // moved check cannot drift from the dispatcher's parse behavior.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Whether [payloadJson] is the SILENT `sync-nudge` kind. The generic
     * UnifiedPush body carries `kind` at the top level; a relayed ntfy JSON
     * publish body carries it in the per-message `headers` map under
     * [NTFY_KIND_HEADER] — both spellings answer true (data-only either
     * way). Malformed JSON is simply not a nudge.
     */
    fun isSyncNudge(payloadJson: String): Boolean {
        val payload = runCatching { json.parseToJsonElement(payloadJson) }
            .getOrNull() as? JsonObject ?: return false

        fun text(element: kotlinx.serialization.json.JsonElement?, key: String): String? =
            (element as? JsonObject)
                ?.let { it[key] as? JsonPrimitive }
                ?.content

        if (text(payload, "kind") == KIND_SYNC_NUDGE) return true
        return text(payload["headers"], NTFY_KIND_HEADER) == KIND_SYNC_NUDGE
    }

    /**
     * Folds a payload plus the resolved `push` gate state into the verdict.
     * [pushGateOpen] must be resolved WITHOUT probing for nudge payloads —
     * see the class KDoc; the receiver's `&&` short-circuit is the blessed
     * shape.
     */
    fun verdict(
        payloadJson: String,
        pushGateOpen: Boolean,
    ): PushMessageVerdict = when {
        isSyncNudge(payloadJson) -> PushMessageVerdict.SyncNudge
        pushGateOpen -> PushMessageVerdict.SurfaceInTray(payloadJson)
        else -> PushMessageVerdict.GateClosed
    }
}
