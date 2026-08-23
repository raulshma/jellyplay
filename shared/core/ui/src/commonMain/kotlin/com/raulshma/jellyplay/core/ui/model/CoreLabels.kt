package com.raulshma.jellyplay.core.ui.model

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.core_clear_filters
import org.jetbrains.compose.resources.stringResource

/**
 * Composable labels for generic core:ui strings whose generated Res accessors
 * are module-internal (shared/feature:library pattern, same story as the
 * media-type names below: display strings resolve at the UI layer while the
 * `@StringRes Int` halves stay in the legacy :core:ui shim until every
 * consumer has migrated off resource ids, plan §Phase X).
 */

/** Localized "Cancel" (confirm-dialog dismiss label). */
@Composable
fun coreCancelLabel(): String = stringResource(Res.string.core_cancel)

/** Localized "Clear filters" (empty-state action label). */
@Composable
fun coreClearFiltersLabel(): String = stringResource(Res.string.core_clear_filters)
