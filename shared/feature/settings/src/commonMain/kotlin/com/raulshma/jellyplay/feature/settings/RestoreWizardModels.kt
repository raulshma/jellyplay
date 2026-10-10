package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerPreferenceSpecs
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingWrite
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * The unified restore wizard's pure model layer (Wave 5) — everything the
 * [RestoreWizardViewModel] and its screen consume that needs no Composition
 * and no I/O, so the diff classification, the two-tier selection algebra and
 * the reject-reason mapping stay pin-able without a VM.
 */

/** The wizard's internal steps (single screen, two states). */
enum class WizardStep { SOURCE, DIFF }

// ---------------------------------------------------------------------------
// Snapshot-source diff (server restore point vs current server state)
// ---------------------------------------------------------------------------

/** One row's classification against the live server state. */
enum class SnapshotRowKind {
    /** Both sides carry the key, values differ. */
    CHANGED,

    /** The snapshot has the key, the live store lacks it. */
    ADDED,

    /** The live store has the key, the snapshot lacks it (a tombstone write when its group is selected). */
    REMOVED,
}

/**
 * One `(profile, ns, key)` triple's before/after face (values rendered
 * verbatim; the screen truncates). The device profile is part of the diff
 * identity: base and overlay rows sharing an ns/key are DISTINCT rows — a
 * snapshot carries every profile's view (`""` base, desktop, phone, tv) and
 * each row applies back under its own origin profile.
 */
@Immutable
data class SnapshotRowDiff(
    val ns: String,
    val key: String,
    val kind: SnapshotRowKind,
    /** The snapshot's value (null for [SnapshotRowKind.REMOVED] rows). */
    val snapshotValue: JsonElement?,
    /** The live row's value (null for [SnapshotRowKind.ADDED] rows). */
    val liveValue: JsonElement?,
    /** The device profile this row's authoritative side carries (`""` = base). */
    val profile: String = "",
    /** The snapshot row's stored LWW stamp (0 for [SnapshotRowKind.REMOVED] rows). */
    val snapshotUpdatedAt: Long = 0,
    /** The live row's stored LWW stamp (0 for [SnapshotRowKind.ADDED] rows). */
    val liveUpdatedAt: Long = 0,
)

/**
 * One selectable diff group: a whole namespace (`search`, `cw`, …) or — inside
 * the `prefs` namespace — one preference domain (or the "Other" bucket).
 */
@Immutable
data class SnapshotGroupDiff(
    /** The group's selection id: the ns itself, or the domain id inside `prefs`. */
    val id: String,
    /** Resolved display name (VM-side one-shot resource resolution). */
    val title: String,
    /** The namespace the group's rows live in (`prefs` for domain groups). */
    val ns: String,
    val rows: List<SnapshotRowDiff>,
) {
    val changed: Int get() = rows.count { it.kind == SnapshotRowKind.CHANGED }
    val added: Int get() = rows.count { it.kind == SnapshotRowKind.ADDED }
    val removed: Int get() = rows.count { it.kind == SnapshotRowKind.REMOVED }
}

/** The snapshot-source diff split into its two selection tiers. */
@Immutable
data class SnapshotDiffGroups(
    /** One group per non-`prefs` namespace the snapshot carries, sorted by ns. */
    val namespaces: List<SnapshotGroupDiff>,
    /** The `prefs` rows sub-grouped into preference domains (the "Other" bucket last). */
    val prefDomains: List<SnapshotGroupDiff>,
) {
    val totalGroups: Int get() = namespaces.size + prefDomains.size
}

/**
 * The prefs-namespace key → domain mapping, built once from the spec
 * declarations (`AppearancePreferenceSpecs.all` etc. — the stores that declare
 * their persisted key names as spec rows). Keys whose canonical name maps to
 * no declared domain land in the [OTHER_DOMAIN] bucket. The per-user
 * `u_<userId>::<canonical>` namespacing grammar is stripped before matching.
 */
