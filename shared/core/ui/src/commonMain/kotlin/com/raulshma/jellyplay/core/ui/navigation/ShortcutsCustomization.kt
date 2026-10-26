package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.model.ShortcutDefinition
import com.raulshma.jellyplay.core.model.ShortcutLimits
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.ShortcutTargetKind
import com.raulshma.jellyplay.core.model.shortcutTargetKey
import com.raulshma.jellyplay.core.ui.icons.ShortcutIconRegistry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The persisted-shortcuts resolution policy — the single hide/order/fallback
 * pipeline every shortcut surface applies, the [applyNavCustomization] twin
 * for user shortcuts. Pure over plain data: callers (home's dock, the shell's
 * overflow menu, the editor's previews) hand in the stored
 * [ShortcutDefinition]s + per-surface order and the catalog, and get back the
 * shortcuts to render, in order, with every stale/gated entry already gone.
 *
 * Policy, in application order:
 * 1. **Defaults**: an unconfigured user (never edited — empty definitions AND
 *    orders) gets the shipped layout ([DEFAULT_HEADER_TARGETS] /
 *    [DEFAULT_OVERFLOW_TARGETS]) — the pre-customization hard-coded menus.
 *    Reset returns here, and a stored config always means "the user chose".
 *    Defaults resolve as synthetic definitions (id = `default:<targetId>`),
 *    so the rest of the pipeline has exactly one path.
 * 2. **Order**: ids read in the surface's stored order; order ids without a
 *    definition, and definitions whose target has no [ShortcutCatalogEntry],
 *    are dropped — a config from an older/newer app version must never wedge
 *    a surface (stale definitions stay in storage until the editor prunes).
 * 3. **Gating**: admin-gated entries drop for non-admins; offline-hidden
 *    entries drop while offline — matching the nav bar's behavior, so a
 *    shortcut can never reach where navigation may not.
 * 4. **Clamp**: the surface capacity ([ShortcutLimits]), enforced again here
 *    as defense in depth behind the store's write clamp.
 * 5. **Guard rail** (overflow only): when Settings is unreachable elsewhere
 *    (hidden from the nav bar AND absent from the resolved menu), a pinned
 *    Settings entry is appended — full customization must never orphan the
 *    settings surface. The append respects the clamp: at capacity the
 *    menu's last entry yields its slot to the guard rail.
 */
fun resolveShortcuts(
    definitions: List<ShortcutDefinition>,
    headerOrder: List<String>,
    overflowOrder: List<String>,
    catalog: List<ShortcutCatalogEntry>,
    surface: ShortcutSurface,
    isAdmin: Boolean = false,
    isOffline: Boolean = false,
    settingsReachable: Boolean = true,
    maxSlots: Int = Int.MAX_VALUE,
): List<ResolvedShortcut> {
    val entryByTarget = catalog.associateBy { it.storageKey }
    val isUnconfigured = definitions.isEmpty() && headerOrder.isEmpty() && overflowOrder.isEmpty()

    val defaultTargets = defaultTargetsFor(surface)
    val definitionsById: Map<String, ShortcutDefinition> = if (isUnconfigured) {
        defaultTargets.associate { target ->
            target.id to ShortcutDefinition(
                id = target.id,
                kind = target.kind,
                targetId = target.targetId,
            )
        }
    } else {
        definitions.associateBy { it.id }
    }
    val order: List<String> = if (isUnconfigured) {
        defaultTargets.map { it.id }
    } else {
        when (surface) {
            ShortcutSurface.HEADER -> headerOrder
            ShortcutSurface.OVERFLOW -> overflowOrder
        }
    }

    val resolved = ArrayList<ResolvedShortcut>(minOf(order.size, maxSlots))
    val seenIds = HashSet<String>(order.size)
    for (id in order) {
        if (resolved.size >= maxSlots) break
        if (!seenIds.add(id)) continue
        val definition = definitionsById[id] ?: continue
        val entry = entryByTarget[definition.targetKey] ?: continue
        if (entry.requiresAdmin && !isAdmin) continue
        if (entry.hiddenOffline && isOffline) continue
        resolved += ResolvedShortcut(
            id = id,
            target = entry.target,
            icon = ShortcutIconRegistry.resolve(definition.iconId) ?: entry.defaultIcon,
            labelRes = entry.labelRes,
            labelOverride = definition.labelOverride?.takeIf { it.isNotBlank() },
        )
    }

    if (surface == ShortcutSurface.OVERFLOW) {
        val settingsReachableInMenu = resolved.any { it.targetsSettings }
        if (!settingsReachable && !settingsReachableInMenu) {
            entryByTarget[settingsStorageKey]?.let { settingsEntry ->
                // The rail still respects the clamp: at capacity the menu's
                // last entry yields its slot (the rail exists precisely for
                // the full-menu case — dropping it would orphan Settings).
                if (maxSlots > 0 && resolved.size >= maxSlots) {
                    resolved.removeAt(resolved.size - 1)
                }
                if (maxSlots > 0) {
                    resolved += ResolvedShortcut(
                        id = GUARD_RAIL_SETTINGS_ID,
                        target = settingsEntry.target,
                        icon = settingsEntry.defaultIcon,
                        labelRes = settingsEntry.labelRes,
                        labelOverride = null,
                    )
                }
            }
        }
    }
    return resolved
}

/**
 * The display label for a resolved shortcut — the user's override when set,
 * else the catalog's localized label. The one label resolution for every
 * shortcut surface (dock content descriptions, overflow pills, editor rows),
 * so an override can never apply on one surface and not another.
 */
@Composable
fun resolvedShortcutLabel(shortcut: ResolvedShortcut): String =
    shortcut.labelOverride ?: stringResource(shortcut.labelRes)

/** One shortcut resolved for rendering — placement decided, gating applied, icon/label final. */@Immutable
data class ResolvedShortcut(
    /**
     * The definition id this row came from, `default:<targetId>` for a
     * shipped-default entry, or [GUARD_RAIL_SETTINGS_ID] for the appended
     * Settings guard rail. Surfaces key their click handlers on the target,
     * not this; the id exists for editor round-trips and test assertions.
     */
    val id: String,
    val target: ShortcutCatalogTarget,
    val icon: ImageVector,
    val labelRes: StringResource,
    val labelOverride: String?,
) {
    /** Whether this shortcut launches the Settings screen (the guard-rail check). */
    val targetsSettings: Boolean
        get() = target is ShortcutCatalogTarget.Screen && target.route == Route.Settings
}

/**
 * The synthetic id of the appended Settings guard-rail entry — recognizable in
 * tests and editor round-trips, never a stored definition id.
 */
const val GUARD_RAIL_SETTINGS_ID = "guardrail:Settings"

// ----------------------------------------------------------------------
// Shipped defaults — the pre-customization overflow menu, expressed as
// target refs (kind + stable id). Screen ids ride the `Route.navKey`
// registry vocabulary; the ids must exist in the feature's catalog or the
// default entry silently drops (a registration gap shows up in
// ShortcutsCustomizationTest, not as a broken menu).
// ----------------------------------------------------------------------

/** A default-layout row: kind + target id, with the synthetic [ShortcutDefinition.id]. */
private data class DefaultTarget(val kind: ShortcutTargetKind, val targetId: String) {
    val id: String get() = "$DEFAULT_ID_PREFIX$targetId"
}

private const val DEFAULT_ID_PREFIX = "default:"

private val DEFAULT_HEADER_TARGETS: List<DefaultTarget> = emptyList()

private val DEFAULT_OVERFLOW_TARGETS: List<DefaultTarget> = listOf(
    DefaultTarget(ShortcutTargetKind.ACTION, ShortcutAction.SURPRISE_ME.id),
    DefaultTarget(ShortcutTargetKind.SCREEN, Route.SyncPlay.navKey),
    DefaultTarget(ShortcutTargetKind.SCREEN, Route.Downloads.navKey),
    DefaultTarget(ShortcutTargetKind.ACTION, ShortcutAction.TOGGLE_OFFLINE.id),
    DefaultTarget(ShortcutTargetKind.ACTION, ShortcutAction.PLAY_ON.id),
    DefaultTarget(ShortcutTargetKind.SCREEN, Route.Shortcuts.navKey),
    DefaultTarget(ShortcutTargetKind.SCREEN, Route.Settings.navKey),
)

private fun defaultTargetsFor(surface: ShortcutSurface): List<DefaultTarget> = when (surface) {
    ShortcutSurface.HEADER -> DEFAULT_HEADER_TARGETS
    ShortcutSurface.OVERFLOW -> DEFAULT_OVERFLOW_TARGETS
}

// ----------------------------------------------------------------------
// Catalog lookup keys — the kind-discriminated pair a definition is
// matched against the catalog by lives on the types themselves
// ([ShortcutCatalogEntry.storageKey] / [ShortcutDefinition.targetKey],
// both the shared `shortcutTargetKey` encoding); only the Settings key
// the guard rail looks up stays here.
// ----------------------------------------------------------------------

private val settingsStorageKey: String
    get() = shortcutTargetKey(ShortcutTargetKind.SCREEN, Route.Settings.navKey)
