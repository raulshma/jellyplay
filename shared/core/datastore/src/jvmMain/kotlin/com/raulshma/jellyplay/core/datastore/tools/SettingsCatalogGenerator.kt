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
import javax.xml.parsers.DocumentBuilderFactory

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
        val optionLabels: List<String>?,
        val group: String,
        val min: Double?,
        val max: Double?,
    ) {
        val id: String get() = "$ns/$key"
    }

    /** Catalog group of the hand-written [SUPPLEMENTAL] rows. */
    const val SUPPLEMENTAL_GROUP = "General"

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
     * rows). [strings] is the settings-search resource table (name → text)
     * the labels and descriptions resolve from. Throws on denylisted or
     * duplicate keys — a broken policy must fail the generation, not ship a
     * wrong catalog.
     */
    fun entries(strings: Map<String, String>): List<Entry> {
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

        SPEC_DOMAINS.forEach { (domain, rows) ->
            rows.forEach { spec ->
                if (SYNC_EXCLUDED_PREFIXES.none { spec.keyName.startsWith(it) }) {
                    spec.toEntry(domain, strings)?.let(::add)
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
                    optionLabels = null,
                    group = SUPPLEMENTAL_GROUP,
                    min = null,
                    max = null,
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
     *
     * The human-facing label and description resolve from the settings-search
     * resource table ([strings], name → text): the row's own
     * [PreferenceSearchSpec.titleKey]/[PreferenceSearchSpec.subtitleKey] are
     * the same strings the client's in-app settings search shows, so the
     * catalog cannot drift from what users see in the app. Rows without
     * search metadata (and keys missing from the table) fall back to the
     * mechanical [humanize] label and an empty description.
     */
    private fun PreferenceSpec<*>.toEntry(group: String, strings: Map<String, String>): Entry {
        val type: String
        var options: List<String>? = null
        var defaultValue: JsonPrimitive? = null

        // Nullable declarations (absent-slot semantics) carry no catalog
        // default; a non-null default that contradicts the declared storage
        // is a broken row and must fail the generation.
        fun wireDefault(storageKind: String, render: (Any) -> JsonPrimitive): JsonPrimitive? {
            val declared = this@toEntry.default ?: return null
            check(declared::class.simpleName == storageKind) {
                "settings catalog: spec '$keyName' declares $storageKind storage but a " +
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
        val label = search?.titleKey?.let { strings[it] }
        val description = search?.subtitleKey?.let { strings[it] }
        if (search != null) {
            // A miss degrades to the mechanical label / no description —
            // legal (the fallbacks keep the artifact valid) but a resources
            // regression the committer should see, never a silent one.
            if (strings[search.titleKey] == null) {
                System.err.println("settings catalog: title key '${search.titleKey}' (spec $keyName) missing from the strings tables — label degraded")
            }
            if (strings[search.subtitleKey] == null) {
                System.err.println("settings catalog: subtitle key '${search.subtitleKey}' (spec $keyName) missing from the strings tables — description degraded")
            }
        }
        return Entry(
            ns = NAMESPACE,
            key = keyName,
            label = label ?: humanize(keyName),
            description = description ?: "",
            valueType = type,
            defaultValue = defaultValue,
            options = options,
            optionLabels = options?.map(::humanizeEnum),
            group = group,
            min = min,
            max = max,
        )
    }

    private fun humanize(keyName: String): String =
        keyName.split('_').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }

    /** "TONAL_SPOT" → "Tonal Spot" — SCREAMING_SNAKE enum names for display. */
    private fun humanizeEnum(name: String): String =
        name.split('_').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercaseChar() }
        }

    /**
     * Parses one settings-search resource file into a name → text map. A real
     * XML parser, not regex, so entities (`&amp;`, `&apos;`, …) resolve
     * exactly as the in-app strings do. A missing file is a broken checkout
     * and fails the generation — the artifact must never ship with
     * silently-degraded labels.
     */
    internal fun loadSearchStrings(path: String): Map<String, String> {
        val file = File(path)
        check(file.isFile) {
            "settings catalog: search strings file missing at $path — pass the client's resource tables via --strings"
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val strings = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            strings[name] = node.textContent.trim()
        }
        return strings
    }

    /** The deterministic artifact text (spec declaration order, fixed shape). */
    fun render(strings: Map<String, String>): String = renderEntries(entries(strings))

    /** The deterministic artifact text for pre-built entries (check mode reuses its walk). */
    internal fun renderEntries(entries: List<Entry>): String {
        val artifact = buildJsonObject {
            put("catalogSchema", CATALOG_SCHEMA)
            put(
                "settings",
                buildJsonArray {
                    entries.forEach { entry ->
                        add(
                            buildJsonObject {
                                put("ns", entry.ns)
                                put("key", entry.key)
                                put("label", entry.label)
                                put("description", entry.description)
                                put("group", entry.group)
                                put("valueType", entry.valueType)
                                entry.defaultValue?.let { put("defaultValue", it) }
                                entry.min?.let { put("min", it) }
                                entry.max?.let { put("max", it) }
                                entry.options?.let { names ->
                                    put("options", buildJsonArray { names.forEach { add(JsonPrimitive(it)) } })
                                }
                                entry.optionLabels?.let { labels ->
                                    put("optionLabels", buildJsonArray { labels.forEach { add(JsonPrimitive(it)) } })
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
    val usage = "usage: SettingsCatalogGenerator [--check] [--strings <resource.xml>]... <artifact-path>"
    val checkMode = args.contains("--check")
    val stringsPaths = mutableListOf<String>()
    var artifactPath: String? = null
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--check" -> Unit
            "--strings" -> {
                i++
                stringsPaths += args.getOrNull(i) ?: error(usage)
            }
            else -> {
                // Guard the ambiguity that would make a table path silently
                // (or worse, destructively) pass for the artifact.
                check(artifactPath == null) { "multiple artifact paths given: $artifactPath, $arg — $usage" }
                artifactPath = arg
            }
        }
        i++
    }
    val path = artifactPath ?: error(usage)
    check(stringsPaths.isNotEmpty()) { usage }
    // Later files win on name collisions (the client's resource merge allows
    // duplicates across files); the search table leads so its ss_* keys are
    // the baseline.
    val strings = stringsPaths.fold(mutableMapOf<String, String>()) { acc, file ->
        acc.putAll(SettingsCatalogGenerator.loadSearchStrings(file))
        acc
    }

    if (checkMode) {
        // The plugin tree (and its embedded artifact) is committed in this
        // repo, so an absent artifact is a broken checkout, not a skip —
        // silently passing here would let a deleted artifact through CI.
        if (!File(path).isFile) {
            error(
                "settings catalog artifact missing at $path — run " +
                    "./gradlew :shared:core:datastore:generateSettingsCatalog and commit the artifact",
            )
        }
        val entries = SettingsCatalogGenerator.entries(strings)
        val committed = File(path).readText().replace("\r\n", "\n")
        val generated = SettingsCatalogGenerator.renderEntries(entries)
        if (committed != generated) {
            error(
                "settings catalog is stale: $path does not match the PreferenceSpec declarations. " +
                    "Run ./gradlew :shared:core:datastore:generateSettingsCatalog and commit the artifact.",
            )
        }
        println("settings catalog: ${entries.size} entries up to date")
        return
    }

    val entries = SettingsCatalogGenerator.entries(strings)
    File(path).parentFile?.mkdirs()
    File(path).writeText(SettingsCatalogGenerator.renderEntries(entries))
    println("settings catalog: wrote ${entries.size} entries to $path")
}