object PrefsDomainCatalog {
    /** The bucket id for keys no declared spec covers. */
    const val OTHER_DOMAIN = "other"

    /** The namespace the domain sub-grouping applies to. */
    const val PREFS_NS = "prefs"

    /** Declared domains, in stable display order: domain id → the spec rows that name it. */
    private val declaredDomains: List<Pair<String, List<String>>> = listOf(
        "appearance" to AppearancePreferenceSpecs.all.map { it.keyName },
        "playback" to PlaybackPreferenceSpecs.all.map { it.keyName },
        "videoplayer" to VideoPlayerPreferenceSpecs.all.map { it.keyName },
        "home" to HomeDiscoveryPreferenceSpecs.all.map { it.keyName },
        "screensaver" to ScreensaverPreferenceSpecs.all.map { it.keyName },
        "experimental" to ExperimentalPreferenceSpecs.all.map { it.keyName },
    )

    /** Canonical key name → domain id (first declaration wins; the spec sets are disjoint). */
    private val keyToDomain: Map<String, String> = buildMap {
        for ((domain, keys) in declaredDomains) {
            for (key in keys) putIfAbsent(key, domain)
        }
    }

    /**
     * The domain a `prefs`-namespace [key] belongs to, or [OTHER_DOMAIN]'s
     * sentinel `null`. A `u_<userId>::<canonical>` key matches on its
     * canonical suffix (the split-on-LAST-`::` grammar
     * `UserNamespacedKeys` documents).
     */
    fun declaredDomainOf(key: String): String? {
        val canonical = stripUserNamespace(key)
        return keyToDomain[canonical]
    }

    /**
     * The canonical display form of a `prefs` key — the `u_<userId>::` user
     * namespace stripped (the diff UI renders canonical names, never the
     * raw per-user grammar).
     */
    fun stripUserNamespace(key: String): String {
        if (!key.startsWith("u_")) return key
        val separator = key.lastIndexOf("::")
        if (separator <= 2) return key
        return key.substring(separator + 2)
    }
}

/**
 * Classifies one restore point's rows against the live server state
 * (the export bundle's rows) and groups them into the wizard's two tiers.
 * Pure so the classification stays pin-able: changed = both sides, values
 * differ; added = snapshot-only; removed = live-only (tombstoned on apply
 * when its group is selected). The diff identity is the `(profile, ns, key)`
 * triple — base and overlay rows with the same ns/key are distinct rows.
 * Namespaces the snapshot does not cover never emit groups — the wizard
 * never offers to tombstone a namespace the snapshot simply does not speak
 * about.
 *
 * @param domainTitle maps a prefs-domain id (or [PrefsDomainCatalog.OTHER_DOMAIN])
 *   to its resolved display name; ns titles pass through verbatim.
 */
