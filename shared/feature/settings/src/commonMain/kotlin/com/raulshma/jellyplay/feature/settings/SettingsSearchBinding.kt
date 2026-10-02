package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import org.jetbrains.compose.resources.StringResource

/**
 * The feature-side half of the spec-derived settings-search catalog.
 * Core:datastore declares each catalog entry's SEMANTICS as a
 * [PreferenceSearchSpec] — id, keywords, category key, isAdvanced, platform
 * rule, route kind — as plain data next to the owning store; a binding
 * supplies the UI OBJECTS a [SettingsSearchItem] needs (resources, icon) and
 * nothing else, and the deep-link Route resolves from the spec's routeKind
 * through the domain's route map. Adding a searchable knob is then two edits:
 * the spec (datastore) and its binding here — no re-typed keywords, no
 * parallel isAdvanced/platform facts to drift, and no per-id Route copies to
 * keep aligned with the declared route kind.
 *
 * The ids in a binding table must stay identical to the spec declarations —
 * [toSettingsSearchItems] fails fast at catalog init on any drift (unbound
 * spec ids, stale bindings, a resource whose key no longer matches the
 * spec's declared key, or a route kind the domain's route map doesn't
 * carry).
 */
internal data class SettingsSearchBinding(
    val id: String,
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val categoryRes: StringResource,
    val icon: ImageVector,
    /**
     * Capability-derived platform tag override (the tags that cannot live
     * datastore-side because they read the runtime [SettingsCapabilities]
     * seam): null derives the tag from the spec's platform rule, a value
     * replaces it wholesale. Static rules belong on the spec, not here.
     */
    val platforms: Set<PlatformKind>? = null,
)

/**
 * Derives the catalog rows for a domain's spec declarations, in spec order:
 * semantics from each [PreferenceSearchSpec], presentation from its binding,
 * the Route from [routes] keyed by the spec's routeKind. Produced items are
 * field-for-field what the retired hand-written lists carried —
 * `SettingsSearchItem` itself is untouched (no field added).
 */
internal fun List<PreferenceSearchSpec>.toSettingsSearchItems(
    bindings: List<SettingsSearchBinding>,
    routes: Map<String, Route>,
): List<SettingsSearchItem> {
    val bindingOfId = bindings.associateBy { it.id }
    val specIds = map { it.id }.toSet()
    val unbound = specIds - bindingOfId.keys
    val stale = bindingOfId.keys - specIds
    require(unbound.isEmpty() && stale.isEmpty()) {
        "settings-search spec/binding drift: spec ids without a binding $unbound, bindings without a spec $stale"
    }
    return map { spec ->
        val binding = bindingOfId.getValue(spec.id)
        val route = requireNotNull(routes[spec.routeKind]) {
            "settings-search route kind '${spec.routeKind}' (spec '${spec.id}') has no Route in the domain's route map"
        }
        verifyResourceKey(spec.id, "title", spec.titleKey, binding.titleRes)
        verifyResourceKey(spec.id, "subtitle", spec.subtitleKey, binding.subtitleRes)
        verifyResourceKey(spec.id, "category", spec.categoryKey, binding.categoryRes)
        SettingsSearchItem(
            id = spec.id,
            titleRes = binding.titleRes,
            subtitleRes = binding.subtitleRes,
            categoryRes = binding.categoryRes,
            keywords = spec.keywords,
            route = route,
            icon = binding.icon,
            isAdvanced = spec.isAdvanced,
            platforms = spec.platformRule.platforms,
        )
    }
}

/**
 * The declared spec key and the bound resource must name the same string —
 * the ratchet that keeps the datastore-side key strings honest (they are the
 * declaration; the binding cannot silently swap a resource in under them).
 */
private fun verifyResourceKey(id: String, field: String, declaredKey: String, bound: StringResource) {
    require(bound.key == declaredKey) {
        "settings-search '$id' $field binding drifted: resource key '${bound.key}' != spec key '$declaredKey'"
    }
}

