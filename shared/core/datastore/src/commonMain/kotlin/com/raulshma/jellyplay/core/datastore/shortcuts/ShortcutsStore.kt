package com.raulshma.jellyplay.core.datastore.shortcuts

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.datastore.dataDegradingToDefaults
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.randomUuidString
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ShortcutLimits
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.AtomicReference

/**
 * Deep module owning the **user shortcuts** preference domain: the shortcut
 * definitions (target + icon + optional label override) and the two per-surface
 * pin/order lists (home header dock, navigation overflow menu).
 *
 * Mirrors the `HomeDiscoveryStore` shape where it matters and stays minimal
 * where it doesn't: every key is namespaced per active user
 * (`u_<userId>::<canonical>` — [ShortcutsPreferenceSpecs]' KDoc), the read
 * projection serves defaults pre-login, and writes resolve the user from the
 * snapshot being edited so they are atomic with respect to user switches.
 * Unlike the home store there is no legacy layer — these keys shipped
 * namespaced — so there is no migration.
 *
 * **Invariants (the store is the clamp/normalize owner):** definitions dedupe
 * by id (first wins) and by `(kind, targetId)` pair (first wins — one
 * definition per target keeps the editor's target picker unambiguous); order
 * lists reference only defined ids, dedupe, and clamp to
 * [ShortcutLimits.HEADER_MAX] / [ShortcutLimits.OVERFLOW_MAX]; a definition may be
 * unpinned (in neither list). Every write normalizes, so no caller can
 * persist a config that violates these.
 *
 * **Defaults:** the persisted default is EMPTY — an unconfigured user sees the
 * code-defined default surface layouts (the pre-customization hard-coded
 * menus) rendered by the resolve policy in core:ui. Reset therefore restores
 * the shipped defaults, and a stored config always means "the user edited".
 */