fun buildSnapshotDiffGroups(
    snapshotRows: List<JellyPlaySettingsEntry>,
    liveRows: List<JellyPlaySettingsEntry>,
    domainTitle: (String) -> String,
): SnapshotDiffGroups {
    val liveByKey = liveRows.associateBy { Triple(it.profile, it.ns, it.key) }
    val snapshotKeys = snapshotRows.mapTo(HashSet()) { Triple(it.profile, it.ns, it.key) }

    // The removed side, bucketed by the ns their group would live in.
    val removedByNs = liveRows
        .filter { Triple(it.profile, it.ns, it.key) !in snapshotKeys }
        .groupBy { it.ns }

    // One row's offered diff face: classified against the live store, unchanged
    // rows dropped. The shared shape behind the namespace and prefs-domain loops.
    fun classifiedDiff(row: JellyPlaySettingsEntry): SnapshotRowDiff? {
        val live = liveByKey[Triple(row.profile, row.ns, row.key)]
        val kind = when {
            live == null -> SnapshotRowKind.ADDED
            live.value == row.value -> null // unchanged — not offered
            else -> SnapshotRowKind.CHANGED
        } ?: return null
        return SnapshotRowDiff(
            ns = row.ns,
            key = row.key,
            kind = kind,
            snapshotValue = row.value,
            liveValue = live?.value,
            profile = row.profile,
            snapshotUpdatedAt = row.updatedAt,
            liveUpdatedAt = live?.updatedAt ?: 0L,
        )
    }

    fun removedDiff(live: JellyPlaySettingsEntry) = SnapshotRowDiff(
        ns = live.ns,
        key = live.key,
        kind = SnapshotRowKind.REMOVED,
        snapshotValue = null,
        liveValue = live.value,
        profile = live.profile,
        liveUpdatedAt = live.updatedAt,
    )

    // Non-prefs namespaces: one group per ns the SNAPSHOT carries.
    val namespaces = snapshotRows
        .filter { it.ns != PrefsDomainCatalog.PREFS_NS }
        .groupBy { it.ns }
        .map { (ns, rows) ->
            val removed = removedByNs[ns].orEmpty().map(::removedDiff)
            val valueRows = rows.mapNotNull(::classifiedDiff)
            SnapshotGroupDiff(
                id = ns,
                title = ns,
                ns = ns,
                rows = (valueRows + removed).sortedBy { it.key },
            )
        }
        .filter { it.rows.isNotEmpty() }
        .sortedBy { it.id }

    // The prefs rows sub-grouped into domains (unchanged rows dropped the same way).
    val prefsRemoved = removedByNs[PrefsDomainCatalog.PREFS_NS].orEmpty()
    val byDomain = LinkedHashMap<String, MutableList<SnapshotRowDiff>>()
    fun bucket(domain: String): MutableList<SnapshotRowDiff> =
        byDomain.getOrPut(domain) { mutableListOf() }

    for (row in snapshotRows.filter { it.ns == PrefsDomainCatalog.PREFS_NS }) {
        val domain = PrefsDomainCatalog.declaredDomainOf(row.key) ?: PrefsDomainCatalog.OTHER_DOMAIN
        classifiedDiff(row)?.let { classified -> bucket(domain) += classified }
    }
    for (live in prefsRemoved) {
        val domain = PrefsDomainCatalog.declaredDomainOf(live.key) ?: PrefsDomainCatalog.OTHER_DOMAIN
        bucket(domain) += removedDiff(live)
    }

    val prefDomains = byDomain.map { (domain, rows) ->
        SnapshotGroupDiff(
            id = domain,
            title = domainTitle(domain),
            ns = PrefsDomainCatalog.PREFS_NS,
            rows = rows.sortedBy { it.key },
        )
    }.sortedWith(compareBy<SnapshotGroupDiff> { it.id == PrefsDomainCatalog.OTHER_DOMAIN }.thenBy { it.id })

    return SnapshotDiffGroups(namespaces = namespaces, prefDomains = prefDomains)
}

// ---------------------------------------------------------------------------
// Selection (two-tier parent/child checkboxes)
// ---------------------------------------------------------------------------

/** The BACKUP-FILE flavor's selection: categories + extras + external slices. */
@Immutable
data class FileSelection(
    val categories: Set<PreferenceResetCategory> = emptySet(),
    val extras: Boolean = false,
    val slices: Set<String> = emptySet(),
) {
    /** The Preferences parent tier's checked face: every declared category selected. */
    fun allCategoriesSelected(declared: List<PreferenceResetCategory>): Boolean =
        declared.isNotEmpty() && declared.all { it in categories }

    fun toggledCategory(category: PreferenceResetCategory): FileSelection = copy(
        categories = if (category in categories) categories - category else categories + category,
    )

    /** The parent tier's toggle: all-or-none over [declared]. */
    fun toggledAllCategories(declared: List<PreferenceResetCategory>): FileSelection = copy(
        categories = if (allCategoriesSelected(declared)) emptySet() else declared.toSet(),
    )

    fun toggledExtras(): FileSelection = copy(extras = !extras)

    fun toggledSlice(key: String): FileSelection = copy(
        slices = if (key in slices) slices - key else slices + key,
    )

    /** The external-slices parent tier's checked face: every offered slice selected. */
    fun allSlicesSelected(declared: List<String>): Boolean =
        declared.isNotEmpty() && declared.all { it in slices }

    /** The external-slices parent's toggle: all-or-none over [declared]. */
    fun toggledAllSlices(declared: List<String>): FileSelection = copy(
        slices = if (allSlicesSelected(declared)) emptySet() else declared.toSet(),
    )
}

