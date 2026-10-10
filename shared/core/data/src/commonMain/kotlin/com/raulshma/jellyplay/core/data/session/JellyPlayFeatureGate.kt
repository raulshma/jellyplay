package com.raulshma.jellyplay.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The ONE per-feature enable/disable seam for the jellyfin-plugin-jellyplay
 * companion plugin — the app-side half of the gate ADR 0010 leaves to the
 * client. [isAvailable] is the whole answer every consumer reads:
 *
 *     probe has the feature (capabilities registry) AND the user's toggle is on
 *
 * The toggle is an ORDINARY preference key in the shared user-preferences
 * DataStore — `pluginFeature.<featureKey>.enabled` (boolean; ABSENT =
 * enabled) — so the settings-sync adapter (`prefs` namespace) carries the
 * choice across devices for free and the plugin's admin-defaults tri-state
 * machinery applies to it unchanged. The `pluginFeature.` prefix MUST stay
 * outside the adapter's exclusion list (it is not a per-device namespace);
 * only the adapter's own `jpsync.*` reservations and the `dream` /
 * `screensaver` per-device prefixes are excluded.
 *
 * Two read shapes:
 *  - [isAvailable] — the reactive [StateFlow] for collectors (session
 *    controllers, screens): a toggle flip or a capabilities change re-emits;
 *  - [isAvailableNow] — the fresh one-shot read for imperative paths that
 *    run right after a synchronous probe refresh (the home fetcher's row
 *    gates, the detail enrichments, the bookmarks sync guard). The reactive
 *    flow is a `combine` behind `stateIn`, so its value can lag a refresh()
 *    that just landed by one dispatch — the one-shot reads the registry and
 *    the DataStore directly and cannot go stale.
 *
 * Toggle writes are explicit true/false, never key REMOVALS: the sync
 * adapter's snapshot only carries present keys, so a removed toggle could
 * never push (and a server-side `false` would re-adopt onto the remover).
 * Absence stays meaningful only as the never-touched default.
 */
