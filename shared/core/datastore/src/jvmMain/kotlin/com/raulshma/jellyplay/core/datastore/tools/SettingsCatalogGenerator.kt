package com.raulshma.jellyplay.core.datastore.tools

import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceStorage
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSyncPolicy
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Generates the JellyPlay client settings catalog — the artifact the Jellyfin
 * plugin embeds (`jellyfin-plugin-jellyplay/src/Jellyfin.Plugin.JellyPlay/
 * Resources/jellyplay-settings-catalog.json`) and serves to the dashboard
 * (GET jellyplay/settings/catalog) so admins pick real keys with typed value
 * editors instead of hand-typing `ns/key` strings and raw values.
 *
 * The catalog is DERIVED, never hand-maintained: it walks the same
 * `PreferenceSpec` declarations the stores persist through, so a key's wire
 * name, type, enum vocabulary and default can never drift from what the
 * client actually reads and writes. Regenerate with
 * `./gradlew :shared:core:datastore:generateSettingsCatalog` after changing
 * any spec row; `checkSettingsCatalog` (wired into `check`) fails when the
 * committed artifact is stale.
 *
 * Coverage policy (the catalog is an ALLOWLIST, not an enumeration of
 * `user_prefs`):
 *  - spec rows of the six spec-backed domains, minus the prefixes the sync
 *    adapter excludes at its registration (`dream`/`screensaver` — the
 *    catalog must describe exactly what syncs);
 *  - the [SUPPLEMENTAL] rows below — valuable hand-written store keys that
 *    predate the spec machinery; each carries a "migrate to specs" note;
 *  - everything else in `user_prefs` (the ~20 stores with hand-written `Keys`
 *    objects) stays OUT until those stores migrate to specs, and secrets or
 *    device identity can never enter regardless (the [DENYLIST] is a hard
 *    error, not a silent skip, so a future spec migration of the security or
 *    identity stores cannot accidentally advertise their keys).
 */
object SettingsCatalogGenerator {

    /** Sync namespace of the user_prefs adapter — every catalog entry's ns. */
    const val NAMESPACE = "prefs"

    /** Wire schema of the artifact; bump on breaking catalog-shape changes. */
    const val CATALOG_SCHEMA = 1

    /**
     * Per-device namespaces the sync adapter excludes at its registration —
     * one shared constant (see [PreferenceSyncPolicy]) so the catalog keeps
     * describing exactly what syncs.
     */
    val SYNC_EXCLUDED_PREFIXES = PreferenceSyncPolicy.EXCLUDED_PREFIXES

    /**
     * Keys that must never be offered as admin defaults, even if their stores
     * migrate to specs later: derived from the same `SyncExcludedKeys` sets
     * the adapter registration excludes (secrets, device/session identity,
     * per-device lock state), so the denylist cannot drift from what sync
     * actually excludes.
     */
    val DENYLIST: Set<String> = SecurityStore.SyncExcludedKeys +
        PinRateLimiter.SyncExcludedKeys +
        ServerIdentityStore.SyncExcludedKeys

    /** One catalog entry as plain data, before JSON rendering. */
    data class Entry(
        val ns: String,
        val key: String,
        val label: String,
        val description: String,
        val valueType: String,
        val defaultValue: JsonPrimitive?,
        val options: List<String>?,
    ) {
        val id: String get() = "$ns/$key"
    }

    /**
     * Hand-written store keys worth advertising before their stores migrate
     * to the spec machinery. Key names reference the store's `Keys` object so
     * a rename cannot silently orphan the row (migration to specs removes the
     * hand-written row entirely).
     */
    private data class Supplemental(
        val key: String,
        val valueType: String,
        val defaultValue: JsonPrimitive?,
        val description: String,
    )

    // VolumeProfileStore (shared/core/datastore .../volume/VolumeProfileStore.kt).
    private val SUPPLEMENTAL = listOf(
        Supplemental(
            key = VolumeProfileStore.Keys.REMEMBER_VOLUME_PER_CONTENT_TYPE.name,
            valueType = "boolean",
            defaultValue = JsonPrimitive(true),
            description = "Remember playback volume per content type (movie, episode, …).",
        ),
        Supplemental(
            key = VolumeProfileStore.Keys.VOLUME_PROFILES.name,
            valueType = "string",
            defaultValue = null,
            description = "Remembered volume levels as a JSON map of content-type bucket to level (0..1).",
        ),
    )

    /** The spec-backed domains, in catalog (namespace, then domain) order. */
    private val SPEC_DOMAINS: List<Pair<String, List<PreferenceSpec<*>>>> = listOf(
        "Appearance" to AppearancePreferenceSpecs.all,
        "Playback" to PlaybackPreferenceSpecs.all,
        "Home & discovery" to HomeDiscoveryPreferenceSpecs.all,
        "Video player" to VideoPlayerPreferenceSpecs.all,
        "Screensaver" to ScreensaverPreferenceSpecs.all,
        "Experimental" to ExperimentalPreferenceSpecs.all,
    )