/** The SNAPSHOT flavor's selection: whole namespaces + prefs domains. */
@Immutable
data class SnapshotSelection(
    val namespaces: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
) {
    /** The prefs parent's checked face: every offered domain selected. */
    fun allDomainsSelected(declared: List<SnapshotGroupDiff>): Boolean =
        declared.isNotEmpty() && declared.all { it.id in domains }

    fun toggledNamespace(ns: String): SnapshotSelection = copy(
        namespaces = if (ns in namespaces) namespaces - ns else namespaces + ns,
    )

    /** The prefs parent's toggle: all-or-none over the offered domains. */
    fun toggledPrefs(declared: List<SnapshotGroupDiff>): SnapshotSelection = copy(
        domains = if (allDomainsSelected(declared)) emptySet() else declared.map { it.id }.toSet(),
    )

    fun toggledDomain(domain: String): SnapshotSelection = copy(
        domains = if (domain in domains) domains - domain else domains + domain,
    )
}

// ---------------------------------------------------------------------------
// Apply-side rejects — the plugin's vocabulary mapped to short human buckets
// ---------------------------------------------------------------------------

/** The reject reasons' canonical buckets ([buildSnapshotWrites]' consumers render them). */
enum class RejectReason {
    STALE_WRITE, CLOCK_SKEW, QUOTA_EXCEEDED, NS_QUOTA_EXCEEDED,
    KEY_TOO_LARGE, KEY_LIMIT_REACHED, DEVICE_REVOKED, OTHER,
}

/** The plugin's raw reason string → bucket (unknown reasons keep their raw text at render). */
fun rejectReason(raw: String): RejectReason = when (raw) {
    "stale-write" -> RejectReason.STALE_WRITE
    "clock-skew" -> RejectReason.CLOCK_SKEW
    "quota-exceeded" -> RejectReason.QUOTA_EXCEEDED
    "ns-quota-exceeded" -> RejectReason.NS_QUOTA_EXCEEDED
    "key-too-large" -> RejectReason.KEY_TOO_LARGE
    "key-limit-reached" -> RejectReason.KEY_LIMIT_REACHED
    "device-revoked" -> RejectReason.DEVICE_REVOKED
    else -> RejectReason.OTHER
}

/** Writes per `applySettings` call (the sync engine's page-limit idiom). */
const val SNAPSHOT_APPLY_CHUNK = 200

/**
 * One ORIGIN-profile slice of the selected snapshot diff — the unit one
 * `applySettings` call applies (the engine pushes rows under a device
 * profile; base rows ride [SnapshotProfileBatch.profile] = `""`, which the
 * caller maps to `profile = null`).
 */
@Immutable
data class SnapshotProfileBatch(
    /** The device profile the batch's rows originate from (`""` = base). */
    val profile: String,
    val writes: List<JellyPlaySettingWrite>,
)