/**
 * The spec-derived projection of a CONVERTED declaration list: the record
 * list stays the ordered spine — it is the catalog order, the [rowTitle]/[rowIcon] screen-face source and the
 * [com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec]s' owner
 * registry — while each row's SEARCH faces derive either from its spec
 * entry (id, keywords, category, isAdvanced, platform rule, routeKind —
 * declared once at the owning store, bound to real resources/icons/routes
 * by [bindings]) or, for the documented RESIDUAL rows whose knobs live in
 * stores the spec machinery does not cover yet, from the record's own hand
 * faces ([SettingsRowRecord.toSearchItem]).
 *
 * Fail-fast at catalog init on any drift: duplicate spec entries, bindings
 * without a spec entry, spec entries without a binding, spec entries for
 * ids the spine doesn't declare, a converted record that still carries hand
 * search faces, a residual record missing them, a resource whose key no
 * longer matches the spec's declared key, a route kind the domain's route
 * map doesn't carry, a binding icon drifted from the record's screen icon,
 * or a category resource that disagrees with the list's shared category.
 */
internal fun List<SettingsRowRecord>.toSearchItems(
    specEntries: List<PreferenceSearchSpec>,
    bindings: List<SettingsSearchBinding>,
    routes: Map<String, Route>,
    categoryRes: StringResource,
): List<SettingsSearchItem> {
    val specOfId = specEntries.associateBy { it.id }
    require(specOfId.size == specEntries.size) {
        "settings-search duplicate spec entries: " +
            specEntries.groupBy { it.id }.filterValues { it.size > 1 }.keys
    }
    val bindingOfId = bindings.associateBy { it.id }
    val recordIds = map { it.id }.toSet()
    val specIds = specOfId.keys
    require(bindingOfId.keys.size == bindings.size) {
        "settings-search duplicate bindings: " +
            bindings.groupBy { it.id }.filterValues { it.size > 1 }.keys
    }
    val staleBindings = bindingOfId.keys - specIds
    require(staleBindings.isEmpty()) { "settings-search bindings without a spec entry: $staleBindings" }
    val unbound = specIds - bindingOfId.keys
    require(unbound.isEmpty()) { "settings-search spec entries without a binding: $unbound" }
    val unknownSpecs = specIds - recordIds
    require(unknownSpecs.isEmpty()) {
        "settings-search spec entries for ids the record list doesn't declare: $unknownSpecs"
    }
    return map { record ->
        val spec = specOfId[record.id]
        if (spec == null) {
            // Residual row: the knob lives in a store without spec machinery,
            // so the record still carries the full hand search faces.
            require(record.route != null) {
                "settings-search record \"${record.id}\" has neither a spec entry nor hand search faces"
            }
            record.toSearchItem(categoryRes)
        } else {
            // Converted row: screen-face-only record, semantics from the spec,
            // presentation from the binding.
            require(record.route == null && record.searchSubtitleRes == null && record.keywords.isEmpty()) {
                "settings-search record \"${record.id}\" is spec-declared but still carries hand search faces"
            }
            val binding = bindingOfId.getValue(record.id)
            val route = requireNotNull(routes[spec.routeKind]) {
                "settings-search route kind '${spec.routeKind}' (spec '${record.id}') has no Route in the domain's route map"
            }
            verifyResourceKey(record.id, "title", spec.titleKey, binding.titleRes)
            verifyResourceKey(record.id, "subtitle", spec.subtitleKey, binding.subtitleRes)
            verifyResourceKey(record.id, "category", spec.categoryKey, binding.categoryRes)
            require(binding.categoryRes == categoryRes) {
                "settings-search '${record.id}' binding category disagrees with the list's shared category"
            }
            require(binding.icon == record.icon) {
                "settings-search '${record.id}' binding icon drifted from the record's screen icon"
            }
            SettingsSearchItem(
                id = spec.id,
                titleRes = binding.titleRes,
                subtitleRes = binding.subtitleRes,
                categoryRes = categoryRes,
                keywords = spec.keywords,
                route = route,
                icon = binding.icon,
                isAdvanced = spec.isAdvanced,
                platforms = binding.platforms ?: spec.platformRule.platforms,
            )
        }
    }
}
