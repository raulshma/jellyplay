package com.raulshma.jellyplay.feature.editor

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.EditableItemMetadata
import com.raulshma.jellyplay.core.model.EditorPerson
import com.raulshma.jellyplay.core.model.MediaDetail

/**
 * The metadata editor's editable form — ONE value whose properties are the
 * single declaration of the editor's metadata fields: [EditorUiState] embeds
 * a [MetadataEditSession] over this form rather than ~30 flat mirrors kept in
 * four-site lockstep.
 *
 * Field names mirror [EditableItemMetadata] — the outbound save type — wherever
 * an outbound counterpart exists. The one deliberate divergence:
 * `runtimeMinutes` is the string-edit representation of the outbound
 * `runtimeTicks` (users edit minutes; the server stores ticks) — [toEditable]
 * applies the `× 600_000_000` conversion, [fromDetail] the inverse.
 *
 * Every property defaults to the "empty" edit value so the unloaded state is
 * the zero form. [fromDetail] is the only inbound builder and [toEditable] the
 * only outbound projection; a NEW field added here participates in both maps
 * (by editing the mapping arms it owns) and in the dirty check automatically
 * ([MetadataEditSession] compares structurally — there is no per-field dirty
 * list to forget an entry on).
 */
@Immutable
data class EditableItemMetadataForm(
    val name: String = "",
    val originalTitle: String = "",
    val sortName: String = "",
    val overview: String = "",
    val tagline: String = "",
    val communityRating: String = "",
    val criticRating: String = "",
    val officialRating: String = "",
    val customRating: String = "",
    val productionYear: String = "",
    val premiereDate: String = "",
    val endDate: String = "",
    val runtimeMinutes: String = "",
    val indexNumber: String = "",
    val parentIndexNumber: String = "",
    val displayOrder: String = "",
    val status: String = "",
    val airTime: String = "",
    val airDays: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val people: List<EditorPerson> = emptyList(),
    val providerIds: Map<String, String> = emptyMap(),
    val taglines: List<String> = emptyList(),
    val productionLocations: List<String> = emptyList(),
    val lockData: Boolean = false,
    val lockedFields: List<String> = emptyList(),
    val preferredMetadataLanguage: String = "",
    val preferredMetadataCountryCode: String = "",
) {

    /**
     * The outbound projection submitted by the editor's save action: the same
     * blank→null / numeric-string parses the save mapping applies, plus the
     * runtimeMinutes→ticks conversion and the singular-tagline preference
     * (`taglines` derives from [tagline]; the list field carries the loaded
     * values for display/dirty only). [dateCreated] is not editable — it rides
     * along from the loaded detail unchanged.
     */
    fun toEditable(dateCreated: String? = null): EditableItemMetadata = EditableItemMetadata(
        name = name,
        originalTitle = originalTitle.ifBlank { null },
        sortName = sortName.ifBlank { null },
        overview = overview.ifBlank { null },
        tagline = tagline.ifBlank { null },
        genres = genres,
        tags = tags,
        studios = studios,
        communityRating = communityRating.toFloatOrNull(),
        criticRating = criticRating.toFloatOrNull(),
        officialRating = officialRating.ifBlank { null },
        customRating = customRating.ifBlank { null },
        productionYear = productionYear.toIntOrNull(),
        premiereDate = premiereDate.ifBlank { null },
        endDate = endDate.ifBlank { null },
        runtimeTicks = runtimeMinutes.toLongOrNull()?.let { it * 600_000_000 },
        indexNumber = indexNumber.toIntOrNull(),
        parentIndexNumber = parentIndexNumber.toIntOrNull(),
        displayOrder = displayOrder.ifBlank { null },
        status = status.ifBlank { null },
        airDays = airDays,
        airTime = airTime.ifBlank { null },
        people = people,
        providerIds = providerIds,
        lockData = lockData,
        lockedFields = lockedFields,
        preferredMetadataLanguage = preferredMetadataLanguage.ifBlank { null },
        preferredMetadataCountryCode = preferredMetadataCountryCode.ifBlank { null },
        taglines = if (tagline.isNotBlank()) listOf(tagline) else emptyList(),
        productionLocations = productionLocations,
        dateCreated = dateCreated,
    )

    companion object {
        /** Seeds the form from the loaded detail: nullable sources become
         *  empty edit strings, ticks become minutes, and the first tagline
         *  becomes the singular tagline field. `endDate` has no `MediaDetail`
         *  source and starts empty. */
        fun fromDetail(detail: MediaDetail): EditableItemMetadataForm {
            val item = detail.item
            return EditableItemMetadataForm(
                name = item.name,
                originalTitle = item.originalTitle ?: "",
                sortName = detail.sortName ?: "",
                overview = item.overview ?: "",
                tagline = detail.taglines.firstOrNull() ?: "",
                communityRating = item.communityRating?.toString() ?: "",
                criticRating = detail.criticRating?.toString() ?: "",
                officialRating = item.officialRating ?: "",
                customRating = detail.customRating ?: "",
                productionYear = item.year?.toString() ?: "",
                premiereDate = item.premiereDate ?: "",
                endDate = "",
                runtimeMinutes = item.runTimeTicks?.let { (it / 600_000_000).toString() } ?: "",
                indexNumber = item.indexNumber?.toString() ?: "",
                parentIndexNumber = item.seasonNumber?.toString() ?: "",
                displayOrder = detail.displayOrder ?: "",
                status = detail.status ?: "",
                airTime = detail.airTime ?: "",
                airDays = detail.airDays,
                genres = item.genres,
                tags = item.tags,
                studios = item.studios,
                people = detail.people.map { person ->
                    EditorPerson(
                        id = person.id,
                        name = person.name,
                        role = person.role,
                        type = person.type,
                        primaryImageTag = person.primaryImageTag,
                    )
                },
                providerIds = detail.providerIds,
                taglines = detail.taglines,
                productionLocations = detail.productionLocations,
                lockData = detail.lockData,
                lockedFields = detail.lockedFields,
                preferredMetadataLanguage = detail.preferredMetadataLanguage ?: "",
                preferredMetadataCountryCode = detail.preferredMetadataCountryCode ?: "",
            )
        }
    }
}

/**
 * One editing session over [EditableItemMetadataForm]: the live [value] plus
 * the [original] snapshot it is dirty-checked against. `isDirty` is plain
 * structural equality, so every form field participates automatically.
 *
 * Structural comparison detects the deltas a hand-ordered hash would miss
 * (both strictly widen correct detection, no UI-reachable behavior changes):
 * person `id`/`primaryImageTag` edits trip the dirty flag, and `providerIds`
 * key-set changes are detected order-insensitively. Editing before a load
 * completes does not show dirty either — the metadata tab is gated on
 * `isLoading`, so no edit control exists there.
 */
@Immutable
data class MetadataEditSession(
    val value: EditableItemMetadataForm = EditableItemMetadataForm(),
    val original: EditableItemMetadataForm = value,
) {
    /** Structural: the live form differs from its loaded original. */
    val isDirty: Boolean get() = value != original

    /** Applies [edit] to the live form (the typed replacement for the
     *  whole-UiState `updateField` mutator). */
    fun edit(edit: (EditableItemMetadataForm) -> EditableItemMetadataForm): MetadataEditSession =
        copy(value = edit(value))

    /** Resets the session over a freshly loaded form (clean). */
    fun loaded(form: EditableItemMetadataForm): MetadataEditSession =
        copy(value = form, original = form)

    /** Accepts the current form as the new clean baseline after a successful
     *  save. */
    fun saved(): MetadataEditSession = copy(original = value)
}
