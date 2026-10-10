package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputDefaults

/**
 * One flattened editor row: the persisted [binding] plus the derived bits
 * the rows render — whether it drifted from the row Reset-all would restore
 * ([modified], drives the swipe-reset affordance) and whether it is a
 * user-captured row at all ([deletable], drives swipe-delete instead; a
 * captured plain key whose id coincides with a default row's is NOT
 * deletable — it resets like any default row).
 */
internal data class InputBindingsRowModel(
    val binding: PlayerBinding,
    val modified: Boolean,
    val deletable: Boolean,
)

/**
 * One editor section: the (filtered) rows plus the unfiltered totals the
 * collapsed header summary shows — "13 · 2 modified" must not lie just
 * because a search query is active.
 */
internal data class InputBindingsSectionModel(
    val rows: List<InputBindingsRowModel>,
    val totalCount: Int,
    val modifiedCount: Int,
)

/**
 * The four fixed sections the editor renders. Section identity is an enum,
 * not a bare string — the collapse state, the layout plan, and the
 * capture-collision flash all key off it, so a typo cannot drift between
 * them.
 */
internal enum class InputBindingsSection { TOUCH, MOUSE, KEYBOARD, TV }

/**
 * The whole editor's derived view model — the four fixed sections in their
 * display order. Pure data; assembled by [InputBindingsFilter.build].
 */
internal data class InputBindingsModel(
    val touch: InputBindingsSectionModel,
    val mouse: InputBindingsSectionModel,
    val keyboard: InputBindingsSectionModel,
    val tv: InputBindingsSectionModel,
) {
    val isEmpty: Boolean
        get() = touch.rows.isEmpty() && mouse.rows.isEmpty() &&
            keyboard.rows.isEmpty() && tv.rows.isEmpty()
}

/**
 * The binding editor's display policy — Compose-free and JVM-testable. It
 * partitions the persisted map into the four fixed sections (the screen's
 * old inline partition, moved here so tests pin it), derives the
 * modified/deletable flags against the parameterized default map (the SAME
 * flags a reset honors, so "unmodified" and "what reset restores" agree),
 * and applies the search query as a lowercase-contains over the per-row
 * corpus strings the caller pre-resolves in composition ([corpusById] —
 * labels are `@Composable` reads, the policy must stay label-free).
 *
 * A blank query is the unfiltered view; sections never drop rows for being
 * filtered empty in the model — the SCREEN hides an empty section's body
 * while a query is active, keeping the model dumb data.
 */
internal object InputBindingsFilter {

    fun matches(query: String, corpus: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return corpus.lowercase().contains(q.lowercase())
    }

    /**
     * The section a pattern renders in — the ONE partition the editor uses:
     * [build] groups the persisted rows by it, and the screen consults it
     * to un-collapse a section before a capture-collision flash. Touch is
     * the fallback arm: everything that is not wheel/key/D-pad lives there
     * (mirroring [PlayerInputDefaults.isTouchPattern]'s extent).
     */
    fun sectionOf(pattern: InputPattern): InputBindingsSection = when (pattern) {
        is InputPattern.Wheel -> InputBindingsSection.MOUSE
        is InputPattern.Key -> InputBindingsSection.KEYBOARD
        is InputPattern.DPad -> InputBindingsSection.TV
        else -> InputBindingsSection.TOUCH
    }

    fun build(
        bindings: List<PlayerBinding>,
        defaultsById: Map<String, PlayerBinding>,
        query: String,
        corpusById: Map<String, String>,
    ): InputBindingsModel {
        val bySection = bindings.groupBy { sectionOf(it.pattern) }
        return InputBindingsModel(
            touch = section(bySection[InputBindingsSection.TOUCH].orEmpty(), defaultsById, query, corpusById),
            mouse = section(bySection[InputBindingsSection.MOUSE].orEmpty(), defaultsById, query, corpusById),
            keyboard = section(bySection[InputBindingsSection.KEYBOARD].orEmpty(), defaultsById, query, corpusById),
            tv = section(bySection[InputBindingsSection.TV].orEmpty(), defaultsById, query, corpusById),
        )
    }

    private fun section(
        bindings: List<PlayerBinding>,
        defaultsById: Map<String, PlayerBinding>,
        query: String,
        corpusById: Map<String, String>,
    ): InputBindingsSectionModel {
        val rowModels = bindings.map { binding ->
            val default = defaultsById[binding.id]
            InputBindingsRowModel(
                binding = binding,
                modified = default == null ||
                    default.action != binding.action ||
                    default.enabled != binding.enabled,
                deletable = default == null,
            )
        }
        val filtered = rowModels.filter { row ->
            matches(query, corpusById[row.binding.id].orEmpty())
        }
        return InputBindingsSectionModel(
            rows = filtered,
            totalCount = rowModels.size,
            modifiedCount = rowModels.count { it.modified },
        )
    }
}
