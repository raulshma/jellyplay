package com.raulshma.jellyplay.desktop.discord

import com.raulshma.jellyplay.core.data.playback.NowPlayingReporter
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget
import java.util.UUID
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The pure presence mapping (feature 4.2): a now-playing snapshot + the
 * SyncPlay group state fold into ONE [DiscordActivitySpec] (or null = clear
 * the activity), and the spec serializes into the Discord `SET_ACTIVITY`
 * frame payload. Everything here is a pure function so the truth table is
 * pinned directly by `DiscordPresenceMappingTest`.
 */
internal object DiscordPresenceMapping {

    /** Discord activity types (the stable Rich Presence subset). */
    const val ACTIVITY_TYPE_LISTENING = 2
    const val ACTIVITY_TYPE_WATCHING = 3

    /** Discord caps a party at 16 members; the group count clamps into 1..16. */
    const val PARTY_MAX = 16

    /** A fresh request nonce — pairs a `SET_ACTIVITY` with Discord's reply. */
    fun newNonce(): String = UUID.randomUUID().toString()

    /**
     * Maps one now-playing snapshot + SyncPlay state to the activity to
     * publish, or null when the shell should clear (nothing playing).
     *
     *  - video → WATCHING, details = title, state = the episode/series line;
     *  - music → LISTENING, details = track, state = artist;
     *  - an end timestamp rides the activity when the item length is known
     *    and the engine is playing (now + remaining) — paused items show no
     *    countdown;
     *  - an active SyncPlay group attaches the party (size = participant
     *    count clamped into 1..[PARTY_MAX], 0 → 1) and the Join secret
     *    ([DeepLinkGrammar.syncPlayJoinLink] payload, decodable by
     *    [decodeJoinSecret]).
     */
    fun mapActivity(
        meta: NowPlayingReporter.NowPlayingMeta?,
        isPlaying: Boolean,
        group: SyncPlayGroup?,
        nowMs: Long,
    ): DiscordActivitySpec? {
        meta ?: return null
        val type = when (meta.kind) {
            NowPlayingReporter.Kind.VIDEO -> ACTIVITY_TYPE_WATCHING
            NowPlayingReporter.Kind.MUSIC -> ACTIVITY_TYPE_LISTENING
        }
        val endMs = meta.durationMs
            ?.takeIf { it > 0 && isPlaying }
            ?.let { duration -> nowMs + (duration - meta.positionMs).coerceAtLeast(0L) }
        val groupId = group?.groupId?.takeIf { it.isNotBlank() }
        return DiscordActivitySpec(
            type = type,
            details = meta.title.take(DISCORD_DETAILS_MAX_CHARS),
            state = meta.subtitle.takeIf { it.isNotBlank() }?.take(DISCORD_STATE_MAX_CHARS),
            endTimestampMs = endMs,
            partyId = groupId?.let { "syncplay:$it" },
            partySize = group?.participantCount?.coerceIn(1, PARTY_MAX) ?: 1,
            joinSecret = groupId?.let(DeepLinkGrammar::syncPlayJoinLink),
        )
    }

    /**
     * The `SET_ACTIVITY` frame payload for [spec] (null → `activity: null`,
     * the documented clear shape). [nonce] pairs the request with Discord's
     * reply.
     */
    fun setActivityPayload(spec: DiscordActivitySpec?, pid: Long, nonce: String): String =
        buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("nonce", nonce)
            putJsonObject("args") {
                put("pid", pid)
                if (spec == null) {
                    put("activity", JsonNull)
                } else {
                    put("activity", spec.toJsonObject())
                }
            }
        }.toString()

    /**
     * Decodes a Join secret back into the SyncPlay group id — the exact
     * inverse of the [DeepLinkGrammar.syncPlayJoinLink] payload
     * [mapActivity] emits. Null for foreign/unknown secrets (Discord only
     * hands back what this app published, but the input is external data).
     */
    fun decodeJoinSecret(secret: String): String? {
        val withoutScheme = secret.removePrefix("${DeepLinkGrammar.SCHEME_CUSTOM}://")
        if (withoutScheme == secret) return null
        val host = withoutScheme.substringBefore('/')
        if (host != DeepLinkGrammar.HOST_SYNCPLAY) return null
        val groupId = withoutScheme.substringAfter('/', "").takeIf { it.isNotBlank() } ?: return null
        val target = DeepLinkGrammar.parseCustom(host, listOf(groupId))
        return (target as? DeepLinkTarget.SyncPlayJoin)?.groupId
    }

    /**
     * Discord truncates details/state hard (128 bytes each documented);
     * trimming by chars keeps the JSON payload inside the frame cap even for
     * long titles.
     */
    private const val DISCORD_DETAILS_MAX_CHARS = 96
    private const val DISCORD_STATE_MAX_CHARS = 96

    private fun DiscordActivitySpec.toJsonObject() = buildJsonObject {
        put("type", type)
        put("details", details)
        state?.let { put("state", it) }
        if (endTimestampMs != null || startTimestampMs != null) {
            putJsonObject("timestamps") {
                startTimestampMs?.let { put("start", it) }
                endTimestampMs?.let { put("end", it) }
            }
        }
        if (partyId != null) {
            putJsonObject("party") {
                put("id", partyId)
                // Discord's party shape is [current, max].
                putJsonArray("size") {
                    add(partySize)
                    add(PARTY_MAX)
                }
            }
        }
        if (joinSecret != null) {
            putJsonObject("secrets") {
                put("join", joinSecret)
            }
        }
        put("instance", true)
    }
}

/**
 * One activity to publish — deliberately payload-library-free (a plain data
 * class) so the mapping truth table needs no JSON in its assertions; the
 * serialization half ([DiscordPresenceMapping.setActivityPayload]) is pinned
 * separately.
 */
internal data class DiscordActivitySpec(
    val type: Int,
    val details: String,
    val state: String?,
    val startTimestampMs: Long? = null,
    val endTimestampMs: Long? = null,
    /** `syncplay:{groupId}` — Discord groups parties by id equality. */
    val partyId: String? = null,
    val partySize: Int = 1,
    /** `jellyplay://syncplay/{groupId}` — the Join button's secret payload. */
    val joinSecret: String? = null,
)
