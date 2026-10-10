package com.raulshma.jellyplay.core.data

import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.datastore.settings.ExternalBackupSlice
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.data.repository.withTransaction
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.dao.MoodPlaylistDao
import com.raulshma.jellyplay.core.database.dao.SmartPlaylistDao
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.model.ItemPlaybackPreferencePayload
import com.raulshma.jellyplay.core.model.MoodPlaylistPayload
import com.raulshma.jellyplay.core.model.MoodPlaylistPreferencePayload
import com.raulshma.jellyplay.core.model.SmartPlaylistPayload
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * The Wave-2 [ExternalBackupSlice] sources: the concrete slice readers/restorers
 * for the config that lives OUTSIDE the 19 domain-store fan-out — Room-backed
 * rows (item prefs, playlists) and the allowlisted config surfaces (integration
 * settings, widget config). They live in core:data because the Room DAOs do;
 * core:datastore only sees the [ExternalBackupSlice] seam. Koin registers each
 * source as an `ExternalBackupSlice` (dataSettingsBackupSlicesModule) and
 * `UserPreferencesStore` folds them into snapshot/restore via `getAll`.
 *
 * All four implement the seam's WHOLESALE-REPLACE contract: an incoming slice
 * REPLACES the domain's rows — identity keys absent from the incoming object
 * are deleted (or reset to default), the rest are upserted — and every decode
 * is lenient (a malformed entry is skipped, never fatal), matching the
 * forward-compat tolerance the v2 slice imports run on.
 */

/** The encodeDefaults/ignoreUnknownKeys codec stance the sync adapters use for the same payloads. */
internal val SliceJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * The `integrations` slice: the NON-SECRET settings of the three integration
 * preference stores, read/applied THROUGH the stores' sync-only surfaces
 * (`syncSnapshot`/`syncApply`) — the allowlists inside the stores stay the
 * single source of truth for which keys may ever leave/enter, so no key name
 * is re-declared here and the secrets (api keys, passwords, cookies) can never
 * ride the slice. Value shape: one JSON object `{"<raw key>": <primitive>}`
 * with the prefs-adapter kind inference (strict true/false → boolean, integer
 * → int, else string) — the raw string is what round-trips; the receiving
 * store re-kinds by its own allowlist.
 */
internal class IntegrationsBackupSliceSource(
    private val fan: IntegrationsStoreFan,
) : ExternalBackupSlice {

    override val key: String = BackupSliceKey.INTEGRATIONS

    override suspend fun read(): JsonElement? {
        val snapshot = fan.snapshot()
        return JsonObject(snapshot).takeIf { snapshot.isNotEmpty() }
    }

    override suspend fun restore(element: JsonElement) {
        val obj = element as? JsonObject ?: return
        // Wholesale replace: an allowlisted key absent from the incoming object
        // resets to its default (the exporting device stored nothing there).
        // The stores' allowlists ignore strangers in both directions.
        for (name in allowlistedKeys) if (name !in obj) fan.apply(name, null)
        for ((name, value) in obj) {
            val raw = when (value) {
                is JsonNull -> null
                is JsonPrimitive -> value.content
                // Objects/arrays unsupported — skipped (the garbage-row rule).
                else -> continue
            }
            fan.apply(name, raw)
        }
    }

    private val allowlistedKeys: Set<String> =
        fan.seerrPreferencesStore.SyncKeys +
            fan.arrPreferencesStore.SyncKeys +
            fan.subtitleProviderPreferencesStore.SyncKeys
}

/**
 * The `itemPrefs` slice: EVERY per-item/per-series playback-preference row —
 * the local backup is deliberately NOT capped (the sync adapter's 100-row
 * roam cap bounds the SYNC SET only; the Room store stays unbounded and so
 * does this slice). Keys are `"{scope}/{key}"`, values the full
 * [ItemPlaybackPreferencePayload] (the same wire payload the `itemprefs` sync
 * namespace carries — install-local Room ids ride NOWHERE; the autogen `id`
 * column is re-assigned by the upsert).
 */
