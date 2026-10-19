@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.HomeLayoutConfig
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The slice→payload mapping of the home-layout sync — the SAME projection
 * the presets machinery snapshots ([HomeLayoutConfig] is
 * `HomeLayoutPreset.config`'s type): what the share button exports, what the
 * autopush uploads, what a restore applies, all one vocabulary. A second
 * hand-copied mapping here is how the two paths would drift.
 */
fun HomeDiscoverySlice.toHomeLayoutConfig(): HomeLayoutConfig = HomeLayoutConfig(
    enabledHomeSectionTypes = enabledHomeSectionTypes,
    homeSectionOrder = homeSectionOrder,
    libraryHomeSectionOverrides = libraryHomeSectionOverrides,
    mergeContinueWatchingAndNextUp = mergeContinueWatchingAndNextUp,
    nextUpMaxDays = nextUpMaxDays,
    nextUpRewatching = nextUpRewatching,
    pinnedHomeSections = pinnedHomeSections,
    discoverRows = discoverRows,
    homeHeroEnabled = homeHeroEnabled,
    continueWatchingClickBehavior = continueWatchingClickBehavior,
)

/**
 * The outcome of fetching the server's saved home layout for the manual
 * restore action. Three cleanly-distinguishable states so the caller can
 * message each: nothing has ever been uploaded ([Empty]), the stored blob
 * failed the lenient decode ([Undecodable] — restore aborts cleanly), or a
 * decodable layout is on offer ([Offer], carrying the write-time stamp the
 * timestamp-diff confirmation dialog surfaces).
 */
sealed interface HomeLayoutRestoreFetch {
    /** A decodable server copy: apply candidate + the metadata the dialog shows. */
    data class Offer(
        val config: HomeLayoutConfig,
        /** The writing client's epoch-millis stamp (client clock — the DisplayPreferences transport carries no server timestamp). */
        val savedAtEpochMillis: Long?,
        val schemaVersion: Int?,
    ) : HomeLayoutRestoreFetch

    /** No layout has ever been stored for this user ("upload it first"). */
    data object Empty : HomeLayoutRestoreFetch

    /** A blob is stored but does not decode (corrupt / alien shape) — restore aborts, local layout untouched. */
    data object Undecodable : HomeLayoutRestoreFetch
}

/**
 * The home-layout ↔ server DisplayPreferences bridge — the layer that owns
 * the network client (the store layer deliberately cannot: [HomeDiscoveryStore]
 * stays transport-free and bumps its observable [HomeDiscoveryStore.layoutRevision]
 * instead; THIS class collects that signal). Local-first policy, per decision:
 *
 *  - **Autopush (optional, `home_layout_autopush`, default off):** collect the
 *    store's layout-revision signal, armed by the pref + an active sign-in;
 *    every layout-writing store command bumps the signal, and a 2-second
 *    trailing debounce collapses bursts (a preset apply fans out ten bumps →
 *    one push) before the CURRENT layout is serialized through the preset
 *    codec and pushed. Failure is silent — the next layout change re-arms the
 *    debounce (no timers, no retry loops; local-first means the server copy is
 *    never load-bearing).
 *  - **Manual actions:** [uploadNow] / [fetchServerLayout] work regardless of
 *    the autopush pref, serialize/decode through the exact same codec, so a
 *    manual upload and an autopushed upload are byte-identical.
 *
 * There is deliberately NO auto-pull: a silent overwrite is precisely what the
 * local-first decision avoids; the only adopt path is the explicit restore
 * action's confirmation dialog (the caller applies through
 * [HomeDiscoveryStore.applyLayout] — the preset-restore path's own command).
 *
 * The serialize side uses [PreferencesJson.export] (the preset share path's
 * encoder); the decode side is LENIENT with the version-union decode's
 * contract (unknown HomeSectionType / PinnedSectionType names dropped, so a
 * newer client's layout restores on an older one) before the strict
 * [PreferencesJson.import] decode — anything that still fails aborts the
 * restore to [HomeLayoutRestoreFetch.Undecodable], never a partial apply.
 */
class HomeLayoutSyncController(
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val identityStore: ServerIdentityStore,
    private val apiClient: LibraryApiClient,
    /** The collector's home — the application scope; virtual-time tests pass a TestScope and construct with [startAutopushOnConstruction] = false. */
    private val externalScope: CoroutineScope,
    private val startAutopushOnConstruction: Boolean = true,
    internal val autopushDebounceMillis: Long = AUTO_PUSH_DEBOUNCE_MILLIS,
) {

    init {
        if (startAutopushOnConstruction) {
            externalScope.launch { runAutopush() }
        }
    }

    // ------------------------------------------------------------------
    // Manual actions — the Home Settings Layout group's Upload / Restore
    // rows (work regardless of autopush; the same codec as the autopush).
    // ------------------------------------------------------------------

    /**
     * Serializes the CURRENT layout through the preset codec and pushes it to
     * the server's DisplayPreferences store (stamping the payload schema
     * version; the transport adds the write-time savedAt). Signed-in only —
     * signed out (or network failure) is a returned [Result.failure], never a
     * throw.
     */
    suspend fun uploadNow(): Result<Unit> {
        val userId = currentUserId()
            ?: return Result.failure(IllegalStateException("Home layout sync requires an active user"))
        return apiClient.writeHomeLayoutDisplayPreferences(
            userId = userId,
            json = serializeLayout(homeDiscoveryStore.homeDiscovery.value.toHomeLayoutConfig()),
            schemaVersion = LAYOUT_PAYLOAD_SCHEMA_VERSION,
        )
    }

    /**
     * Reads the server's saved layout and decodes it leniently into the
     * tri-state [HomeLayoutRestoreFetch]. The [Result] wrapper only carries
     * the transport outcome — network failure is a failure, the three fetch
     * states are successes (nothing stored and undecodable are answers, not
     * errors).
     */
    suspend fun fetchServerLayout(): Result<HomeLayoutRestoreFetch> {
        val userId = currentUserId()
            ?: return Result.failure(IllegalStateException("Home layout sync requires an active user"))
        return apiClient.readHomeLayoutDisplayPreferences(userId).map { state ->
            val payload = state.json
            when {
                payload.isNullOrBlank() -> HomeLayoutRestoreFetch.Empty
                else -> decodeServerLayout(payload)
                    ?.let { decoded -> HomeLayoutRestoreFetch.Offer(decoded, state.savedAtEpochMillis, state.schemaVersion) }
                    ?: HomeLayoutRestoreFetch.Undecodable
            }
        }
    }

    // ------------------------------------------------------------------
    // The autopush engine — observable in full for tests: [autopushFlow]
    // is the whole policy (arm gate, debounce, push), [runAutopush] just
    // collects it; production runs it from [init], tests launch it inside
    // their own virtual-time scope.
    // ------------------------------------------------------------------

    internal fun autopushFlow(): Flow<Result<Unit>> =
        combine(
            homeDiscoveryStore.homeDiscovery.map { it.homeLayoutAutopush }.distinctUntilChanged(),
            identityStore.activeUserId.map { userId -> userId?.takeIf { it.isNotBlank() } }.distinctUntilChanged(),
        ) { autopushEnabled, userId ->
            // Keep the USER in the arm key (not just a Boolean): a user switch
            // while armed must restart the inner flow so drop(1) re-applies —
            // otherwise dirty revision state from the outgoing user's session
            // rides the still-live flow and immediately pushes under the new
            // identity.
            if (autopushEnabled) userId else null
        }
            .distinctUntilChanged()
            .flatMapLatest { armedUserId ->
                if (armedUserId == null) {
                    emptyFlow()
                } else {
                    homeDiscoveryStore.layoutRevision
                        // drop(1): arming (or a user switch re-arming) must not
                        // replay the dirty state accumulated while disarmed —
                        // the next layout change pushes, matching local-first.
                        .drop(1)
                        .debounce(autopushDebounceMillis)
                        .mapLatest { uploadNow() }
                }
            }

    internal suspend fun runAutopush() {
        autopushFlow().collect { }
    }

    private suspend fun currentUserId(): String? =
        identityStore.activeUserId.first()?.takeIf { it.isNotBlank() }

    companion object {
        /** Trailing debounce between the last layout write and the autopush. */
        const val AUTO_PUSH_DEBOUNCE_MILLIS = 2_000L

        /**
         * The layout payload's own schema version — bumped when
         * [HomeLayoutConfig] grows a field older clients can't decode (their
         * lenient decode handles additive drift today; this is the escape
         * hatch for breaking drift). Distinct from the enabled-set version the
         * store stamps, which versions ONE domain, not the whole payload.
         */
        const val LAYOUT_PAYLOAD_SCHEMA_VERSION = 1
    }
}

// ----------------------------------------------------------------------
// The codec — the presets machinery's exact serialize/deserialize pair
// (PreferencesJson.export for the wire, PreferencesJson.import + a
// section-name sanitizing pass for the lenient decode).
// ----------------------------------------------------------------------

/** Serializes a layout exactly like the presets share path does. */
internal fun serializeLayout(config: HomeLayoutConfig): String =
    PreferencesJson.export.encodeToString(HomeLayoutConfig.serializer(), config)

/**
 * The lenient server-payload decode (contract: unknown sections dropped —
 * the same tolerance the store's version-union read applies to its persisted
 * set): the section-typed positions ([HomeLayoutConfig.enabledHomeSectionTypes],
 * [HomeLayoutConfig.homeSectionOrder], the [HomeLayoutConfig.libraryHomeSectionOverrides]
 * values, and [HomeLayoutConfig.pinnedHomeSections] entries typed by an unknown
 * [PinnedSectionType]) are filtered to the names this client knows BEFORE the
 * strict decode, so a newer client's upload restores here instead of failing
 * wholesale. Everything else stays strict: an unknown Discover-row enum or a
 * corrupt blob decodes to null — the caller aborts the restore cleanly.
 */
internal fun decodeServerLayout(raw: String): HomeLayoutConfig? = runCatching {
    val root = PreferencesJson.import.parseToJsonElement(raw) as? JsonObject ?: return null
    val sanitized = JsonObject(
        buildMap {
            putAll(root)
            root[KEY_ENABLED_SECTION_TYPES]?.let { put(KEY_ENABLED_SECTION_TYPES, filterSectionTypeNames(it)) }
            root[KEY_SECTION_ORDER]?.let { put(KEY_SECTION_ORDER, filterSectionTypeNames(it)) }
            root[KEY_LIBRARY_SECTION_OVERRIDES]?.let { element ->
                if (element is JsonObject) {
                    put(
                        KEY_LIBRARY_SECTION_OVERRIDES,
                        JsonObject(element.mapValues { (_, value) -> filterSectionTypeNames(value) }),
                    )
                }
            }
            root[KEY_PINNED_SECTIONS]?.let { element ->
                if (element is JsonArray) {
                    put(
                        KEY_PINNED_SECTIONS,
                        JsonArray(element.filter { entry ->
                            val typeName = (entry as? JsonObject)?.get(KEY_PINNED_TYPE) as? JsonPrimitive
                            typeName != null && typeName.isString && PinnedSectionType.entries.any { it.name == typeName.content }
                        }),
                    )
                }
            }
        },
    )
    PreferencesJson.import.decodeFromJsonElement(HomeLayoutConfig.serializer(), sanitized)
}.getOrNull()

/**
 * Keeps only the string entries naming a known [HomeSectionType] (the
 * version-union decode's `entries.find` filter, applied to the raw JSON so
 * the strict decode never sees the unknowns). A wrong-shaped element is
 * passed through untouched — the strict decode then aborts, which is the
 * corrupt-payload contract, not the unknown-section one.
 */
private fun filterSectionTypeNames(element: JsonElement): JsonElement =
    when (element) {
        is JsonArray -> JsonArray(
            element.filter { name ->
                val primitive = name as? JsonPrimitive
                primitive != null && primitive.isString && HomeSectionType.entries.any { it.name == primitive.content }
            },
        )
        else -> element
    }

private const val KEY_ENABLED_SECTION_TYPES = "enabledHomeSectionTypes"
private const val KEY_SECTION_ORDER = "homeSectionOrder"
private const val KEY_LIBRARY_SECTION_OVERRIDES = "libraryHomeSectionOverrides"
private const val KEY_PINNED_SECTIONS = "pinnedHomeSections"
private const val KEY_PINNED_TYPE = "type"
