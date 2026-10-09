package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeLayoutPreset
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The [ProfileSyncAdapter] that roams the home LAYOUT (ADR 0011's state
 * surfaces): namespace [NAMESPACE], one key per layout domain — the section
 * enabled-set / order / per-library (hidden) overrides / pinned sections /
 * custom Discover rows / layout presets — keyed by the
 * [HomeDiscoveryStore]'s canonical wire names (`home_enabled_section_types`,
 * `home_section_order`, `home_library_section_overrides`,
 * `pinned_home_sections`, `home_discover_rows`, `home_layout_presets`) so the
 * wire names stay the store's declared-once names. Values are the same
 * structured JSON the store persists, opaque to the server.
 *
 * The rest of the home domain deliberately does NOT roam here: the behavioral
 * knobs (badges, hide-watched, Next-Up window, click behavior…) are ordinary
 * `prefs`-namespace settings, the hidden-CW set is the `cw` adapter's state,
 * and the server-driven row CONTENT was never local — only the local layout
 * roams. The plugin's device-profile overlay (base/desktop/phone/tv) gives
 * per-form-factor layouts for free: each profile resolves its own view of
 * these keys, nothing to build.
 *
 * The store's read projection is the user-namespaced
 * [HomeDiscoveryStore.homeDiscovery] StateFlow, so the synced layout follows
 * the active user; adoption goes through the store's OWN setters (the section
 * algebra — normalization, version stamping — is theirs, never duplicated
 * here). The mirror — the last-synced snapshot dirty detection diffs against
 * — rides the user-prefs DataStore under the reserved
 * `jpsync.mirror.homelayout.` prefix (the shared [SyncMirror] idiom; the
 * `jpsync.` reservation keeps every mirror key out of every adapter's synced
 * set — these keys left the `prefs` adapter's raw snapshot when the
 * per-user-namespaced keys moved behind this namespace's exclusions).
 *
 * DELETES (a key reset to its default) do NOT roam: like prefs, a cleared
 * layout domain means "back to the default layout" on this device, never
 * "delete everywhere" — [deletedKeys] stays the SPI default (the mirror
 * entries are dropped by [markSynced] of the empty state… they are simply
 * never reported as tombstones; the store has no per-domain remove that is
 * not a value write).
 *
 * NOTE: the read projection is a StateFlow snapshot, so a setter lands in
 * [snapshot] one dispatch after the DataStore edit — the background flush
 * triggers (app-background / reconnect / periodic) all read long after the
 * write settles; nothing here needs write-adjacent freshness.
 */
class JellyPlayHomeLayoutSyncAdapter(
    private val homeDiscoveryStore: HomeDiscoveryStore,
    /** The mirror's persistence home — the SAME user-prefs store the other adapters mirror, under a reserved prefix. */
    private val mirrorStore: DataStore<Preferences>,
    override val namespace: String = NAMESPACE,
) : ProfileSyncAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    private val mirror = SyncMirror(mirrorStore, JpsyncReservation.mirrorPrefix(NAMESPACE))

    /** One layout domain: the canonical key, its slice reader, and its store-setter adoption. */
    private abstract class Domain<T>(
        val key: String,
    ) {
        abstract fun read(slice: HomeDiscoverySlice): T
        abstract fun encode(value: T): JsonElement
        abstract suspend fun adopt(store: HomeDiscoveryStore, element: JsonElement): Boolean

        /**
         * Encodes the domain's current slice value — the read+encode pair
         * lives INSIDE the class so the star-projected [Domain] list never
         * has to call the generic members from outside.
         */
        fun encodeCurrent(slice: HomeDiscoverySlice): JsonElement = encode(read(slice))
    }

    private val domains: List<Domain<*>> = listOf(
        object : Domain<Set<HomeSectionType>>(KEY_ENABLED_SECTION_TYPES) {
            override fun read(slice: HomeDiscoverySlice) =
                slice.enabledHomeSectionTypes

            override fun encode(value: Set<HomeSectionType>) =
                toJsonElement(value.map { it.name }.toSet())

            override suspend fun adopt(store: HomeDiscoveryStore, element: JsonElement): Boolean {
                val decoded = decodeOrNull<Set<String>>(element) ?: return false
                val types = decoded.mapNotNull { name -> HomeSectionType.entries.find { it.name == name } }.toSet()
                store.setEnabledHomeSectionTypes(types)
                return true
            }
        },
        object : Domain<List<HomeSectionType>>(KEY_SECTION_ORDER) {
            override fun read(slice: HomeDiscoverySlice) =
                slice.homeSectionOrder

            override fun encode(value: List<HomeSectionType>) =
                toJsonElement(value.map { it.name })

            override suspend fun adopt(store: HomeDiscoveryStore, element: JsonElement): Boolean {
                val decoded = decodeOrNull<List<String>>(element) ?: return false
                val order = decoded.mapNotNull { name -> HomeSectionType.entries.find { it.name == name } }
                store.setHomeSectionOrder(order)
                return true
            }
        },
        object : Domain<Map<String, Set<HomeSectionType>>>(KEY_LIBRARY_SECTION_OVERRIDES) {
            override fun read(slice: HomeDiscoverySlice) =
                slice.libraryHomeSectionOverrides

            override fun encode(value: Map<String, Set<HomeSectionType>>) =
                toJsonElement(value.mapValues { (_, types) -> types.map { it.name } })

            override suspend fun adopt(store: HomeDiscoveryStore, element: JsonElement): Boolean {
                val raw = decodeOrNull<Map<String, Set<String>>>(element) ?: return false
                val overrides = raw.mapValues { (_, names) ->
                    names.mapNotNull { name -> HomeSectionType.entries.find { it.name == name } }.toSet()
                }
                store.setLibraryHomeSectionOverrides(overrides)
                return true
            }
        },
        object : Domain<List<PinnedHomeSection>>(KEY_PINNED_SECTIONS) {
            override fun read(slice: HomeDiscoverySlice) =
                slice.pinnedHomeSections

            override fun encode(value: List<PinnedHomeSection>) = toJsonElement(value)

            override suspend fun adopt(store: HomeDiscoveryStore, element: JsonElement): Boolean {
                val decoded = decodeOrNull<List<PinnedHomeSection>>(element) ?: return false
                store.setPinnedHomeSections(decoded)
                return true
            }
        },
        object : Domain<List<DiscoverRowConfig>>(KEY_DISCOVER_ROWS) {
            override fun read(slice: HomeDiscoverySlice) =
                slice.discoverRows

            override fun encode(value: List<DiscoverRowConfig>) = toJsonElement(value)

            override suspend fun adopt(store: HomeDiscoveryStore, element: JsonElement): Boolean {
                val decoded = decodeOrNull<List<DiscoverRowConfig>>(element) ?: return false
                store.setDiscoverRows(decoded)
                return true
            }
        },
        object : Domain<List<HomeLayoutPreset>>(KEY_LAYOUT_PRESETS) {
            override fun read(slice: HomeDiscoverySlice) =
                slice.homeLayoutPresets

            override fun encode(value: List<HomeLayoutPreset>) = toJsonElement(value)

            override suspend fun adopt(store: HomeDiscoveryStore, element: JsonElement): Boolean {
                val decoded = decodeOrNull<List<HomeLayoutPreset>>(element) ?: return false
                store.setHomeLayoutPresets(decoded)
                return true
            }
        },
    )

    private val domainsByKey = domains.associateBy { it.key }

    private val slice: HomeDiscoverySlice
        get() = homeDiscoveryStore.homeDiscovery.value

    override suspend fun snapshot(): Map<String, JsonElement> =
        domains.associate { domain -> domain.key to domain.encodeCurrent(slice) }

    override suspend fun dirtyValues(current: Map<String, JsonElement>): Map<String, JsonElement> =
        mirror.dirtyValues(current)

    override suspend fun applyRemote(entries: Map<String, JsonElement>) {
        for ((key, value) in entries) {
            val domain = domainsByKey[key] ?: continue // an unknown key never touches the store
            if (value is JsonNull) continue // defensive: tombstones never roam for this namespace
            domain.adopt(homeDiscoveryStore, value)
            // A false return (malformed value) skips the key — the local
            // layout survives a garbage row (the prefs-adapter rule).
        }
    }

    override suspend fun markSynced(values: Map<String, JsonElement>) = mirror.markSynced(values)

    // ------------------------------------------------------------------
    // Encoding helpers: the wire value is the same structured JSON the
    // store persists (encoded through this adapter's lenient Json), decoded
    // tolerantly — any shape drift or garbage decodes to null and the key
    // is skipped.
    // ------------------------------------------------------------------

    private inline fun <reified T> toJsonElement(value: T): JsonElement =
        Json.parseToJsonElement(json.encodeToString(value))

    private inline fun <reified T> decodeOrNull(element: JsonElement): T? = try {
        json.decodeFromJsonElement<T>(element)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: kotlinx.serialization.SerializationException) {
        null
    }

    private companion object {
        const val NAMESPACE = "homelayout"

        // The canonical wire names — the store's own spec-declared key names
        // (the `u_<userId>::` layer stays device-local; these are the names
        // the wire sees).
        const val KEY_ENABLED_SECTION_TYPES = "home_enabled_section_types"
        const val KEY_SECTION_ORDER = "home_section_order"
        const val KEY_LIBRARY_SECTION_OVERRIDES = "home_library_section_overrides"
        const val KEY_PINNED_SECTIONS = "pinned_home_sections"
        const val KEY_DISCOVER_ROWS = "home_discover_rows"
        const val KEY_LAYOUT_PRESETS = "home_layout_presets"
    }
}
