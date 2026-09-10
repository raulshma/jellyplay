package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.library.LibraryItemsQuerySpec
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/**
 * The JVM half of the [LibraryItemsQuerySpec] seam: wire serial names → the
 * Jellyfin SDK enums `itemsApi.getItems` accepts. The shared commonMain
 * builders hold the per-endpoint request DECISIONS in wire vocabulary; these
 * resolvers (with the wasm client's raw query strings) are the two adapters
 * that prove the seam — one spec, two transports. Same serialName-lookup
 * regime as [LIST_ITEM_FIELDS]: resolve against prebuilt serialName maps,
 * failing fast on SDK drift, so a spec token the SDK no longer knows about
 * cannot silently degrade a query.
 */
private val BASE_ITEM_KINDS_BY_SERIAL_NAME = BaseItemKind.entries.associateBy { it.serialName }
private val ITEM_FILTERS_BY_SERIAL_NAME = ItemFilter.entries.associateBy { it.serialName }
private val ITEM_SORT_BY_SERIAL_NAME = ItemSortBy.entries.associateBy { it.serialName }
private val ITEM_FIELDS_BY_SERIAL_NAME = ItemFields.entries.associateBy { it.serialName }

/** includeItemTypes / excludeItemTypes: spec serial names → [BaseItemKind]s (null passes through). */
internal fun List<String>?.toBaseItemKinds(): List<BaseItemKind>? = mapNotNullToSerial("BaseItemKind", BASE_ITEM_KINDS_BY_SERIAL_NAME)

/** filters: spec ItemFilter serial names → [ItemFilter]s (null passes through). */
internal fun List<String>?.toItemFilters(): List<ItemFilter>? = mapNotNullToSerial("ItemFilter", ITEM_FILTERS_BY_SERIAL_NAME)

/** sortBy: spec ItemSortBy serial names → [ItemSortBy]s (null passes through). */
internal fun List<String>?.toItemSortBys(): List<ItemSortBy>? = mapNotNullToSerial("ItemSortBy", ITEM_SORT_BY_SERIAL_NAME)

/** fields: spec ItemFields serial names → [ItemFields]s (null passes through). */
internal fun List<String>?.toItemFieldsList(): List<ItemFields>? = mapNotNullToSerial("ItemFields", ITEM_FIELDS_BY_SERIAL_NAME)

private fun <E : Enum<E>> List<String>?.mapNotNullToSerial(
    enumLabel: String,
    bySerialName: Map<String, E>,
): List<E>? = this?.map { name ->
    requireNotNull(bySerialName[name]) { "$enumLabel has no serial name '$name' — SDK drift vs the shared query spec" }
}

/**
 * The spec's normalized sort order → the SDK [SortOrder] list getItems takes
 * (null = omit the parameter, matching the endpoints that send no sortOrder).
 */
internal fun Boolean?.toSortOrderList(): List<SortOrder>? = this?.let { descending ->
    listOf(if (descending) SortOrder.DESCENDING else SortOrder.ASCENDING)
}
