package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One settings row's single feature-side declaration — presentation, ordering,
 * and capability in one home. This is the fused-catalog wave's row type: every
 * domain declares its rows as [SettingsRow]s, and the former trio is retired —
 * the `SettingsSearchBinding` entry (search presentation), the
 * `SettingsRowRecord` entry (screen face + ordering), and the `*Ids` holder
 * const (identity) collapsed into this one object, and the ordered row list IS
 * the spine (no separate ids constants: references use the row object; [id]
 * stays a `val` for the search/spec/deep-link string joins).
 *
 * The two-home shape this type realized: the datastore-side
 * [PreferenceSearchSpec] stays the SEMANTICS tier (id, keywords, category,
 * isAdvanced, platform rule, route kind — declared next to the owning store,
 * untouched here), and [SettingsRow] is the PRESENTATION tier (title/icon
 * faces, catalog order, admission gates). The retired declarations the
 * rollout deleted, per domain as its rows converted:
 *  - `SettingsRowRecord` and its registry entries (a converted row carries no
 *    record);
 *  - `SettingsSearchBinding`'s record-based overloads (a converted row IS its
 *    own binding — the spec-direct path the experimental screen keeps is the
 *    one survivor);
 *  - the `*Ids` holders (the row object replaces the constant);
 *  - the per-group admissions maps ([SettingsSearchItemGroup.admissions]
 *    derives from the rows' [gate]s).
 *
 * Field grammar (validated loudly at projection time):
 *  - SPEC-BACKED row (the knob's semantics live in a [PreferenceSearchSpec]):
 *    carry screen faces only — [titleRes]/[icon] (plus the search
 *    title/subtitle RESOURCES the spec's key strings bind to:
 *    [searchTitleRes] may default to the fold, [searchSubtitleRes] is
 *    required). [keywords]/[route]/[isAdvanced] must stay defaulted (the spec
 *    owns them); [platforms] carries only the capability-derived tag override
 *    (the runtime [SettingsCapabilities] seam facts a spec cannot declare).
 *  - RESIDUAL row (the knob lives in a store the spec tier does not cover
 *    yet): carry the full hand search faces — [searchSubtitleRes],
 *    [keywords], [route] (all required), [searchTitleRes] defaulting to the
 *    [titleRes] fold — until the store migrates.
 *  - [gate] — the row's admission (see [RowAdmission]): null derives the
 *    base gate from the row's EFFECTIVE advanced flag (the spec's for
 *    spec-backed rows, [isAdvanced] for residuals);
 *    [RowAdmission.ContentGated] declares the exception whose visibility
 *    rides content state the flags vocabulary does not carry.
 */
internal data class SettingsRow(
    /** The stable catalog id — the deep-link `highlightSettingId` target. */
    val id: String,
    /** The row's leading icon — screen face AND search face (one field, no binding drift to police). */
    val icon: ImageVector,
    /**
     * The settings-screen row's title resource (`settings_*`), or null for the
     * documented no-screen-title exceptions (hand-built pickers / enum-driven
     * rows — see the per-domain lists).
     */
    val titleRes: StringResource? = null,
    /**
     * The settings-screen row's STATIC subtitle resource, or null when the
     * subtitle is a dynamic value read (preference current-value, an on/off
     * resource pair chosen by state, a `displayName` enum) or absent. The row
     * is the ONE place a static subtitle is declared — emission sites read
     * [rowSubtitle] instead of hand-picking the resource.
     */
    val subtitleRes: StringResource? = null,
    /**
     * The search hit's title resource, or null where the hit restates the
     * row's screen title (the default-title fold resolves it to [titleRes]).
     */
    val searchTitleRes: StringResource? = null,
    /** The search hit's subtitle resource. Required for every searchable row. */
    val searchSubtitleRes: StringResource? = null,
    /** Fuzzy-match keywords — residual rows only (spec-backed rows take the spec's). */
    val keywords: List<String> = emptyList(),
    /** The deep-link route — residual rows only (spec-backed rows resolve the spec's routeKind). */
    val route: Route? = null,
    /** The advanced gate — residual rows only (spec-backed rows take the spec's flag). */
    val isAdvanced: Boolean = false,
    /**
     * Capability-derived platform tag override: null derives the tag from the
     * spec's platform rule, a value replaces it wholesale (the tags that
     * cannot live datastore-side because they read the runtime
     * [SettingsCapabilities] seam). Static rules belong on the spec.
     */
    val platforms: Set<PlatformKind>? = null,
    /**
     * The row's admission gate — the ONE declaration both the row-total
     * derivation and the screen's emission `if`s consult. Null derives the
     * base gate from the row's effective advanced flag; an explicit gate
     * states the exception (the shipped quirk shapes: always-on rows that
     * ride no gate despite an advanced tag, form-factor forks).
     */
    val gate: RowAdmission? = null,
)

/**
 * The spec entries a row list consumes, in spec order: the domain's spec
 * declarations filtered to the rows' ids. Requires every spec-backed row to
 * find its entry (a spec the store retired before the row did fails here, at
 * catalog init — the retired binding-table check, re-homed on the rows).
 * Callers pass the DOMAIN-wide spec list, so an entry another group of the
 * same domain claims is expected to be filtered out here — spec↔row totality
 * across a whole domain is pinned in FusedRowsRatchetTest, not per group.
 */
internal fun specEntriesFor(rows: List<SettingsRow>, specEntries: List<PreferenceSearchSpec>): List<PreferenceSearchSpec> {
    val rowIds = rows.map { it.id }.toSet()
    val matched = specEntries.filter { it.id in rowIds }
    val missing = rowIds - matched.map { it.id }.toSet()
    // Only the spec-backed rows must match; residuals legitimately have none.
    val residualIds = rows.filter { it.isResidual() }.map { it.id }.toSet()
    require(missing.isEmpty() || missing.all { it in residualIds }) {
        "settings rows without a spec entry: ${missing - residualIds}"
    }
    return matched
}

/** A residual row: the knob lives in a store the spec tier does not cover yet. */
private fun SettingsRow.isResidual(): Boolean = keywords.isNotEmpty() || route != null

/**
 * The fused catalog projection: each row's SEARCH faces derive either from
 * its spec entry (id, keywords, category, isAdvanced, platform rule,
 * routeKind — declared once at the owning store) or, for the residual rows,
 * from the row's own hand faces. Produced items are field-for-field what the
 * record+binding pair produced — `SettingsSearchItem` itself is untouched (no
 * field added), so the catalog, groups and search resolve are byte-identical.
 *
 * Fail-fast at catalog init on any drift: duplicate rows, duplicate spec
 * entries, a converted row that still carries hand search faces, a residual
 * row missing them, a resource whose key no longer matches the spec's
 * declared key, or a route kind the domain's route map doesn't carry.
 */
internal fun List<SettingsRow>.toSearchItems(
    specEntries: List<PreferenceSearchSpec>,
    routes: Map<String, Route>,
    categoryRes: StringResource,
): List<SettingsSearchItem> {
    val specOfId = specEntries.associateBy { it.id }
    require(specOfId.size == specEntries.size) {
        "settings-search duplicate spec entries: " +
            specEntries.groupBy { it.id }.filterValues { it.size > 1 }.keys
    }
    val rowIds = map { it.id }
    require(rowIds.size == rowIds.toSet().size) {
        "settings-search duplicate rows: " + groupBy { it.id }.filterValues { it.size > 1 }.keys
    }
    val unknownSpecs = specOfId.keys - rowIds.toSet()
    require(unknownSpecs.isEmpty()) {
        "settings-search spec entries for ids the row list doesn't declare: $unknownSpecs"
    }
    return map { row ->
        val spec = specOfId[row.id]
        if (spec == null) {
            // Residual row: the knob lives in a store without spec machinery,
            // so the row carries the full hand search faces.
            require(row.route != null) {
                "settings row \"${row.id}\" has neither a spec entry nor hand search faces"
            }
            SettingsSearchItem(
                id = row.id,
                titleRes = requireNotNull(row.searchTitleRes ?: row.titleRes) {
                    "settings row \"${row.id}\" declares neither a search title nor a screen title to default to"
                },
                subtitleRes = requireNotNull(row.searchSubtitleRes) {
                    "settings row \"${row.id}\" declares no search subtitle (residual rows must carry the full search faces)"
                },
                categoryRes = categoryRes,
                keywords = row.keywords,
                route = row.route,
                icon = row.icon,
                isAdvanced = row.isAdvanced,
                platforms = row.platforms ?: PlatformKind.entries.toSet(),
            )
        } else {
            // Converted row: semantics from the spec, presentation from the row.
            require(row.route == null && row.keywords.isEmpty() && !row.isAdvanced) {
                "settings row \"${row.id}\" is spec-declared but still carries hand search faces"
            }
            val route = requireNotNull(routes[spec.routeKind]) {
                "settings-search route kind '${spec.routeKind}' (spec '${row.id}') has no Route in the domain's route map"
            }
            verifyRowResourceKey(row.id, "title", spec.titleKey, row.searchTitleRes ?: row.titleRes)
            verifyRowResourceKey(row.id, "subtitle", spec.subtitleKey, row.searchSubtitleRes)
            SettingsSearchItem(
                id = spec.id,
                titleRes = requireNotNull(row.searchTitleRes ?: row.titleRes) {
                    "settings row \"${row.id}\" declares neither a search title nor a screen title to default to"
                },
                subtitleRes = requireNotNull(row.searchSubtitleRes) {
                    "settings row \"${row.id}\" declares no search subtitle"
                },
                categoryRes = categoryRes,
                keywords = spec.keywords,
                route = route,
                icon = row.icon,
                isAdvanced = spec.isAdvanced,
                platforms = row.platforms ?: spec.platformRule.platforms,
            )
        }
    }
}

/**
 * The declared spec key and the row's bound resource must name the same
 * string — the ratchet that keeps the datastore-side key strings honest (they
 * are the declaration; the row cannot silently swap a resource in under
 * them). The retired binding-era check, re-homed on the rows.
 */
private fun verifyRowResourceKey(id: String, field: String, declaredKey: String?, bound: StringResource?) {
    require(bound != null && bound.key == declaredKey) {
        "settings-search '$id' $field binding drifted: resource key '${bound?.key}' != spec key '$declaredKey'"
    }
}

/**
 * The group assembly of a converted declaration list: the fused projection of
 * the rows PLUS their admission derivation in one act — the group's
 * [SettingsSearchItemGroup.admissions] derive from the rows' [SettingsRow.gate]s
 * (null → the effective advanced flag's base gate;
 * [RowAdmission.ContentGated] rows declare no entry, exactly the retired
 * separate admissions maps' strict-count/true-emission semantics), so the
 * declaration you read is the single home of the group's order, faces and
 * gates. Decorating and aggregating stay the same act (see
 * [SettingsScreenGroups]).
 */
internal fun List<SettingsRow>.asRowGroup(
    id: String,
    specEntries: List<PreferenceSearchSpec>,
    routes: Map<String, Route>,
    categoryRes: StringResource,
): SettingsSearchItemGroup {
    val items = toSearchItems(specEntries, routes, categoryRes)
    val specAdvancedOfId = specEntries.associate { it.id to it.isAdvanced }
    val admissions = filter { it.gate !is RowAdmission.ContentGated }.associate { row ->
        val effectiveAdvanced = specAdvancedOfId[row.id] ?: row.isAdvanced
        row.id to (row.gate ?: if (effectiveAdvanced) RowAdmission.Advanced else RowAdmission.Always)
    }
    return SettingsSearchItemGroup(id, items, admissions)
}

/**
 * The screen-side consumer of a fused row: the ONE place the row's title
 * resource is resolved from. `title = rowTitle(SomeRows.X)` — the `settings_*`
 * resource itself is referenced from exactly one place in code: the row.
 */
@Composable
internal fun rowTitle(row: SettingsRow): String =
    stringResource(
        requireNotNull(row.titleRes) {
            "settings row \"${row.id}\" declares no screen title resource (documented no-screen-title exception)"
        },
    )

/**
 * The screen-side consumer of a fused row's STATIC subtitle: null when the
 * row declares none (dynamic value reads stay at their emission sites). The
 * static `settings_*` subtitle resource is referenced from exactly one place
 * in code: the row.
 */
@Composable
internal fun rowSubtitle(row: SettingsRow): String? =
    row.subtitleRes?.let { stringResource(it) }

/**
 * [rowTitle]'s icon twin for fused rows: the icon IS the row's field, so this
 * is a pure projection — kept as a call-site twin of [rowTitle] so a screen
 * row reads `rowIcon(SomeRows.X)` the same way records and bindings did.
 */
internal fun rowIcon(row: SettingsRow): ImageVector = row.icon
