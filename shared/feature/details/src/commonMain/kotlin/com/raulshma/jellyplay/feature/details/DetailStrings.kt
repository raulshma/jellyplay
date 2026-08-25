package com.raulshma.jellyplay.feature.details

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * One-method localization seam for the detail feature's action helpers (and
 * the [DetailViewModel] body). Production wires it to compose-resources'
 * suspend [getString] (same resolver settings' LicensesViewModel uses); unit
 * tests supply a pure fake (e.g.
 * `DetailStrings { res, args -> "res#$res:${args.joinToString()}" }` or a
 * map-backed fake reconstructing a template), so no helper holds a platform
 * context and no test hand-stubs string resolution.
 *
 * KMP move note (from the legacy `context.getString(res, *args)` binding):
 * the resolver had to become `suspend` because compose-resources offers no
 * synchronous resolution outside composition on EITHER platform — the price
 * is that every call site must run inside a coroutine, which the module's
 * action helpers already did wholesale (`scope.launch` / suspend VM bodies);
 * the two non-suspend holdouts ([DetailViewModel.applyLoadState] /
 * `SmartPlayResult.toUiTarget`) became suspend at the move.
 */
internal interface DetailStrings {
    suspend fun get(res: StringResource, vararg args: Any?): String
}

/**
 * SAM-style factory (Kotlin has no `suspend fun interface`): call sites keep
 * the `DetailStrings { res, args -> ... }` lambda shape the tests use.
 */
internal fun DetailStrings(
    block: suspend (res: StringResource, args: Array<out Any?>) -> String,
): DetailStrings = object : DetailStrings {
    override suspend fun get(res: StringResource, vararg args: Any?): String = block(res, args)
}

/** Production binding: compose-resources' suspend resolver (commonMain). */
internal fun detailStrings(): DetailStrings = DetailStrings { res, args ->
    // Legacy `context.getString(res, *args)` rendered a null arg as the
    // literal "null" (String.format semantics); map explicitly so arity and
    // output stay identical through the non-null vararg resolver.
    getString(res, *args.map { it ?: "null" }.toTypedArray())
}
