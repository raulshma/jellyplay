package com.raulshma.jellyplay.core.model

/**
 * Wire-stable ids for the binding editor's user-captured keyboard rows.
 *
 * The default catalog ids ([PlayerInputDefaults]) are underscore-only —
 * `key.ctrl_bracket_left` — so a user-captured MODIFIER combo derives a
 * dot-separated id from its pattern instead: `key.ctrl_shift.m`. The two
 * schemes never collide, and the same capture reproduces the same id on a
 * reinstall or a restored backup. A captured PLAIN key deliberately lands
 * on the default scheme (`key.m`): when that key already has a default row
 * the ids coincide, which is exactly what makes re-capturing it edit that
 * row rather than mint a sibling.
 *
 * Modifier order is fixed ctrl → shift → alt, matching the default
 * catalog's compound rows. Derivation is pure and total over
 * [InputPattern.Key] — every combo gets an id, whether or not a default row
 * exists for it.
 */
object PlayerBindingIds {

    fun customKeyId(pattern: InputPattern.Key): String {
        val modifiers = listOfNotNull(
            "ctrl".takeIf { pattern.ctrl },
            "shift".takeIf { pattern.shift },
            "alt".takeIf { pattern.alt },
        )
        return buildString {
            append("key")
            if (modifiers.isNotEmpty()) {
                append('.')
                append(modifiers.joinToString("_"))
            }
            append('.')
            append(pattern.key.name.lowercase())
        }
    }
}
