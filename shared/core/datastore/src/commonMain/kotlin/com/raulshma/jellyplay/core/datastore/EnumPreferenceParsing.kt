package com.raulshma.jellyplay.core.datastore

/**
 * The one persisted-string → enum parse behind every preference-store enum
 * read: returns the constant whose [Enum.name] matches the stored string, or
 * null when the string is null (key absent) or names no constant (corrupt
 * value). Never throws, so callers supply the documented default with `?:`
 * instead of a per-site try/catch.
 *
 * This is the repo-wide parse seam: core/data repositories parse persisted
 * enum strings through it too, so a corrupt stored value degrades to the
 * documented default everywhere instead of throwing.
 */
public inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }
