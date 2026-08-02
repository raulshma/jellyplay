package com.raulshma.jellyplay.core.network.subtitle

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import dagger.MapKey

/**
 * Hilt [MapKey] binding each [SubtitleProvider] implementation into
 * `Map<SubtitleProviderKind, SubtitleProvider>`, consumed by the fan-out
 * repository to dispatch search/download to the configured providers.
 *
 * `unwrapValue = true` (the default for a single-field annotation) maps the
 * enum directly to the map key without requiring the auto-value annotation
 * processor that `unwrapValue = false` would demand.
 */
@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class SubtitleProviderKey(val value: SubtitleProviderKind)
