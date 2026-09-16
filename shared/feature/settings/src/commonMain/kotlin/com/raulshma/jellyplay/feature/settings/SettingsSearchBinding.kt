package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import org.jetbrains.compose.resources.StringResource

/**
 * The feature-side half of the spec-derived settings-search catalog (Stage A
 * pilot). Core:datastore declares each catalog entry's SEMANTICS as a
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