internal class ItemPrefsBackupSliceSource(
    private val database: JellyPlayDatabase,
    private val dao: ItemPlaybackPreferenceDao,
) : ExternalBackupSlice {

    override val key: String = BackupSliceKey.ITEM_PREFS

    override suspend fun read(): JsonElement? {
        val rows = dao.getAll()
        if (rows.isEmpty()) return null
        return buildJsonObject {
            for (row in rows) put(row.backupKey(), SliceJson.encodeToJsonElement(row.toPayload()))
        }
    }

    override suspend fun restore(element: JsonElement) {
        val obj = element as? JsonObject ?: return
        val incoming = LinkedHashMap<Pair<String, String>, ItemPlaybackPreferencePayload>()
        for ((name, value) in obj) {
            val (scope, rowKey) = parseSlashKey(name) ?: continue
            if (value is JsonNull) continue
            val payload = value.toPayloadOrNull(ItemPlaybackPreferencePayload.serializer()) ?: continue
            // The payload must agree with its own key — a mismatched pair is a
            // garbage entry, skipped like any uncoercible value.
            if (payload.scope != scope || payload.key != rowKey) continue
            incoming[scope to rowKey] = payload
        }
        // Wholesale replace, TRANSACTIONALLY (the ServerBackupMetadataStore
        // pattern): rows absent from the incoming map are deleted, the rest
        // upserted (the (scope, key) unique index makes REPLACE an upsert) —
        // one write transaction, so a crash mid-restore cannot leave the
        // domain half-replaced.
        database.withTransaction {
            for (row in dao.getAll()) {
                if (row.scope to row.key !in incoming) dao.deleteByKey(row.scope, row.key)
            }
            for ((identity, payload) in incoming) {
                dao.upsert(entityFrom(payload, identity.first, identity.second))
            }
        }
    }
}

/**
 * The `playlists` slice: the smart/mood playlist DEFINITIONS plus the
 * per-playlist user preferences — whole rows over the three Room tables (the
 * same payloads the `playlists` sync namespace carries; no cached item lists
 * exist anywhere). Keys `smart/{id}` / `mood/{id}` / `moodpref/{playlistId}`.
 */
internal class PlaylistsBackupSliceSource(
    private val database: JellyPlayDatabase,
    private val smartPlaylistDao: SmartPlaylistDao,
    private val moodPlaylistDao: MoodPlaylistDao,
) : ExternalBackupSlice {

    override val key: String = BackupSliceKey.PLAYLISTS

    override suspend fun read(): JsonElement? {
        val smart = smartPlaylistDao.getAll()
        val mood = moodPlaylistDao.getAll()
        val moodPrefs = moodPlaylistDao.getAllPreferences()
        if (smart.isEmpty() && mood.isEmpty() && moodPrefs.isEmpty()) return null
        return buildJsonObject {
            for (row in smart) put("smart/${row.id}", SliceJson.encodeToJsonElement(row.toPayload()))
            for (row in mood) put("mood/${row.id}", SliceJson.encodeToJsonElement(row.toPayload()))
            for (row in moodPrefs) put("moodpref/${row.playlistId}", SliceJson.encodeToJsonElement(row.toPayload()))
        }
    }

    override suspend fun restore(element: JsonElement) {
        val obj = element as? JsonObject ?: return
        val smart = LinkedHashMap<String, SmartPlaylistPayload>()
        val mood = LinkedHashMap<String, MoodPlaylistPayload>()
        val moodPrefs = LinkedHashMap<String, MoodPlaylistPreferencePayload>()
        for ((name, value) in obj) {
            val (family, id) = parseSlashKey(name) ?: continue
            if (value is JsonNull) continue
            when (family) {
                FAMILY_SMART -> value.toPayloadOrNull(SmartPlaylistPayload.serializer())
                    ?.takeIf { it.id == id }
                    ?.let { smart[it.id] = it }

                FAMILY_MOOD -> value.toPayloadOrNull(MoodPlaylistPayload.serializer())
                    ?.takeIf { it.id == id }
                    ?.let { mood[it.id] = it }

                FAMILY_MOODPREF -> value.toPayloadOrNull(MoodPlaylistPreferencePayload.serializer())
                    ?.takeIf { it.playlistId == id }
                    ?.let { moodPrefs[it.playlistId] = it }
            }
        }
        // Wholesale replace, per family, each family pass in its OWN write
        // transaction (the ServerBackupMetadataStore pattern): rows absent
        // from the incoming map are deleted, the rest upserted (the id IS the
        // primary key — REPLACE is a true replace). A crash mid-restore can
        // then only ever leave one family untouched, never half-replaced.
        database.withTransaction {
            for (row in smartPlaylistDao.getAll()) {
                if (row.id !in smart) smartPlaylistDao.deleteById(row.id)
            }
            for (payload in smart.values) smartPlaylistDao.insert(entityFrom(payload))
        }

        database.withTransaction {
            for (row in moodPlaylistDao.getAll()) {
                if (row.id !in mood) moodPlaylistDao.deleteById(row.id)
            }
            for (payload in mood.values) moodPlaylistDao.insert(entityFrom(payload))
        }

        database.withTransaction {
            for (row in moodPlaylistDao.getAllPreferences()) {
                if (row.playlistId !in moodPrefs) moodPlaylistDao.deletePreferenceById(row.playlistId)
            }
            for (payload in moodPrefs.values) moodPlaylistDao.upsertPreference(entityFrom(payload))
        }
    }
}

