package com.raulshma.jellyplay.feature.details

/**
 * One-method localization seam for the detail feature's action helpers (and
 * the [DetailViewModel] body). Production wires it to
 * `context.getString(res, *args)`; unit tests supply a pure fake (e.g.
 * `DetailStrings { res, args -> "res#$res:${args.joinToString()}" }` or a
 * map-backed fake reconstructing a template), so no helper holds an Android
 * [android.content.Context] and no test hand-stubs `context.getString`.
 */
internal fun interface DetailStrings {
    fun get(res: Int, vararg args: Any?): String
}

/** Production binding: the app context adapts Android string resources. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object DetailStringsModule {
    @dagger.Provides
    internal fun provideDetailStrings(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    ): DetailStrings = DetailStrings { res, args -> context.getString(res, *args) }
}
