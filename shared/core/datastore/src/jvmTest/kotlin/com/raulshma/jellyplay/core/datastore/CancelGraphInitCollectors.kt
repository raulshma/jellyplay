package com.raulshma.jellyplay.core.datastore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Tears down a preference-graph test's scope: the graph's HomeDiscoveryStore
 * runs init collectors (Eagerly sharing, namespace migration, v3 upgrade
 * baseline) on active_user_id changes against the SHARED test DataStore —
 * cancel them so this class's graph never fires a fold into a later class's
 * test window.
 */
internal fun cancelGraphInitCollectors(scope: CoroutineScope) {
    scope.cancel()
}