/**
 * The `widget` slice: the home-screen widget CONFIG only (`widget_config` +
 * `widget_configs`), over [WidgetDataStore]'s config-only surface. The
 * payload-cache keys (continue-watching / library / Seerr item buffers) stay
 * out — they are derived I/O state, never a setting. The JSON blobs ride as
 * decoded JSON (the config object / per-widget-id map), not as escaped
 * strings, so the slice is readable and diffs cleanly in the preview.
 */
internal class WidgetBackupSliceSource(
    private val widgetDataStore: WidgetDataStore,
) : ExternalBackupSlice {

    override val key: String = BackupSliceKey.WIDGET

    override suspend fun read(): JsonElement? {
        val snapshot = widgetDataStore.configSnapshot()
        val obj = buildJsonObject {
            for ((name, raw) in snapshot) {
                val element = raw?.toJsonElementOrNull() ?: continue
                put(name, element)
            }
        }
        return obj.takeIf { it.isNotEmpty() }
    }

    override suspend fun restore(element: JsonElement) {
        val obj = element as? JsonObject ?: return
        // Wholesale replace over the config allowlist: a config key absent from
        // the incoming object resets to default (removed); payload-cache keys
        // are unreachable — the allowlist ignores them in BOTH directions.
        for (name in widgetDataStore.ConfigKeys) {
            when (val value = obj[name]) {
                null, is JsonNull -> widgetDataStore.configApply(name, null)
                else -> widgetDataStore.configApply(name, value.toRawString())
            }
        }
    }

    /** JSON text → element: the persisted blobs are JSON, so decode them verbatim (lenient). */
    private fun String.toJsonElementOrNull(): JsonElement? = runCatching {
        SliceJson.parseToJsonElement(this)
    }.getOrNull()

    /** Element → the raw string the store persists: objects/arrays verbatim, string primitives unwrapped. */
    private fun JsonElement.toRawString(): String = when (this) {
        is JsonPrimitive -> if (isString) content else toString()
        else -> toString()
    }
}

// ----------------------------------------------------------------------
// Shared helpers (slash keying + lenient payload decode + the Wave-1
// payload <-> Room entity mappers; these live beside the DAO-backed sources
// that consume them, and the sync adapters in core.data.repository share
// them instead of keeping private copies).
// ----------------------------------------------------------------------

/** The `smart/` key family (playlist definitions). */
internal const val FAMILY_SMART = "smart"