class JellyPlayFeatureGate(
    private val statusStore: JellyPlayPluginStatusStore,
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {

    /**
     * The toggleable features whose user switch is currently ON (the probe is
     * NOT consulted — row VISIBILITY rides [JellyPlayPluginStatusStore.features],
     * this is only the switch state behind the settings "Server plugin"
     * section). Eagerly shared; a toggle write re-emits.
     */
    val enabledFeatures: StateFlow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> TOGGLEABLE_FEATURES.filterTo(mutableSetOf()) { toggle(it, prefs) } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, TOGGLEABLE_FEATURES.toSet())

    /**
     * Per-feature availability, eagerly built over [TOGGLEABLE_FEATURES] so a
     * consumer's first collect (or `.value` read) is served without setup.
     * Unknown keys can never appear in the probe's registry, so they read a
     * constant false.
     */
    private val availability: Map<String, StateFlow<Boolean>> =
        TOGGLEABLE_FEATURES.associateWith { feature ->
            combine(statusStore.features, toggleFlow(feature)) { features, enabled ->
                enabled && feature in features
            }.stateIn(scope, SharingStarted.Eagerly, false)
        }

    /** The reactive gate: probe has [feature] AND the user's toggle is on. */
    fun isAvailable(feature: String): StateFlow<Boolean> =
        availability[feature] ?: MutableStateFlow(false)

    /**
     * The fresh one-shot gate for imperative paths (see the class KDoc):
     * reads the registry snapshot and the DataStore directly, so a probe
     * refresh that just landed synchronously is honored without waiting for
     * the reactive pipeline's next emission.
     */
    suspend fun isAvailableNow(feature: String): Boolean =
        feature in statusStore.features.value && toggle(feature, dataStore.data.catch { emit(emptyPreferences()) }.first())

    /**
     * Persists the user's per-feature choice. Always writes an explicit
     * boolean (never removes the key) — see the class KDoc's sync rule.
     */
    suspend fun setEnabled(feature: String, enabled: Boolean) {
        dataStore.edit { prefs -> prefs[toggleKey(feature)] = enabled }
    }

    /** The reactive toggle half of [isAvailable] (corrupt read → default-on). */
    private fun toggleFlow(feature: String): Flow<Boolean> =
        dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs -> toggle(feature, prefs) }
            .distinctUntilChanged()

    /** ABSENT = enabled — the toggle's default-on contract. */
    private fun toggle(feature: String, prefs: Preferences): Boolean =
        prefs[toggleKey(feature)] ?: true

    private fun toggleKey(feature: String) = booleanPreferencesKey(toggleKeyName(feature))

    companion object {
        /** `pluginFeature.<featureKey>.enabled` — the ordinary preference keys (synced). */
        const val TOGGLE_KEY_PREFIX = "pluginFeature."
        const val TOGGLE_KEY_SUFFIX = ".enabled"

        /**
         * The DataStore key name one feature's toggle rides — the single home
         * of the key grammar (`pluginFeature.<featureKey>.enabled`). The sync
         * wire composite the server's admin-defaults `modes` map addresses is
         * `"<namespace>/<this>"` (the `prefs` adapter's namespace + this key
         * name); the composite lives with the sync-facing consumers since the
         * `prefs` namespace is the adapter's vocabulary.
         */
        fun toggleKeyName(feature: String): String = TOGGLE_KEY_PREFIX + feature + TOGGLE_KEY_SUFFIX

        /**
         * The keys a USER toggle exists for: the user-facing capabilities plus
         * the admin-context transcodes monitor. The meta keys (settings-sync,
         * device-profiles, admin-defaults, config-backup) are deliberately
         * absent — they are infrastructure the probe alone governs, and no
         * settings row exposes them.
         */
        val TOGGLEABLE_FEATURES: List<String> = listOf(
            JellyPlayPluginFeatures.Events,
            JellyPlayPluginFeatures.Messages,
            JellyPlayPluginFeatures.SeerrBridge,
            JellyPlayPluginFeatures.Ratings,
            JellyPlayPluginFeatures.CustomRows,
            JellyPlayPluginFeatures.SeasonalRows,
            JellyPlayPluginFeatures.AnimeMarkers,
            JellyPlayPluginFeatures.Recommendations,
            JellyPlayPluginFeatures.UserRatings,
            JellyPlayPluginFeatures.Bookmarks,
            JellyPlayPluginFeatures.Newsletter,
            JellyPlayPluginFeatures.Transcodes,
            JellyPlayPluginFeatures.Push,
            JellyPlayPluginFeatures.Analytics,
        )
    }
}

/**
 * The nullable-seam ladder shared by every consumer constructible without the
 * plugin seams (direct-construction tests): the ONE gate seam when wired,
 * otherwise the probe-only fallback — AVAILABLE AND the registry has
 * [feature] ([JellyPlayPluginStatusStore.hasFeature] alone is equivalent —
 * the feature set only populates on a successful AVAILABLE probe). Unwired
 * consumers gate CLOSED: the registry is the only gating mechanism (ADR 0010
 * §6), so an absent gate must never read as open.
 */
suspend fun JellyPlayFeatureGate?.isAvailableNowOrProbe(
    statusStore: JellyPlayPluginStatusStore,
    feature: String,
): Boolean =
    this?.isAvailableNow(feature)
        ?: (statusStore.status.value == JellyPlayPluginStatus.AVAILABLE && statusStore.hasFeature(feature))

/** The reactive twin of [isAvailableNowOrProbe] for collector sites. */
fun JellyPlayFeatureGate?.isAvailableOrProbe(
    statusStore: JellyPlayPluginStatusStore,
    feature: String,
): Flow<Boolean> =
    this?.isAvailable(feature)
        ?: combine(statusStore.status, statusStore.features) { status, features ->
            status == JellyPlayPluginStatus.AVAILABLE && feature in features
        }