/**
 * Builds the snapshot-source apply batches: every selected group's
 * changed/added rows become value writes, and every snapshot-absent-but-live
 * row inside a SELECTED group becomes a `deleted=true` tombstone — scoped to
 * the selected groups only, so restoring `appearance` never wipes `audio`.
 *
 * Rows are grouped by their ORIGIN device profile and emitted as one
 * [SnapshotProfileBatch] per profile (a snapshot carries every profile's
 * view; collapsing them into one batch would stamp base-profile rows onto
 * the resolved profile). Each batch is stamped with an LWW-safe stamp: the
 * max of [now] and the newest snapshot/live row the batch touches, plus 1 —
 * a behind-clock device must not lose its restore to the very rows it
 * replaces. The max() guard keeps the restore from ADDING skew beyond the
 * ordinary client-stamped write: the stamp can only exceed the wall clock by
 * as much as the rows it replaces were already server-accepted with. (A
 * device clock >5min ahead of the server is rejected for every client-stamped
 * write alike — the plugin's `clock-skew` bucket — and is not special to the
 * restore; the contract's server-stamped `restore` route is the
 * clock-immune alternative, ridden as the pre-wave degrade below.)
 *
 * Pure: `[now]` is injected (the VM passes [com.raulshma.jellyplay.core.model.wallNowMillis]).
 */
fun buildSnapshotWrites(
    groups: SnapshotDiffGroups,
    selection: SnapshotSelection,
    now: Long,
): List<SnapshotProfileBatch> {
    val selectedNamespaces = selection.namespaces
    val selectedDomains = selection.domains

    val selectedGroups = groups.namespaces.filter { it.id in selectedNamespaces } +
        groups.prefDomains.filter { it.id in selectedDomains }

    val rowsByProfile = LinkedHashMap<String, MutableList<SnapshotRowDiff>>()
    for (group in selectedGroups) {
        for (row in group.rows) {
            rowsByProfile.getOrPut(row.profile) { mutableListOf() }.add(row)
        }
    }

    return rowsByProfile.map { (profile, rows) ->
        val stamp = maxOf(now, rows.maxOf { it.snapshotUpdatedAt }, rows.maxOf { it.liveUpdatedAt }) + 1
        SnapshotProfileBatch(
            profile = profile,
            writes = rows.mapNotNull { row ->
                when (row.kind) {
                    SnapshotRowKind.REMOVED -> JellyPlaySettingWrite(
                        ns = row.ns,
                        key = row.key,
                        updatedAt = stamp,
                        value = JsonNull,
                        deleted = true,
                    )
                    SnapshotRowKind.CHANGED, SnapshotRowKind.ADDED -> row.snapshotValue?.let { value ->
                        JellyPlaySettingWrite(
                            ns = row.ns,
                            key = row.key,
                            updatedAt = stamp,
                            value = value,
                        )
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Wave 6 — the shared pre-destructive restore-point capture
// ---------------------------------------------------------------------------

/**
 * Best-effort safety restore point before a destructive settings write — the
 * ONE helper every Wave-6 hook site runs (wizard apply, namespace reset,
 * factory reset). The sync gate is re-checked first (ADR 0010's gating rule:
 * the api client is never touched without it); a closed gate means there is
 * no server store to snapshot, which is NOT a failure. Only a FAILED call
 * (transport error, plugin 5xx) returns false — the caller warns and
 * proceeds, never blocks. A SUCCESS with a null payload is the
 * pre-restore-points plugin's quiet 404: nothing to snapshot against — a
 * quiet skip, not a failure.
 */
suspend fun captureSafetySnapshot(
    apiClient: JellyPlaySettingsSyncRoutes,
    statusStore: JellyPlayPluginStatusStore,
): Boolean {
    val gateOpen = statusStore.status.value == JellyPlayPluginStatus.AVAILABLE &&
        statusStore.hasFeature(JellyPlayPluginFeatures.SettingsSync)
    if (!gateOpen) return true
    // The block returns the api's own Result, so the outer runCatching only
    // sees a THROWN error — the api's failure arm is the inner Result's.
    return runCatchingRethrowingCancellation { apiClient.createSnapshot() }
        .getOrNull()?.isSuccess == true
}
