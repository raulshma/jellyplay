package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.datastore.BackupParser
import com.raulshma.jellyplay.core.datastore.BackupSecrets
import com.raulshma.jellyplay.core.datastore.BackupSecretsCodec
import com.raulshma.jellyplay.core.datastore.BackupSecretsException
import com.raulshma.jellyplay.core.datastore.SecretsEnvelope
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSnapshotReader
import com.raulshma.jellyplay.core.datastore.settings.buildPreferenceSliceSnapshotFromBackup
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.wallNowMillis
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_appearance
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_experimental
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_home_discovery
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.factory_reset_cat_screensaver
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unknown
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_domain_other
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_domain_videoplayer
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_done_file
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_done_full_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_done_snapshot
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_clock_skew
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_device_revoked
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_key_limit
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_key_too_large
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_ns_quota
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_other
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_quota
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_stale_write
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_secrets_done
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_safety_snapshot_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_summary_categories
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_summary_extras
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_summary_rejected
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_summary_slices
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * One BACKUP-FILE diff category's rendered face: identity + the changed rows
 * (current vs incoming) beside the row total. The selection lives in
 * [FileRestoreState.selection].
 */
@androidx.compose.runtime.Immutable
data class FileCategoryGroup(
    val category: PreferenceResetCategory,
    val title: String,
    val changed: List<PreferenceField>,
    val total: Int,
)

/** One Wave-2 external slice card's rendered face (same register as the categories). */
@androidx.compose.runtime.Immutable
data class ExternalSliceGroup(
    val key: String,
    val title: String,
    val changed: List<PreferenceField>,
    val total: Int,
)

/** The extras ("App State") card's rendered face. */
@androidx.compose.runtime.Immutable
data class ExtrasGroup(
    val changed: List<PreferenceField>,
    val total: Int,
)

/**
 * The BACKUP-FILE diff step's whole face. [backup] rides in the state so the
 * apply arms can fan it to the store seams without a second field — it is an
 * immutable value object, never mutated after staging.
 */
@androidx.compose.runtime.Immutable
data class FileRestoreState(
    val backup: SettingsBackup,
    val schemaVersion: Int,
    val versionMismatch: Boolean,
    val hasSecuritySensitive: Boolean,
    /** The origin ids differ from the active session (the confirm dialog's extra warning line). */
    val crossAccount: Boolean,
    val categories: List<FileCategoryGroup>,
    val extras: ExtrasGroup?,
    val externalSlices: List<ExternalSliceGroup>,
    val selection: FileSelection = FileSelection(),
    /** The lock-config opt-in (defaults off; the row renders only when [hasSecuritySensitive]). */
    val restoreSecuritySensitive: Boolean = false,
    // ── Wave-3 secrets block: locked until the passphrase unlocks it; the
    //    apply rides its own explicit confirm, never another apply arm. ──
    val hasSecrets: Boolean = false,
    val secretsUnlocked: SecretsRestoreSummary? = null,
    val secretsUnlocking: Boolean = false,
    /** The inline unlock failure flag (the screen renders the localized retry text). */
    val secretsError: Boolean = false,
)

/** The SNAPSHOT diff step's whole face. */
@androidx.compose.runtime.Immutable
data class SnapshotRestoreState(
    val snapshotId: String,
    val groups: SnapshotDiffGroups,
    val selection: SnapshotSelection = SnapshotSelection(),
)

/** The old-plugin degrade face: no preview read — the full server-orchestrated restore only. */
@androidx.compose.runtime.Immutable
data class FullRestoreState(
    val snapshotId: String,
)

/** The whole wizard's UI state — exactly one of the three diff faces is non-null in [WizardStep.DIFF]. */
@androidx.compose.runtime.Immutable
data class RestoreWizardUiState(
    val step: WizardStep = WizardStep.SOURCE,
    // ── SOURCE faces ──
    /** The plugin probe + `settings-sync` registry check — the server arm's availability. */
    val serverAvailable: Boolean = false,
    val snapshotsLoading: Boolean = false,
    /** True when the server arm is unusable (gate closed, no client, old plugin, failed read) — the empty state explains. */
    val snapshotsUnavailable: Boolean = false,
    val snapshots: List<JellyPlaySnapshot> = emptyList(),
    // ── DIFF faces ──
    val file: FileRestoreState? = null,
    val snapshot: SnapshotRestoreState? = null,
    val fullRestore: FullRestoreState? = null,
    // ── shared faces ──
    /** A file read/parse failure (inline warning; picking another file retries). */
    val loadError: Boolean = false,
    /** The apply's failure text (inline; the dialog has settled by then). */
    val applyError: String? = null,
    /** True between the confirm tap and the apply settling — the dialog's loading face. */
    val applying: Boolean = false,
    /** One-shot success signal — the screen navigates back on it. */
    val completed: Boolean = false,
)