/** The `mood/` key family (playlist definitions). */
internal const val FAMILY_MOOD = "mood"

/** The `moodpref/` key family (per-playlist user preferences). */
internal const val FAMILY_MOODPREF = "moodpref"

/** `"{scope}/{key}"` → pair; anything without exactly one leading segment is malformed (the sync adapters' rule). */
internal fun parseSlashKey(key: String): Pair<String, String>? {
    val first = key.substringBefore('/')
    val second = key.substringAfter('/')
    if (first.isEmpty() || second.isEmpty() || first == key) return null
    return first to second
}

/** Lenient payload decode: a malformed value skips the entry (never throws). */
internal fun <T> JsonElement.toPayloadOrNull(serializer: KSerializer<T>): T? = try {
    SliceJson.decodeFromJsonElement(serializer, this)
} catch (_: IllegalArgumentException) {
    null // not a JSON object / wrong shapes — skip
} catch (_: SerializationException) {
    null
}

/**
 * The three integration stores' fan-out, shared by
 * [JellyPlayIntegrationsSyncAdapter] and [IntegrationsBackupSliceSource] (the
 * sync namespace and the backup slice read/apply the SAME allowlisted key
 * space through the SAME stores' sync-only surfaces).
 */
internal class IntegrationsStoreFan(
    val seerrPreferencesStore: SeerrPreferencesStore,
    val arrPreferencesStore: ArrPreferencesStore,
    val subtitleProviderPreferencesStore: SubtitleProviderPreferencesStore,
) {

    /** The stores' raw stored values → wire primitives, absent keys dropped. */
    suspend fun snapshot(): Map<String, JsonElement> = buildMap {
        // The three raw key spaces are disjoint by prefix, so one flat map is
        // unambiguous. Absent key = the store's default governs; nothing to
        // push (the prefs-adapter stance: only stored values roam).
        for (raw in listOf(
            seerrPreferencesStore.syncSnapshot(),
            arrPreferencesStore.syncSnapshot(),
            subtitleProviderPreferencesStore.syncSnapshot(),
        )) {
            for ((name, value) in raw) if (value != null) put(name, encodeRaw(value))
        }
    }

    /** Routes one apply into all three stores (each allowlist applies only its own keys). */
    suspend fun apply(name: String, raw: String?) {
        seerrPreferencesStore.syncApply(name, raw)
        arrPreferencesStore.syncApply(name, raw)
        subtitleProviderPreferencesStore.syncApply(name, raw)
    }
}

/**
 * Raw string → wire primitive, kind inferred from a strict parse (the raw
 * string is what round-trips; the receiving store re-kinds by its allowlist,
 * so the JSON kind is presentation only).
 */
internal fun encodeRaw(raw: String): JsonElement = when (raw) {
    "true" -> JsonPrimitive(true)
    "false" -> JsonPrimitive(false)
    else -> raw.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)
}

internal fun ItemPlaybackPreferenceEntity.backupKey(): String = "$scope/$key"

internal fun ItemPlaybackPreferenceEntity.toPayload(): ItemPlaybackPreferencePayload = ItemPlaybackPreferencePayload(
    scope = scope,
    key = key,
    audioLanguage = audioLanguage,
    subtitleLanguage = subtitleLanguage,
    subtitleDisabled = subtitleDisabled,
    subtitleForced = subtitleForced,
    subtitleHearingImpaired = subtitleHearingImpaired,
    dialogueBoostStrength = dialogueBoostStrength,
    rememberedAudioLabel = rememberedAudioLabel,
    rememberedAudioLanguage = rememberedAudioLanguage,
    rememberedAudioIndex = rememberedAudioIndex,
    rememberedAudioCodec = rememberedAudioCodec,
    rememberedSubtitleLabel = rememberedSubtitleLabel,
    rememberedSubtitleLanguage = rememberedSubtitleLanguage,
    rememberedSubtitleIndex = rememberedSubtitleIndex,
    rememberedSubtitleCodec = rememberedSubtitleCodec,
    renderProfile = renderProfile,
    preferredMediaSourceId = preferredMediaSourceId,
    updatedAt = updatedAt,
)