    /**
     * All catalog entries, in artifact order (spec domains, then supplemental
     * rows). Throws on denylisted or duplicate keys — a broken policy must
     * fail the generation, not ship a wrong catalog.
     */
    fun entries(): List<Entry> {
        val result = mutableListOf<Entry>()
        val seen = mutableSetOf<String>()

        fun add(entry: Entry) {
            check(entry.key !in DENYLIST) {
                "settings catalog policy violation: '${entry.id}' is denylisted but reached the catalog"
            }
            // Multiple spec rows may legitimately share one storage key (the
            // experimental set-membership toggles all ride
            // enabled_experimental_features) — one catalog entry per wire key.
            if (!seen.add(entry.id)) {
                return
            }
            result += entry
        }

        SPEC_DOMAINS.forEach { (_, rows) ->
            rows.forEach { spec ->
                if (SYNC_EXCLUDED_PREFIXES.none { spec.keyName.startsWith(it) }) {
                    spec.toEntry()?.let(::add)
                }
            }
        }
        SUPPLEMENTAL.forEach {
            add(
                Entry(
                    ns = NAMESPACE,
                    key = it.key,
                    label = humanize(it.key),
                    description = it.description,
                    valueType = it.valueType,
                    defaultValue = it.defaultValue,
                    options = null,
                ),
            )
        }
        return result
    }

    /**
     * Maps one spec row to a catalog entry. String-storage rows without enum
     * options (raw strings, JSON blobs, codec-encoded values) are catalogued
     * as plain "string" — the sync wire value for every string slot IS a JSON
     * string; defaults are only carried when they are wire-representable
     * (a codec-encoded default like a Set cannot be rendered without its
     * store codec, so it is omitted rather than approximated).
     */
    private fun PreferenceSpec<*>.toEntry(): Entry {
        val type: String
        var options: List<String>? = null
        var defaultValue: JsonPrimitive? = null

        // Nullable declarations (absent-slot semantics) carry no catalog
        // default; a non-null default that contradicts the declared storage
        // is a broken row and must fail the generation.
        fun wireDefault(storageKind: String, render: (Any) -> JsonPrimitive): JsonPrimitive? {
            val declared = this@toEntry.default ?: return null
            check(declared::class.simpleName == storageKind) {
                "settings catalog: spec '$keyName' declares $storage storage but a " +
                    "${declared::class.simpleName} default — the row's default cannot be rendered"
            }
            return render(declared)
        }

        when (storage) {
            PreferenceStorage.BOOLEAN -> {
                type = "boolean"
                defaultValue = wireDefault("Boolean") { JsonPrimitive(it as Boolean) }
            }
            PreferenceStorage.INT -> {
                type = "number"
                defaultValue = wireDefault("Int") { JsonPrimitive(it as Int) }
            }
            PreferenceStorage.LONG -> {
                type = "number"
                defaultValue = wireDefault("Long") { JsonPrimitive((it as Long).toDouble()) }
            }
            PreferenceStorage.FLOAT -> {
                type = "number"
                defaultValue = wireDefault("Float") { JsonPrimitive((it as Float).toDouble()) }
            }
            PreferenceStorage.STRING -> {
                val enumNames = enumOptions
                if (enumNames != null) {
                    type = "enum"
                    options = enumNames
                    defaultValue = (default as? Enum<*>)?.let { JsonPrimitive(it.name) }
                } else {
                    type = "string"
                    defaultValue = (default as? String)?.let(::JsonPrimitive)
                }
            }
        }
        return Entry(
            ns = NAMESPACE,
            key = keyName,
            label = humanize(keyName),
            description = "",
            valueType = type,
            defaultValue = defaultValue,
            options = options,
        )
    }

    private fun humanize(keyName: String): String =
        keyName.split('_').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }

    /** The deterministic artifact text (spec declaration order, fixed shape). */
    fun render(): String {
        val artifact = buildJsonObject {
            put("catalogSchema", CATALOG_SCHEMA)
            put(
                "settings",
                buildJsonArray {
                    entries().forEach { entry ->
                        add(
                            buildJsonObject {
                                put("ns", entry.ns)
                                put("key", entry.key)
                                put("label", entry.label)
                                put("description", entry.description)
                                put("valueType", entry.valueType)
                                entry.defaultValue?.let { put("defaultValue", it) }
                                entry.options?.let { names ->
                                    put("options", buildJsonArray { names.forEach { add(JsonPrimitive(it)) } })
                                }
                            },
                        )
                    }
                },
            )
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), artifact) + "\n"
    }
}

fun main(args: Array<String>) {
    val checkMode = args.firstOrNull() == "--check"
    val path = args.lastOrNull { it != "--check" }
        ?: error("usage: SettingsCatalogGenerator [--check] <artifact-path>")
    val file = File(path)

    if (checkMode) {
        // The plugin tree (and its embedded artifact) is committed in this
        // repo, so an absent artifact is a broken checkout, not a skip —
        // silently passing here would let a deleted artifact through CI.
        if (!file.isFile) {
            error(
                "settings catalog artifact missing at $path — run " +
                    "./gradlew :shared:core:datastore:generateSettingsCatalog and commit the artifact",
            )
        }
        val committed = file.readText().replace("\r\n", "\n")
        val generated = SettingsCatalogGenerator.render()
        if (committed != generated) {
            error(
                "settings catalog is stale: $path does not match the PreferenceSpec declarations. " +
                    "Run ./gradlew :shared:core:datastore:generateSettingsCatalog and commit the artifact.",
            )
        }
        println("settings catalog: ${SettingsCatalogGenerator.entries().size} entries up to date")
        return
    }

    file.parentFile?.mkdirs()
    file.writeText(SettingsCatalogGenerator.render())
    println("settings catalog: wrote ${SettingsCatalogGenerator.entries().size} entries to $path")
}