/**
 * The unified restore wizard's model (Wave 5): ONE flow for restoring settings
 * regardless of source — pick source (server restore point / backup file) →
 * diff vs the live state → select categories (two-tier) → confirm
 * (destructive + cross-account + passphrase) → automatic pre-apply snapshot
 * (Wave 6 hook #1) → apply → done.
 *
 * Replaces the retired `ImportPreviewViewModel` (its parser/diff/restore logic
 * is ported verbatim — the seams it rode stay: [BackupParser],
 * [PreferenceSnapshotReader], [UserPreferencesStore.restoreV2Categories] /
 * [UserPreferencesStore.restoreExternalSlice] / [UserPreferencesStore.restoreExtras],
 * [SecretsBackupAssembler]) and absorbs the sync screen's import-from-file and
 * one-click snapshot restore rows.
 *
 * The SERVER arm diffs the restore point's rows against the CURRENT server
 * state (`exportSettings()` bundle, parsed client-side) and applies as ONE
 * `applySettings` batch — selected rows freshly stamped, snapshot-absent live
 * rows in selected groups as tombstones — then `requestSync()` so the server's
 * adopted state reaches this device. The FILE arm writes locally through the
 * store seams and pushes with the same trailing sync.
 *
 * The backup file is staged from the `uri` passed via navigation (the backup
 * screen) or picked in the wizard's SOURCE step (the sync screen entry).
 * Only the v2 per-slice shape (and forward-compatible future versions)
 * restore; the parser rejects legacy v0/v1 backups. A `getSnapshotContent`
 * miss (old plugin) degrades to the full-restore-only state — the direct
 * `restoreSnapshot(id)` with the same confirm + capture choreography.
 */