internal fun entityFrom(payload: ItemPlaybackPreferencePayload, scope: String, key: String) =
    ItemPlaybackPreferenceEntity(
        scope = scope,
        key = key,
        audioLanguage = payload.audioLanguage,
        subtitleLanguage = payload.subtitleLanguage,
        // The repository's local-write invariant (pinning a subtitle language
        // clears the disabled intent — see ItemPlaybackPreferenceRepositoryImpl.save)
        // re-imposed here, so BOTH remote-adoption paths (the sync adapter and
        // the backup slice) land rows exactly as a local write could have
        // produced them.
        subtitleDisabled = payload.subtitleDisabled.takeIf { payload.subtitleLanguage == null },
        subtitleForced = payload.subtitleForced,
        subtitleHearingImpaired = payload.subtitleHearingImpaired,
        dialogueBoostStrength = payload.dialogueBoostStrength,
        rememberedAudioLabel = payload.rememberedAudioLabel,
        rememberedAudioLanguage = payload.rememberedAudioLanguage,
        rememberedAudioIndex = payload.rememberedAudioIndex,
        rememberedAudioCodec = payload.rememberedAudioCodec,
        rememberedSubtitleLabel = payload.rememberedSubtitleLabel,
        rememberedSubtitleLanguage = payload.rememberedSubtitleLanguage,
        rememberedSubtitleIndex = payload.rememberedSubtitleIndex,
        rememberedSubtitleCodec = payload.rememberedSubtitleCodec,
        renderProfile = payload.renderProfile,
        preferredMediaSourceId = payload.preferredMediaSourceId,
        updatedAt = payload.updatedAt,
    )

internal fun SmartPlaylistEntity.toPayload(): SmartPlaylistPayload = SmartPlaylistPayload(
    id = id,
    name = name,
    criteriaJson = criteriaJson,
    maxItems = maxItems,
    sortBy = sortBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun entityFrom(payload: SmartPlaylistPayload) = SmartPlaylistEntity(
    id = payload.id,
    name = payload.name,
    criteriaJson = payload.criteriaJson,
    maxItems = payload.maxItems,
    sortBy = payload.sortBy,
    createdAt = payload.createdAt,
    updatedAt = payload.updatedAt,
)

internal fun MoodPlaylistEntity.toPayload(): MoodPlaylistPayload = MoodPlaylistPayload(
    id = id,
    name = name,
    emoji = emoji,
    description = description,
    genreKeywordsJson = genreKeywordsJson,
    excludedGenresJson = excludedGenresJson,
    minRating = minRating,
    sortBy = sortBy,
    maxItems = maxItems,
    themeColorHex = themeColorHex,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun entityFrom(payload: MoodPlaylistPayload) = MoodPlaylistEntity(
    id = payload.id,
    name = payload.name,
    emoji = payload.emoji,
    description = payload.description,
    genreKeywordsJson = payload.genreKeywordsJson,
    excludedGenresJson = payload.excludedGenresJson,
    minRating = payload.minRating,
    sortBy = payload.sortBy,
    maxItems = payload.maxItems,
    themeColorHex = payload.themeColorHex,
    createdAt = payload.createdAt,
    updatedAt = payload.updatedAt,
)

internal fun MoodPlaylistPreferenceEntity.toPayload(): MoodPlaylistPreferencePayload =
    MoodPlaylistPreferencePayload(
        playlistId = playlistId,
        isEnabled = isEnabled,
        isFavorite = isFavorite,
        lastPlayedAt = lastPlayedAt,
        updatedAt = updatedAt,
    )

internal fun entityFrom(payload: MoodPlaylistPreferencePayload) = MoodPlaylistPreferenceEntity(
    playlistId = payload.playlistId,
    isEnabled = payload.isEnabled,
    isFavorite = payload.isFavorite,
    lastPlayedAt = payload.lastPlayedAt,
    updatedAt = payload.updatedAt,
)