@OptIn(ExperimentalAtomicApi::class)
class ShortcutsStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
    private val identityStore: ServerIdentityStore,
) {
    private val json get() = PreferenceCodec.json

    internal object Keys {
        val SHORTCUTS_DEFINITIONS = stringPreferencesKey(ShortcutsPreferenceSpecs.DEFINITIONS.keyName)
        val SHORTCUTS_HEADER_ORDER = stringPreferencesKey(ShortcutsPreferenceSpecs.HEADER_ORDER.keyName)
        val SHORTCUTS_OVERFLOW_ORDER = stringPreferencesKey(ShortcutsPreferenceSpecs.OVERFLOW_ORDER.keyName)
    }

    /**
     * Surface caps live on [ShortcutLimits] (shared with the editor UI); the
     * store stays the clamp owner — [normalize] enforces them on every write.
     */

    // ------------------------------------------------------------------
    // Read projection
    // ------------------------------------------------------------------

    private val sharedPrefs = dataStore.dataDegradingToDefaults()

    val shortcuts: StateFlow<ShortcutsSlice> = combine(
        identityStore.activeUserId,
        sharedPrefs,
    ) { userId, prefs ->
        val uid = userId?.takeIf { it.isNotBlank() }
        // Pre-login: no namespace exists yet — serve the default slice.
        if (uid == null) ShortcutsSlice() else readerFor(uid).read(prefs)
    }
        .distinctUntilChanged()
        .stateIn(externalScope, SharingStarted.Eagerly, ShortcutsSlice())

    /**
     * Parse-cache-aware reader for the given user — reused across emissions for
     * the same user, recreated on user switch so one user can never be served
     * another user's cached parse (the `HomeDiscoveryStore`-precedent CAS
     * publication; a lost race merely re-parses, never a cross-user read).
     */
    private val readerByUser = AtomicReference<Pair<String, UserReader>?>(null)

    private fun readerFor(uid: String): UserReader {
        while (true) {
            val current = readerByUser.load()
            current?.let { (id, reader) -> if (id == uid) return reader }
            val next = UserReader(uid)
            if (readerByUser.compareAndSet(current, uid to next)) return next
        }
    }

    /** Snapshot projection for [prefs], resolved against its embedded active user. */
    internal fun read(prefs: Preferences): ShortcutsSlice {
        val uid = identityStore.activeUserIdIn(prefs) ?: return ShortcutsSlice()
        return readerFor(uid).read(prefs)
    }

    private inner class UserReader(private val userId: String) {

        // Parse caches — per-user by construction: one UserReader per user.
        private var cachedDefinitions = ParsedCache<List<ShortcutDefinition>>(null, emptyList())
        private var cachedHeaderOrder = ParsedCache<List<String>>(null, emptyList())
        private var cachedOverflowOrder = ParsedCache<List<String>>(null, emptyList())

        fun read(prefs: Preferences): ShortcutsSlice = ShortcutsSlice(
            definitions = readDefinitions(prefs),
            headerOrder = readOrder(prefs, ShortcutsPreferenceSpecs.HEADER_ORDER, cachedHeaderOrder) { cachedHeaderOrder = it },
            overflowOrder = readOrder(prefs, ShortcutsPreferenceSpecs.OVERFLOW_ORDER, cachedOverflowOrder) { cachedOverflowOrder = it },
        )

        /**
         * Lenient decode: an undecodable blob (hand-edited, newer-version shape
         * drift) degrades to the default instead of wedging the read — the same
         * tolerance `decodeOrDefault` applies on the write side.
         */
        private fun readDefinitions(prefs: Preferences): List<ShortcutDefinition> =
            PreferenceCodec.cachedJson(
                raw = prefs[rawShortcutsUserKey(userId, ShortcutsPreferenceSpecs.DEFINITIONS)],
                cache = cachedDefinitions,
                default = emptyList(),
                parse = { encoded ->
                    try {
                        json.decodeFromString<List<ShortcutDefinition>>(encoded)
                    } catch (_: Exception) {
                        emptyList()
                    }
                },
                cacheRef = { cachedDefinitions = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )

        private fun readOrder(
            prefs: Preferences,
            row: PreferenceSpec<List<String>>,
            cache: ParsedCache<List<String>>,
            cacheRef: (ParsedCache<List<String>>) -> Unit,
        ): List<String> = PreferenceCodec.cachedJson(
            raw = prefs[rawShortcutsUserKey(userId, row)],
            cache = cache,
            default = emptyList(),
            parse = { encoded ->
                try {
                    json.decodeFromString<List<String>>(encoded)
                } catch (_: Exception) {
                    emptyList()
                }
            },
            cacheRef = cacheRef,
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )
    }

    // ------------------------------------------------------------------
    // Normalization — the single invariants owner (every write + restore).
    // ------------------------------------------------------------------

    /**
     * Applies the store's documented invariants to a raw config: definitions
     * dedupe by id then by `(kind, targetId)` (first wins), blank ids/targets
     * drop; order lists reference defined ids only, dedupe, and clamp to the
     * surface caps.
     */
    internal fun normalize(
        definitions: List<ShortcutDefinition>,
        headerOrder: List<String>,
        overflowOrder: List<String>,
    ): ShortcutsConfig {
        val byId = LinkedHashMap<String, ShortcutDefinition>()
        val seenTargets = HashSet<String>()
        for (definition in definitions) {
            if (definition.id.isBlank() || definition.targetId.isBlank()) continue
            if (byId.containsKey(definition.id)) continue
            if (!seenTargets.add(definition.targetKey)) continue
            byId[definition.id] = definition
        }
        val definedIds = byId.keys
        return ShortcutsConfig(
            definitions = byId.values.toList(),
            headerOrder = headerOrder.filter { it in definedIds }.distinct().take(ShortcutLimits.HEADER_MAX),
            overflowOrder = overflowOrder.filter { it in definedIds }.distinct().take(ShortcutLimits.OVERFLOW_MAX),
        )
    }

    // ------------------------------------------------------------------
    // Setters — every write resolves the user from the snapshot being
    // edited (atomic vs user switches; skipped pre-login), reads current,
    // normalizes, and commits all three keys in ONE atomic edit.
    // ------------------------------------------------------------------

    private suspend fun editForUser(block: (MutablePreferences, String) -> Unit): Preferences =
        dataStore.edit { prefs ->
            val userId = identityStore.activeUserIdIn(prefs) ?: return@edit
            block(prefs, userId)
        }

    private fun writeConfig(prefs: MutablePreferences, userId: String, config: ShortcutsConfig) {
        prefs[rawShortcutsUserKey(userId, ShortcutsPreferenceSpecs.DEFINITIONS)] = json.encodeToString(config.definitions)
        prefs[rawShortcutsUserKey(userId, ShortcutsPreferenceSpecs.HEADER_ORDER)] = json.encodeToString(config.headerOrder)
        prefs[rawShortcutsUserKey(userId, ShortcutsPreferenceSpecs.OVERFLOW_ORDER)] = json.encodeToString(config.overflowOrder)
    }

    private fun readConfig(prefs: Preferences, userId: String): ShortcutsConfig {
        val slice = readerFor(userId).read(prefs)
        return ShortcutsConfig(slice.definitions, slice.headerOrder, slice.overflowOrder)
    }

    suspend fun setShortcutsConfig(
        definitions: List<ShortcutDefinition>,
        headerOrder: List<String>,
        overflowOrder: List<String>,
    ) = editForUser { prefs, userId ->
        writeConfig(prefs, userId, normalize(definitions, headerOrder, overflowOrder))
    }

    /** Fresh persisted-identity value for a new [ShortcutDefinition.id]. */
    fun newShortcutId(): String = randomUuidString()

    /**
     * Adds [definition] and pins it per [pinHeader] / [pinOverflow]. No-ops
     * when the target already has a definition (one definition per target —
     * see the class KDoc) or when the target surface is at capacity.
     */
    suspend fun addShortcut(
        definition: ShortcutDefinition,
        pinHeader: Boolean = false,
        pinOverflow: Boolean = false,
    ) = editForUser { prefs, userId ->
        val current = readConfig(prefs, userId)
        if (current.definitions.any { it.targetKey == definition.targetKey }) return@editForUser
        val headerOrder = current.headerOrder +
            (if (pinHeader && current.headerOrder.size < ShortcutLimits.HEADER_MAX) listOf(definition.id) else emptyList())
        val overflowOrder = current.overflowOrder +
            (if (pinOverflow && current.overflowOrder.size < ShortcutLimits.OVERFLOW_MAX) listOf(definition.id) else emptyList())
        writeConfig(prefs, userId, normalize(current.definitions + definition, headerOrder, overflowOrder))
    }

    /** Replaces the definition with [definition.id] (icon / label edits). No-op when absent. */
    suspend fun updateShortcut(definition: ShortcutDefinition) = editForUser { prefs, userId ->
        val current = readConfig(prefs, userId)
        if (current.definitions.none { it.id == definition.id }) return@editForUser
        writeConfig(
            prefs,
            userId,
            normalize(
                current.definitions.map { if (it.id == definition.id) definition else it },
                current.headerOrder,
                current.overflowOrder,
            ),
        )
    }

    /** Pins/unpins [id] on each surface. Pinning a full surface is a no-op for that surface. */
    suspend fun setShortcutSurfaces(id: String, header: Boolean, overflow: Boolean) = editForUser { prefs, userId ->
        val current = readConfig(prefs, userId)
        if (current.definitions.none { it.id == id }) return@editForUser
        val headerOrder = when {
            header && id !in current.headerOrder && current.headerOrder.size < ShortcutLimits.HEADER_MAX -> current.headerOrder + id
            !header -> current.headerOrder - id
            else -> current.headerOrder
        }
        val overflowOrder = when {
            overflow && id !in current.overflowOrder && current.overflowOrder.size < ShortcutLimits.OVERFLOW_MAX -> current.overflowOrder + id
            !overflow -> current.overflowOrder - id
            else -> current.overflowOrder
        }
        writeConfig(prefs, userId, normalize(current.definitions, headerOrder, overflowOrder))
    }

    /** Moves [id] one slot up/down within [surface]'s order. No-op when unpinned or at the edge. */
    suspend fun moveShortcut(id: String, surface: ShortcutSurface, up: Boolean) = editForUser { prefs, userId ->
        val current = readConfig(prefs, userId)
        val order = when (surface) {
            ShortcutSurface.HEADER -> current.headerOrder
            ShortcutSurface.OVERFLOW -> current.overflowOrder
        }
        val index = order.indexOf(id)
        if (index < 0) return@editForUser
        val target = if (up) index - 1 else index + 1
        if (target !in order.indices) return@editForUser
        val moved = order.toMutableList().apply {
            removeAt(index)
            add(target, id)
        }
        writeConfig(
            prefs,
            userId,
            when (surface) {
                ShortcutSurface.HEADER -> normalize(current.definitions, moved, current.overflowOrder)
                ShortcutSurface.OVERFLOW -> normalize(current.definitions, current.headerOrder, moved)
            },
        )
    }

    /** Removes the definition and both its pins. No-op when absent. */
    suspend fun removeShortcut(id: String) = editForUser { prefs, userId ->
        val current = readConfig(prefs, userId)
        if (current.definitions.none { it.id == id }) return@editForUser
        writeConfig(
            prefs,
            userId,
            normalize(
                current.definitions.filterNot { it.id == id },
                current.headerOrder - id,
                current.overflowOrder - id,
            ),
        )
    }

    /**
     * Clears the user's whole shortcuts config — back to the empty default
     * (the code-defined default surface layouts). The category-reset path
     * reaches the same state through [removeDynamicResetKeys].
     */
    suspend fun resetShortcuts() = editForUser { prefs, userId ->
        prefs.remove(rawShortcutsUserKey(userId, ShortcutsPreferenceSpecs.DEFINITIONS))
        prefs.remove(rawShortcutsUserKey(userId, ShortcutsPreferenceSpecs.HEADER_ORDER))
        prefs.remove(rawShortcutsUserKey(userId, ShortcutsPreferenceSpecs.OVERFLOW_ORDER))
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back — into the
     * CURRENT active user's namespace, normalized (a restore blob is never
     * trusted over the store's own invariants).
     */
    suspend fun restore(slice: ShortcutsSlice) {
        editForUser { prefs, userId ->
            writeConfig(prefs, userId, normalize(slice.definitions, slice.headerOrder, slice.overflowOrder))
        }
    }

    /**
     * Category reset participation: the canonical keys whose spec rows declare
     * [category] — every row here rides `HOME_DISCOVERY`, the same bucket as
     * the nav-customization keys. The list covers the canonical wire names for
     * the reset-coverage guard; the per-user namespaced entries (the actual
     * live keys) are dynamic and are stripped by [removeDynamicResetKeys]
     * inside the reset edit, matching the `HomeDiscoveryStore` split.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        ShortcutsPreferenceSpecs.all
            .filter { it.resetCategory == category }
            .map { stringPreferencesKey(it.keyName) }

    /**
     * Factory-reset participation for the dynamic keys: strips every user's
     * `u_<userId>::shortcuts_*` entries (canonical-suffix matching via
     * [UserNamespacedKeys.isNamespaced], so an unrelated key that merely starts
     * with `u_` is never touched). No-ops for every other category.
     */
    internal fun removeDynamicResetKeys(category: PreferenceResetCategory, prefs: MutablePreferences) {
        if (category != PreferenceResetCategory.HOME_DISCOVERY) return
        val canonicalNames = ShortcutsPreferenceSpecs.all.mapTo(mutableSetOf()) { it.keyName }
        prefs.asMap().keys
            .filter { key -> UserNamespacedKeys.isNamespaced(key.name, canonicalNames) }
            .forEach { prefs.remove(it) }
    }
}

/** Which surface a shortcut is pinned to — declared in `core.model` (shared with core:ui). */
typealias ShortcutSurface = com.raulshma.jellyplay.core.model.ShortcutSurface

/**
 * The normalized write shape — [ShortcutsStore]'s commands read current state
 * into this, transform, normalize, and write it back atomically.
 */
internal data class ShortcutsConfig(
    val definitions: List<ShortcutDefinition>,
    val headerOrder: List<String>,
    val overflowOrder: List<String>,
)

/**
 * The user shortcuts preference slice. Plain data class. Defaults mirror the
 * empty persisted state — the resolve policy in core:ui renders the
 * code-defined default surface layouts while the config stays empty.
 */
@Immutable
@Serializable
data class ShortcutsSlice(
    val definitions: List<ShortcutDefinition> = emptyList(),
    val headerOrder: List<String> = emptyList(),
    val overflowOrder: List<String> = emptyList(),
) {
    /** True while the user has never customized — surfaces render their shipped defaults. */
    val isUnconfigured: Boolean get() = definitions.isEmpty() && headerOrder.isEmpty() && overflowOrder.isEmpty()
}