class RestoreWizardViewModel(
    private val settingsBackupIo: SettingsBackupIo,
    private val userPreferencesStore: UserPreferencesStore,
    private val snapshotReader: PreferenceSnapshotReader,
    /** The Wave-3 secrets seam (unlock preview counts + explicit apply fan-out). */
    private val secretsBackupAssembler: SecretsBackupAssembler? = null,
    /** The server arm's routes — null in file-only graphs (the server arm degrades off). */
    private val pluginApiClient: JellyPlaySettingsSyncRoutes? = null,
    /** The gate seam the server arm re-checks before every call (ADR 0010). */
    private val statusStore: JellyPlayPluginStatusStore? = null,
    /** The trailing `requestSync()` + this device's registry id for apply attribution. */
    private val syncRepository: ProfileSyncRepository? = null,
    /** Session identity — the cross-account warning vs the staged backup's origin ids. */
    private val serverIdentityStore: ServerIdentityStore? = null,
    /** Wave 6's warning surface (safety-snapshot misses + success summaries). */
    private val messageBus: UserMessageBus? = null,
    private val diffLabelResolver: suspend (List<StringResource>) -> (StringResource) -> String = ::resolveDiffLabels,
    /**
     * The wizard's own non-diff texts (summaries, warnings, reject buckets) —
     * resolved ONCE per use through this seam so pure tests can pin the texts
     * without a resource loader (the diffLabelResolver idiom).
     */
    private val textResolver: suspend (StringResource, List<Any>) -> String =
        { res, args ->
            runCatchingRethrowingCancellation { getString(res, *args.toTypedArray()) }
                .getOrElse { res.toString() }
        },
) : JellyPlayViewModel() {

    var uiState by composeState(RestoreWizardUiState())
        private set

    /** Resolved label lookup shared across a staged snapshot's diff build. */
    private var labels: (StringResource) -> String = { it.toString() }

    private var labelsLoaded = false

    /** The decrypted payload — held only in memory, wiped by re-staging. */
    private var decryptedSecrets: BackupSecrets? = null

    /**
     * The exact [SecretsEnvelope] instance [decryptedSecrets] was computed
     * from — the identity token the apply path re-checks against the currently
     * staged backup, so a payload decrypted from an old envelope can never be
     * applied after the wizard re-staged a different source.
     */
    private var decryptedForEnvelope: SecretsEnvelope? = null

    private val loadMutex = kotlinx.coroutines.sync.Mutex()
    private var loadedUri: String? = null

    /** One-shot label resolution over every resource the diff faces render (first staging pays it). */
    private suspend fun ensureLabels() {
        if (labelsLoaded) return
        labels = diffLabelResolver(
            // The categories' own displayNameRes titles the diff cards — without
            // them here the resolver's toString() fallback leaks StringResource@hash.
            PreferenceCategoryViews.flatMap { it.labelResources + it.displayNameRes } +
                ExternalSliceLabelResources + domainTitleResources +
                listOf(Res.string.settings_unknown),
        )
        labelsLoaded = true
    }

    // ---------------------------------------------------------------------------
    // Entry + SOURCE step
    // ---------------------------------------------------------------------------

    /**
     * One entry pull: the gate face + the restore-point list (server arm), then
     * the nav-arg jump — a staged [uri] goes straight to the file diff, a
     * [snapshotId] to the snapshot diff (or its degrade face).
     */
    fun start(uri: String?, snapshotId: String?) {
        launch {
            val gateOpen = syncGateOpen()
            uiState = uiState.copy(serverAvailable = gateOpen, snapshotsUnavailable = !gateOpen)
            if (gateOpen) refreshSnapshots()
            when {
                !uri.isNullOrBlank() -> loadBackupFile(uri)
                !snapshotId.isNullOrBlank() -> openSnapshot(snapshotId)
            }
        }
    }

    private suspend fun refreshSnapshots() {
        val client = pluginApiClient
        if (client == null || !syncGateOpen()) {
            uiState = uiState.copy(snapshotsUnavailable = true, snapshots = emptyList(), snapshotsLoading = false)
            return
        }
        uiState = uiState.copy(snapshotsLoading = true, snapshotsUnavailable = false)
        // apiResult flattens the api's `Result<Result<T>>`: a THROWN error and a
        // FAILED Result both land null beside the success-null payload, so all
        // three share the quiet unavailable face (the source list carries no
        // failure/null distinction).
        val snapshots = apiResult { client.getSnapshots() }.getOrNull()
        uiState = uiState.copy(
            snapshotsLoading = false,
            // null payload = old plugin → the same quiet unavailable face as a failed read.
            snapshots = snapshots.orEmpty(),
            snapshotsUnavailable = snapshots == null,
        )
    }

    /** Re-pulls the restore-point list (the SOURCE step's manual refresh). */
    fun refreshSource() {
        launch { refreshSnapshots() }
    }

    // ---------------------------------------------------------------------------
    // BACKUP-FILE arm (the retired ImportPreview's logic, ported)
    // ---------------------------------------------------------------------------

    /**
     * Reads + parses the picked backup file and stages the file diff.
     * A different uri reloads (back → pick another file); the same uri is
     * served once. Failures land in the inline [RestoreWizardUiState.loadError]
     * face and clear the staged uri so the same file can retry.
     */
    fun loadBackupFile(uriString: String) {
        launch {
            if (!loadMutex.tryLock()) return@launch
            try {
                if (loadedUri == uriString) return@launch
                loadedUri = uriString
                uiState = uiState.copy(loadError = false)
                val state = stageFile(uriString)
                // A successful re-stage invalidates any unlock from the
                // previous envelope (its in-flight decrypt is discarded by the
                // identity check; the apply path re-checks it).
                decryptedSecrets = null
                decryptedForEnvelope = null
                uiState = uiState.copy(step = WizardStep.DIFF, file = state)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedUri = null
                // The resolved message is surfaced verbatim by the screen's warning card.
                lastLoadErrorMessage = UserErrorMessages.resolve(e, "Could not read backup file")
                lastLoadErrorIsServer = false
                uiState = uiState.copy(loadError = true)
            } finally {
                loadMutex.unlock()
            }
        }
    }

    /** The load failure's resolved text — read by the screen when [RestoreWizardUiState.loadError] flips on. */
    var lastLoadErrorMessage: String? = null
        private set

    /** Whether [lastLoadErrorMessage] came from the server arm (the card's title differs). */
    var lastLoadErrorIsServer: Boolean = false
        private set

    private suspend fun stageFile(uriString: String): FileRestoreState {
        val jsonString = settingsBackupIo.readImportPayload(uriString)
            ?: throw IllegalStateException("Cannot open backup file")
        val parsed = BackupParser.parse(jsonString)
        val backup = when (parsed) {
            is BackupParser.Parsed.V2 -> parsed.backup
            is BackupParser.Parsed.Future -> parsed.backup
        }
        ensureLabels()
        return buildFileState(
            backup = backup,
            versionMismatch = parsed is BackupParser.Parsed.Future,
            hasSecuritySensitive = when (parsed) {
                is BackupParser.Parsed.V2 -> parsed.hasSecuritySensitive
                is BackupParser.Parsed.Future -> parsed.hasSecuritySensitive
            },
        )
    }

    private suspend fun buildFileState(
        backup: SettingsBackup,
        versionMismatch: Boolean,
        hasSecuritySensitive: Boolean,
    ): FileRestoreState {
        val current = snapshotReader.snapshotOnce()
        val incoming = buildPreferenceSliceSnapshotFromBackup(backup)
        val currentSnapshot = PreferenceDiffSnapshot(current) { res -> labels(res) }
        val incomingSnapshot = PreferenceDiffSnapshot(incoming) { res -> labels(res) }

        val categories = PreferenceCategoryViews.map { view ->
            FileCategoryGroup(
                category = view.category,
                title = labels(view.displayNameRes),
                changed = view.changedFields(currentSnapshot, incomingSnapshot),
                total = view.totalFields(currentSnapshot, incomingSnapshot),
            )
        }
        val noneLabel = labels(Res.string.settings_unknown)
        val extrasFields = appRuntimeFields(current.runtime, backup.extras, noneLabel)
        val extras = ExtrasGroup(
            changed = extrasFields.filter { it.changed },
            total = extrasFields.size,
        )
        val currentExternal = userPreferencesStore.externalSliceSnapshot()
        val externalSlices = buildExternalSliceDiffs(currentExternal, backup.slices, labels).map { diff ->
            ExternalSliceGroup(
                key = diff.view.key,
                title = labels(diff.view.nameRes),
                changed = diff.changed,
                total = diff.total,
            )
        }

        val identity = serverIdentityStore?.identity?.value
        val crossAccount = crossAccountDetected(backup, identity)

        return FileRestoreState(
            backup = backup,
            schemaVersion = backup.schemaVersion,
            versionMismatch = versionMismatch,
            hasSecuritySensitive = hasSecuritySensitive,
            crossAccount = crossAccount,
            categories = categories,
            extras = extras,
            externalSlices = externalSlices,
            hasSecrets = backup.secrets != null,
        )
    }

    // ---------------------------------------------------------------------------
    // SERVER-SNAPSHOT arm
    // ---------------------------------------------------------------------------

    /**
     * Loads one restore point's rows and diffs them against the CURRENT server
     * state. A null content read (unknown/not-ours id, or the server's plugin
     * predates the preview read) stages the full-restore-only degrade face. A
     * FAILED read (either the content or the live export) or an undecodable
     * export bundle is a LOAD failure — the inline error card with a retry —
     * never a silent "the live store is empty" (which would classify every
     * row ADDED and never tombstone) and never the degrade face.
     */
    fun openSnapshot(snapshotId: String) {
        launch {
            val client = pluginApiClient
            if (client == null || !syncGateOpen()) {
                uiState = uiState.copy(snapshotsUnavailable = true)
                return@launch
            }
            val contentOutcome = apiResult { client.getSnapshotContent(snapshotId) }
            val content = contentOutcome.getOrNull()
            if (contentOutcome.isFailure) {
                failServerLoad(
                    UserErrorMessages.resolve(
                        contentOutcome.exceptionOrNull(),
                        "Could not load the restore point",
                    ),
                )
                return@launch
            }
            if (content == null) {
                uiState = uiState.copy(
                    step = WizardStep.DIFF,
                    file = null,
                    snapshot = null,
                    fullRestore = FullRestoreState(snapshotId),
                )
                return@launch
            }
            val liveOutcome = apiResult { client.exportSettings() }
            val liveBundle = liveOutcome.getOrNull()
            if (liveOutcome.isFailure || liveBundle == null) {
                // A failed export — or the pre-wave 404 null — leaves the live
                // state unreadable; an undecodable bundle is the same failure.
                failServerLoad(
                    liveOutcome.exceptionOrNull()?.message
                        ?: "Could not read the current settings state",
                )
                return@launch
            }
            val liveRows = parseExportRows(liveBundle)
            if (liveRows == null) {
                failServerLoad("Could not read the current settings state")
                return@launch
            }
            val snapshotRows = stampedRows(content.profiles)
            ensureLabels()
            val groups = buildSnapshotDiffGroups(snapshotRows, liveRows) { domain -> domainTitle(domain) }
            uiState = uiState.copy(
                step = WizardStep.DIFF,
                file = null,
                fullRestore = null,
                snapshot = SnapshotRestoreState(snapshotId = snapshotId, groups = groups),
                loadError = false,
            )
        }
    }

    /**
     * The server arm's read failure face: the same inline error card the file
     * arm uses (with a retry via re-tapping the restore point) — the wizard
     * stays on the SOURCE step and never stages a diff.
     */
    private fun failServerLoad(message: String) {
        lastLoadErrorMessage = message
        lastLoadErrorIsServer = true
        uiState = uiState.copy(loadError = true)
    }

    /**
     * Runs one plugin call with its two failure channels flattened: a THROWN
     * error (the outer [runCatchingRethrowingCancellation] arm) and a FAILED
     * [Result] (the api's own arm) both land in the returned Result's failure
     * side, so callers can branch on one `isFailure`.
     */
    private suspend fun <T> apiResult(block: suspend () -> Result<T>): Result<T> =
        runCatchingRethrowingCancellation(block).getOrElse { failure -> Result.failure(failure) }

    /**
     * Parses the export bundle's `profiles[].settings[]` half — the only part
     * the diff needs. Returns null when the bundle does not decode (a LOAD
     * failure, never "the live store is empty"). Lenient (unknown keys
     * ignored): the bundle also carries the catalog stamp + modes maps, which
     * are opaque here.
     */
    private fun parseExportRows(bundleJson: String): List<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry>? =
        runCatching {
            val bundle = EXPORT_JSON.decodeFromString(ExportBundle.serializer(), bundleJson)
            stampedRows(bundle.profiles)
        }.getOrNull()

    /**
     * Rows stamped with their container profile — the container's profile is
     * authoritative for the stored rows (the wire may leave the per-row field
     * blank). The one shape both the snapshot content and the export bundle
     * read through.
     */
    private fun stampedRows(
        profiles: List<com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotProfile>,
    ): List<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry> =
        profiles.flatMap { profile ->
            profile.settings.map { row ->
                if (row.profile.isBlank()) row.copy(profile = profile.profile) else row
            }
        }

    @kotlinx.serialization.Serializable
    private data class ExportBundle(
        val profiles: List<com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotProfile> = emptyList(),
    )

    // ---------------------------------------------------------------------------
    // Selection (parent/child consistency lives in the pure model)
    // ---------------------------------------------------------------------------

    fun toggleCategory(category: PreferenceResetCategory) {
        val file = uiState.file ?: return
        uiState = uiState.copy(file = file.copy(selection = file.selection.toggledCategory(category)))
    }

    fun toggleAllCategories() {
        val file = uiState.file ?: return
        uiState = uiState.copy(
            file = file.copy(
                selection = file.selection.toggledAllCategories(PreferenceCategoryViews.map { it.category }),
            ),
        )
    }

    fun toggleExtras() {
        val file = uiState.file ?: return
        uiState = uiState.copy(file = file.copy(selection = file.selection.toggledExtras()))
    }

    fun toggleSlice(key: String) {
        val file = uiState.file ?: return
        uiState = uiState.copy(file = file.copy(selection = file.selection.toggledSlice(key)))
    }

    /** The external-slices parent's toggle: all-or-none over the offered slices. */
    fun toggleAllSlices() {
        val file = uiState.file ?: return
        uiState = uiState.copy(
            file = file.copy(
                selection = file.selection.toggledAllSlices(file.externalSlices.map { it.key }),
            ),
        )
    }

    fun toggleRestoreSecuritySensitive(enabled: Boolean) {
        val file = uiState.file ?: return
        uiState = uiState.copy(file = file.copy(restoreSecuritySensitive = enabled))
    }

    fun toggleNamespace(ns: String) {
        val snapshot = uiState.snapshot ?: return
        uiState = uiState.copy(snapshot = snapshot.copy(selection = snapshot.selection.toggledNamespace(ns)))
    }

    fun togglePrefs() {
        val snapshot = uiState.snapshot ?: return
        uiState = uiState.copy(
            snapshot = snapshot.copy(selection = snapshot.selection.toggledPrefs(snapshot.groups.prefDomains)),
        )
    }

    fun togglePrefDomain(domain: String) {
        val snapshot = uiState.snapshot ?: return
        uiState = uiState.copy(snapshot = snapshot.copy(selection = snapshot.selection.toggledDomain(domain)))
    }

    /** The confirm dialog's count face: how many top groups the restore touches. */
    fun selectedGroupCount(): Int {
        uiState.file?.let { file ->
            return file.selection.categories.size + (if (file.selection.extras) 1 else 0) + file.selection.slices.size
        }
        uiState.snapshot?.let { snapshot ->
            return snapshot.selection.namespaces.size +
                (if (snapshot.selection.domains.isNotEmpty()) 1 else 0)
        }
        return 1 // full-restore degrade: one operation
    }

    // ---------------------------------------------------------------------------
    // Wave-3 secrets block — unlock + explicit apply (ported verbatim)
    // ---------------------------------------------------------------------------

    /**
     * Decrypts the staged envelope with [passphrase] (wrong passphrase → the
     * inline retry flag, never a crash). On success the preview counts land in
     * the state and the payload is held in memory for the explicit apply.
     * [passphrase] is zeroed before this returns — including the early
     * returns (nothing staged, no secrets envelope, already unlocking); the
     * KDF runs on Dispatchers.Default.
     *
     * The busy flag is set synchronously BEFORE the coroutine launches, so the
     * second of two rapid confirmations is refused instead of queueing a
     * second 600k KDF run. The result is bound to the exact staged
     * [SecretsEnvelope] instance: a decrypt that settles after the wizard
     * re-staged a DIFFERENT source is discarded — it must not resurrect a
     * summary computed from the old envelope, and the apply path re-checks the
     * same identity, so a stale payload can never be applied.
     */
    fun unlockSecrets(passphrase: CharArray) {
        val file = uiState.file
        val envelope = file?.backup?.secrets
        if (file == null || envelope == null || file.secretsUnlocking) {
            passphrase.fill('\u0000')
            return
        }
        // Synchronous busy-flag flip: the second of two rapid confirmations
        // must hit the `secretsUnlocking` early return above, not queue behind
        // the first KDF run.
        uiState = uiState.copy(file = file.copy(secretsUnlocking = true, secretsError = false))
        val staged = envelope
        launch {
            try {
                val secrets = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    BackupSecretsCodec.decrypt(envelope, passphrase)
                }
                // The wizard may have re-staged a different source while this
                // decrypt ran: discard the stale result rather than surfacing
                // (or holding) a payload computed from the old envelope. The
                // passphrase is zeroed in `finally` either way.
                val current = uiState.file
                if (current?.backup?.secrets !== staged) return@launch
                decryptedSecrets = secrets
                decryptedForEnvelope = staged
                val summary = secretsBackupAssembler?.summarize(secrets)
                    ?: SecretsRestoreSummary.of(secrets)
                uiState = uiState.copy(
                    file = current.copy(secretsUnlocked = summary, secretsUnlocking = false, secretsError = false),
                )
            } catch (e: BackupSecretsException) {
                // Same identity rule on the failure arm: a stale attempt must
                // not touch the state of the newly staged source.
                val current = uiState.file
                if (current?.backup?.secrets === staged) {
                    decryptedSecrets = null
                    decryptedForEnvelope = null
                    uiState = uiState.copy(
                        file = current.copy(secretsUnlocked = null, secretsUnlocking = false, secretsError = true),
                    )
                }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    /** Dismisses the unlock error (a fresh attempt clears it anyway). */
    fun clearSecretsError() {
        val file = uiState.file ?: return
        uiState = uiState.copy(file = file.copy(secretsError = false))
    }

    /**
     * Applies the unlocked secrets payload through the assembler — requires the
     * explicit UI confirmation that precedes the call, never invoked as a side
     * effect of the main restore. The payload must still belong to the
     * currently staged envelope: after a re-stage (a different backup file,
     * or one without secrets) a stale apply is a no-op. A successful apply
     * SPENDS the payload — the in-memory plaintext and its envelope binding
     * are dropped so a second tap can never re-apply a stale copy.
     */
    fun applySecrets() {
        val secrets = decryptedSecrets ?: return
        val assembler = secretsBackupAssembler ?: return
        if (decryptedForEnvelope !== uiState.file?.backup?.secrets) return
        launch {
            try {
                assembler.apply(secrets)
                decryptedSecrets = null
                decryptedForEnvelope = null
                messageBus?.info(text(Res.string.wizard_secrets_done))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(applyError = UserErrorMessages.resolve(e, "Secrets restore failed"))
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Confirm → capture → apply → done
    // ---------------------------------------------------------------------------

    /**
     * The confirmed apply — every arm's choreography: best-effort safety
     * snapshot first (Wave 6 hook #1; failure warns through the bus, never
     * blocks), then the arm's own write, then the trailing `requestSync()` so
     * local changes push / server changes adopt. The summary lands on the bus;
     * [RestoreWizardUiState.completed] pops the wizard. A failure anywhere in
     * the choreography surfaces the inline error face ([RestoreWizardUiState.applyError])
     * and never claims success.
     */
    fun restoreSelected() {
        if (uiState.applying) return
        // Flipped synchronously (before the coroutine) so the second of two
        // rapid confirmations is refused outright instead of queueing a
        // second apply behind the first.
        uiState = uiState.copy(applying = true, applyError = null)
        launch {
            try {
                captureSafetySnapshotBestEffort()
                val summary = when {
                    uiState.file != null -> applyFileRestore()
                    uiState.snapshot != null -> applySnapshotRestore()
                    uiState.fullRestore != null -> applyFullRestore()
                    else -> null
                }
                if (summary != null) {
                    requestTrailingSync()
                    messageBus?.info(summary)
                    uiState = uiState.copy(applying = false, completed = true)
                } else {
                    uiState = uiState.copy(applying = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = uiState.copy(
                    applying = false,
                    applyError = UserErrorMessages.resolve(e, "Restore failed"),
                )
            }
        }
    }

    /** Acknowledges the one-shot success signal (the screen navigated back). */
    fun consumeCompleted() {
        uiState = uiState.copy(completed = false)
    }

    /** Dismisses the inline apply failure. */
    fun clearApplyError() {
        uiState = uiState.copy(applyError = null)
    }

    /** Wave 6 hook #1: the pre-apply restore point. Miss → bus warn, never a blocker. */
    private suspend fun captureSafetySnapshotBestEffort() {
        val client = pluginApiClient
        val store = statusStore
        if (client == null || store == null) return
        if (!captureSafetySnapshot(client, store)) {
            runCatchingRethrowingCancellation {
                messageBus?.error(text(Res.string.wizard_safety_snapshot_failed))
            }
        }
    }

    private suspend fun requestTrailingSync() {
        val repo = syncRepository ?: return
        runCatchingRethrowingCancellation { repo.requestSync() }
    }

    /** The BACKUP-FILE arm: selected categories + slices + extras through the store seams. */
    private suspend fun applyFileRestore(): String? {
        val file = uiState.file ?: return null
        val backup = file.backup
        val selection = file.selection
        if (selection.categories.isNotEmpty()) {
            userPreferencesStore.restoreV2Categories(
                backup = backup,
                categories = selection.categories,
                restoreSecuritySensitive = file.restoreSecuritySensitive,
                includeExtras = false,
            )
        }
        for (key in selection.slices) {
            userPreferencesStore.restoreExternalSlice(backup, key)
        }
        if (selection.extras) {
            userPreferencesStore.restoreExtras(backup)
        }
        val parts = buildList {
            if (selection.categories.isNotEmpty()) {
                add(text(Res.string.wizard_summary_categories, selection.categories.size))
            }
            if (selection.extras) add(text(Res.string.wizard_summary_extras))
            if (selection.slices.isNotEmpty()) {
                add(text(Res.string.wizard_summary_slices, selection.slices.size))
            }
        }
        if (parts.isEmpty()) return null
        return text(Res.string.wizard_done_file, parts.joinToString(" • "))
    }

    /**
     * The SNAPSHOT arm: the selected diff as per-ORIGIN-PROFILE batches
     * ([buildSnapshotWrites] — base rows apply under `profile = null`, overlay
     * rows under their own profile), each chunked at [SNAPSHOT_APPLY_CHUNK]
     * writes per call; applied/rejected aggregate across chunks into ONE
     * summary (+ reject buckets). A failed call throws into [restoreSelected]'s
     * catch — the inline error face — never a silent "nothing to do".
     */
    private suspend fun applySnapshotRestore(): String? {
        val client = pluginApiClient ?: return null
        val state = uiState.snapshot ?: return null
        val deviceId = syncRepository?.let {
            runCatchingRethrowingCancellation { it.currentDeviceId() }.getOrNull()
        }
        val batches = buildSnapshotWrites(state.groups, state.selection, wallNowMillis())
        if (batches.isEmpty()) return null

        val applied = mutableListOf<com.raulshma.jellyplay.core.network.api.JellyPlayAppliedSetting>()
        val rejects = mutableListOf<com.raulshma.jellyplay.core.network.api.JellyPlayRejectedSetting>()
        for (batch in batches) {
            for (chunk in batch.writes.chunked(SNAPSHOT_APPLY_CHUNK)) {
                val result = client.applySettings(
                    // `null` = the base profile; overlay batches ride their own profile.
                    profile = batch.profile.takeIf { it.isNotBlank() },
                    deviceId = deviceId,
                    writes = chunk,
                ).getOrThrow()
                applied += result.applied
                rejects += result.rejected
            }
        }

        val perNamespace = applied.groupingBy { it.ns }.eachCount()
        val nsSummary = perNamespace.entries.sortedBy { it.key }
            .joinToString(", ") { (ns, count) -> "$ns ($count)" }
        var summary = text(Res.string.wizard_done_snapshot, applied.size, nsSummary)
        if (rejects.isNotEmpty()) {
            val rendered = renderRejects(rejects)
            summary += " " + text(Res.string.wizard_summary_rejected, rejects.size, rendered)
        }
        return summary
    }

    /**
     * The old-plugin degrade arm: the server-orchestrated full restore. A
     * failed call throws into [restoreSelected]'s catch; a success with a null
     * payload (pre-wave 404, or the restore point is not the caller's) means
     * nothing was restored — surfaced as an error, never as success.
     */
    private suspend fun applyFullRestore(): String? {
        val client = pluginApiClient ?: return null
        val full = uiState.fullRestore ?: return null
        val result = client.restoreSnapshot(full.snapshotId).getOrThrow()
            ?: throw IllegalStateException("The restore point could not be restored")
        val appliedCount = result.applied.size
        val rejects = result.rejected
        var summary = text(Res.string.wizard_done_full_restore, appliedCount)
        if (rejects.isNotEmpty()) {
            summary += " " + text(Res.string.wizard_summary_rejected, rejects.size, renderRejects(rejects))
        }
        return summary
    }

    /** The rejected writes' aggregated face: "bucket ×N, bucket ×M" (ordinal order). */
    private suspend fun renderRejects(rejects: List<com.raulshma.jellyplay.core.network.api.JellyPlayRejectedSetting>): String {
        val buckets = rejects.groupingBy { rejectReason(it.reason) }.eachCount()
        val parts = mutableListOf<String>()
        for ((reason, count) in buckets.entries.sortedBy { it.key.ordinal }) {
            parts += "${rejectLabel(reason)} ×$count"
        }
        return parts.joinToString(", ")
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * The ONE gate seam for the server arm (the sync screen's pattern): probe
     * AVAILABLE AND the `settings-sync` meta key. Meta keys carry no user
     * toggle, so the gate is probe-only.
     */
    private fun syncGateOpen(): Boolean {
        val store = statusStore ?: return false
        return pluginApiClient != null &&
            store.status.value == JellyPlayPluginStatus.AVAILABLE &&
            store.hasFeature(JellyPlayPluginFeatures.SettingsSync)
    }

    /** The wizard's own texts through the injectable seam (see [textResolver]). */
    private suspend fun text(res: StringResource, vararg args: Any): String =
        textResolver(res, args.toList())

    /** The reject buckets' short human text (the summary's rendered tail). */
    private suspend fun rejectLabel(reason: RejectReason): String = when (reason) {
        RejectReason.STALE_WRITE -> text(Res.string.wizard_reject_bucket_stale_write)
        RejectReason.CLOCK_SKEW -> text(Res.string.wizard_reject_bucket_clock_skew)
        RejectReason.QUOTA_EXCEEDED -> text(Res.string.wizard_reject_bucket_quota)
        RejectReason.NS_QUOTA_EXCEEDED -> text(Res.string.wizard_reject_bucket_ns_quota)
        RejectReason.KEY_TOO_LARGE -> text(Res.string.wizard_reject_bucket_key_too_large)
        RejectReason.KEY_LIMIT_REACHED -> text(Res.string.wizard_reject_bucket_key_limit)
        RejectReason.DEVICE_REVOKED -> text(Res.string.wizard_reject_bucket_device_revoked)
        RejectReason.OTHER -> text(Res.string.wizard_reject_bucket_other)
    }

    /**
     * The Wave-3 cross-account detection: the staged backup's origin ids (when
     * it carries them) vs the active session. Only a POSITIVE origin id that
     * differs warns — a blank origin (older export) stays silent.
     */
    private fun crossAccountDetected(
        backup: SettingsBackup,
        identity: com.raulshma.jellyplay.core.datastore.identity.ServerIdentity?,
    ): Boolean {
        if (identity == null) return false
        val originUser = backup.originUserId?.takeIf { it.isNotBlank() } ?: return false
        val originServer = backup.originServerId?.takeIf { it.isNotBlank() }
        val sameUser = originUser == identity.activeUserId
        val sameServer = originServer == null || originServer == identity.activeServerId
        return !(sameUser && sameServer)
    }

    private fun domainTitle(domain: String): String = when (domain) {
        "appearance" -> labels(Res.string.factory_reset_cat_appearance)
        "playback" -> labels(Res.string.factory_reset_cat_playback)
        "videoplayer" -> labels(Res.string.wizard_domain_videoplayer)
        "home" -> labels(Res.string.factory_reset_cat_home_discovery)
        "screensaver" -> labels(Res.string.factory_reset_cat_screensaver)
        "experimental" -> labels(Res.string.factory_reset_cat_experimental)
        PrefsDomainCatalog.OTHER_DOMAIN -> labels(Res.string.wizard_domain_other)
        else -> domain
    }

    private val domainTitleResources: List<StringResource>
        get() = listOf(
            Res.string.wizard_domain_videoplayer,
            Res.string.factory_reset_cat_home_discovery,
            Res.string.factory_reset_cat_screensaver,
            Res.string.factory_reset_cat_experimental,
            Res.string.wizard_domain_other,
        )

    private companion object {
        /** The export bundle decoder — lenient, only the profiles half is read. */
        val EXPORT_JSON = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    }
}
