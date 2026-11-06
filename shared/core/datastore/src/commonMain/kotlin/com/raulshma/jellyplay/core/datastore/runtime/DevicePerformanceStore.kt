package com.raulshma.jellyplay.core.datastore.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.dataDegradingToDefaults
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.PerformanceDetection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-device AUTO-detection cache for the playback performance profiles
 * (issue #186 §5.5): the sticky [PerformanceDetection] the
 * [VideoPerformanceResolver][com.raulshma.jellyplay.core.data.playback.VideoPerformanceResolver]
 * consults until its fingerprint or the detector version changes.
 *
 * **Not a preference spec, not backed up** (§5.5: "detection is per-device
 * state, not a user preference; keeping it out of `AppRuntimeState` keeps it
 * out of the backup envelope and avoids changing backup semantics"). This is
 * why the store is a dedicated seam instead of an `AppRuntimeState` field.
 *
 * **Storage**: reuses the shared `"user_prefs"` DataStore file under its own
 * `performance_*` keys — same single-instance-per-file rule every other
 * runtime store follows. Three keys per §5.5:
 *  - `performance_detection_json` — the serialized detection (authoritative);
 *  - `performance_detector_version` — the detector version that produced it;
 *  - `performance_fingerprint` — the device fingerprint it was detected for.
 * The two scalar keys are written alongside the JSON for cheap reads; a read
 * whose standalone version disagrees with the JSON's is treated as corrupt.
 */
class DevicePerformanceStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    // Raw read arm — same degrade-to-defaults policy as every preference
    // store: a failed read degrades to defaults (here: "no detection"), it
    // never throws at the eager collector.
    private val sharedPrefs: Flow<Preferences> = dataStore.dataDegradingToDefaults()

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val DETECTION_JSON = stringPreferencesKey("performance_detection_json")
        val DETECTOR_VERSION = intPreferencesKey("performance_detector_version")
        val FINGERPRINT = stringPreferencesKey("performance_fingerprint")
    }

    /**
     * The sticky detection, seeded null before the first file read lands
     * (§5.5: `StateFlow<PerformanceDetection?>` seeded null) and null when no
     * detection has been saved yet. Eagerly shared, so it self-loads shortly
     * after construction without any caller action.
     */
    val detection: StateFlow<PerformanceDetection?> =
        dataStore.sliceStateFlow(scope, seed = null, read = ::readDetection)

    /**
     * One-shot authoritative read — the resolver consults this (not the
     * eagerly-seeded [detection] value) when validating the sticky cache, so
     * an early resolve can never mistake the seed for "no cache".
     */
    suspend fun read(): PerformanceDetection? = readDetection(sharedPrefs.first())

    /**
     * Persists [detection] across all three keys (the JSON plus the scalar
     * detector-version/fingerprint mirrors).
     */
    suspend fun save(detection: PerformanceDetection) {
        dataStore.edit { prefs ->
            prefs[Keys.DETECTION_JSON] = json.encodeToString(PerformanceDetection.serializer(), detection)
            prefs[Keys.DETECTOR_VERSION] = detection.detectorVersion
            prefs[Keys.FINGERPRINT] = detection.fingerprint
        }
    }

    /** Clears the detection cache (the Re-detect path runs a fresh probe after this). */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.DETECTION_JSON)
            prefs.remove(Keys.DETECTOR_VERSION)
            prefs.remove(Keys.FINGERPRINT)
        }
    }

    /**
     * Decodes the detection JSON; null when absent or unparseable (corrupt
     * blob = no cache = re-detect, never a crash). A standalone
     * `performance_detector_version` that disagrees with the JSON's own
     * version is also treated as absent.
     */
    private fun readDetection(prefs: Preferences): PerformanceDetection? {
        val raw = prefs[Keys.DETECTION_JSON] ?: return null
        val decoded = runCatching {
            json.decodeFromString(PerformanceDetection.serializer(), raw)
        }.getOrNull() ?: return null
        val standaloneVersion = prefs[Keys.DETECTOR_VERSION]
        if (standaloneVersion != null && standaloneVersion != decoded.detectorVersion) {
            return null
        }
        return decoded
    }
}
