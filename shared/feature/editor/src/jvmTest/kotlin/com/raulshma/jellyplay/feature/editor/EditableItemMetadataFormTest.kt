package com.raulshma.jellyplay.feature.editor

import com.raulshma.jellyplay.core.model.EditorPerson
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The drift-class pins the editor's former flat-field shape could not express.
 * The old UiState/load-map/save-map/dirty-hash quartet had no enumeration
 * asserting that every field appears in EVERY site — a missed save-map entry
 * silently dropped an edit; a missed hash entry killed the dirty flag. These
 * tests enumerate [EditableItemMetadataForm]'s OWN properties so a NEW field
 * is auto-covered: adding one fails them until it is round-tripped and
 * dirty-tracked.
 *
 * Reflection here is Java reflection only (the `ResetCoverageGuard`
 * precedent — no kotlin-reflect dependency), and reads the data-class
 * machinery itself (property getters for names, `componentN()` + the primary
 * constructor for values), so the enumeration cannot drift from the
 * declaration.
 */
class EditableItemMetadataFormTest {

    /** Every source field non-default, so an unmapped field cannot hide
     *  behind a default value. Single tagline: the outbound `taglines`
     *  projection derives from the singular [EditableItemMetadataForm.tagline]. */
    private fun fullyPopulatedDetail(): MediaDetail = MediaDetail(
        item = MediaItem(
            id = "item-1",
            name = "Stolen Movie",
            originalTitle = "Film Volé",
            overview = "An overview.",
            mediaType = MediaType.SERIES,
            year = 1999,
            communityRating = 8.5f,
            officialRating = "PG-13",
            runTimeTicks = 90L * 600_000_000,
            premiereDate = "1999-07-02",
            genres = listOf("Drama"),
            tags = listOf("award-winner"),
            studios = listOf("Studio A"),
            indexNumber = 3,
            seasonNumber = 2,
        ),
        sortName = "Movie Stolen",
        customRating = "custom-rating",
        criticRating = 7.5f,
        taglines = listOf("A tagline"),
        productionLocations = listOf("Paris"),
        lockData = true,
        lockedFields = listOf("Overview"),
        status = "Ended",
        airDays = listOf("Monday"),
        airTime = "20:00",
        displayOrder = "SortName",
        preferredMetadataLanguage = "eng",
        preferredMetadataCountryCode = "USA",
        dateCreated = "2020-01-01",
        people = listOf(
            PersonInfo(id = "p1", name = "Alice", role = "Hero", type = "Actor", primaryImageTag = "tag-1"),
        ),
        providerIds = mapOf("tmdb" to "12345"),
    )

    // ── (a) round-trip: every form field survives fromDetail→toEditable ──

    @Test
    fun `fromDetail toEditable round-trips every form field`() {
        val detail = fullyPopulatedDetail()
        val form = EditableItemMetadataForm.fromDetail(detail)
        val editable = form.toEditable(detail.dateCreated)

        // The outbound expectation per form field, read from the DETAIL (the
        // source of truth — not from either map under test, so a field missed
        // in either map, or both, still mismatches). `runtimeMinutes` is the
        // one renamed outbound slot (runtimeTicks, with the minutes→ticks
        // conversion); `endDate` has no MediaDetail source.
        val expectedByField = mapOf(
            "name" to detail.item.name,
            "originalTitle" to detail.item.originalTitle,
            "sortName" to detail.sortName,
            "overview" to detail.item.overview,
            "tagline" to detail.taglines.firstOrNull(),
            "communityRating" to detail.item.communityRating,
            "criticRating" to detail.criticRating,
            "officialRating" to detail.item.officialRating,
            "customRating" to detail.customRating,
            "productionYear" to detail.item.year,
            "premiereDate" to detail.item.premiereDate,
            "endDate" to null,
            "runtimeMinutes" to detail.item.runTimeTicks,
            "indexNumber" to detail.item.indexNumber,
            "parentIndexNumber" to detail.item.seasonNumber,
            "displayOrder" to detail.displayOrder,
            "status" to detail.status,
            "airTime" to detail.airTime,
            "airDays" to detail.airDays,
            "genres" to detail.item.genres,
            "tags" to detail.item.tags,
            "studios" to detail.item.studios,
            "people" to detail.people.map {
                EditorPerson(it.id, it.name, it.role, it.type, it.primaryImageTag)
            },
            "providerIds" to detail.providerIds,
            "taglines" to detail.taglines,
            "productionLocations" to detail.productionLocations,
            "lockData" to detail.lockData,
            "lockedFields" to detail.lockedFields,
            "preferredMetadataLanguage" to detail.preferredMetadataLanguage,
            "preferredMetadataCountryCode" to detail.preferredMetadataCountryCode,
        )

        val outboundGetters = outboundPropertyGetters()
        for ((field, getter) in formPropertyGetters()) {
            // Key-presence check, not `?:` — a field's legitimate expected value
            // may BE null (endDate has no detail source).
            if (field !in expectedByField) fail(
                "form field '$field' has no round-trip expectation — wire it into " +
                    "expectedByField (a missing entry is exactly the silent-edit-drop " +
                    "drift this pin exists for)",
            )
            val expected = expectedByField[field]
            val outboundName = if (field == "runtimeMinutes") "runtimeTicks" else field
            val outboundGetter = outboundGetters[outboundName]
                ?: fail("no EditableItemMetadata.$outboundName property to receive form field '$field'")
            assertEquals(
                expected,
                outboundGetter.invoke(editable),
                "form field '$field' must survive fromDetail→toEditable",
            )
        }
    }

    // ── (b) dirty: mutating EVERY field individually trips isDirty ────────

    @Test
    fun `mutating every form field individually trips the dirty flag`() {
        val original = EditableItemMetadataForm.fromDetail(fullyPopulatedDetail())
        val session = MetadataEditSession(value = original, original = original)
        assertFalse(session.isDirty)

        for ((field, mutated) in formsWithOneFieldMutated(original)) {
            assertTrue(
                session.copy(value = mutated).isDirty,
                "mutating form field '$field' must trip isDirty",
            )
        }
    }

    // ── (c) an untouched form is not dirty ────────────────────────────────

    @Test
    fun `an untouched loaded form and the zero form are not dirty`() {
        val form = EditableItemMetadataForm.fromDetail(fullyPopulatedDetail())
        assertFalse(MetadataEditSession(value = form, original = form).isDirty)
        assertFalse(MetadataEditSession().isDirty)
    }

    // ── reflection enumeration (Java reflection only) ────────────────────

    /** Zero-arg property getters of [EditableItemMetadataForm], keyed by
     *  property name (`getOriginalTitle` → `originalTitle`). */
    private fun formPropertyGetters(): Map<String, Method> =
        propertyGetters(EditableItemMetadataForm::class.java)

    /** Zero-arg property getters of [EditableItemMetadata], keyed by name. */
    private fun outboundPropertyGetters(): Map<String, Method> =
        propertyGetters(com.raulshma.jellyplay.core.model.EditableItemMetadata::class.java)

    private fun propertyGetters(cls: Class<*>): Map<String, Method> =
        cls.declaredMethods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .associateBy { it.name.removePrefix("get").replaceFirstChar { c -> c.lowercase() } }

    /**
     * Every single-field mutation of [form], keyed by property name. Values
     * are read through the `componentN()` functions (constructor-argument
     * order) and rebuilt through the primary constructor with one argument
     * swapped, so the mutation set is exactly the declared field set. Property
     * NAMES come from the getters; the name→position pairing is resolved on a
     * sentinel form whose positional values are all distinct (Java reflection
     * exposes no constructor-parameter names).
     */
    private fun formsWithOneFieldMutated(form: EditableItemMetadataForm): Map<String, EditableItemMetadataForm> {
        val cls = EditableItemMetadataForm::class.java
        val ctor = cls.constructors
            .filter { !it.isSynthetic }
            .maxByOrNull { it.parameterCount }
            ?: fail("no primary constructor on ${cls.simpleName}")
        val componentGetters = cls.declaredMethods
            .filter { COMPONENT_NAME.matches(it.name) }
            .sortedBy { it.name.removePrefix("component").toInt() }
        val componentCount = componentGetters.size
        val getters = formPropertyGetters()
        check(componentCount == getters.size) {
            "componentN count ($componentCount) must match the property count (${getters.size})"
        }

        // Sentinel pairing: position-unique values identify which component
        // belongs to which named property.
        val sentinelArgs = ctor.parameterTypes.mapIndexed { i, type ->
            when (type) {
                String::class.java -> "v$i"
                Boolean::class.javaPrimitiveType -> i == 0
                List::class.java -> listOf("v$i")
                Map::class.java -> mapOf("k$i" to "v$i")
                else -> fail("unexpected form field type $type — extend the sentinel/mutators")
            }
        }.toTypedArray()
        val sentinel = ctor.newInstance(*sentinelArgs)
        val sentinelComponents = componentGetters.map { it.invoke(sentinel) }

        val currentArgs = componentGetters.map { it.invoke(form) }.toTypedArray()
        return buildMap {
            for ((name, getter) in getters) {
                val sentinelValue = getter.invoke(sentinel)
                val position = sentinelComponents.indexOfFirst { it == sentinelValue }
                if (position < 0) fail("cannot pair form property '$name' with a component position")
                val mutatedArgs = currentArgs.clone()
                mutatedArgs[position] = mutatedValue(getter.returnType, currentArgs[position])
                put(name, ctor.newInstance(*mutatedArgs) as EditableItemMetadataForm)
            }
        }
    }

    /** A structurally different value of the same erased type as [current]
     *  (append/duplicate/flip — the mutation only has to differ). */
    private fun mutatedValue(type: Class<*>, current: Any?): Any? = when {
        type == String::class.java -> current.toString() + "~mutated"
        type == Boolean::class.javaPrimitiveType -> !(current as Boolean)
        List::class.java.isAssignableFrom(type) -> {
            val list = current as List<*>
            list + list.last()
        }
        Map::class.java.isAssignableFrom(type) -> {
            val map = current as Map<*, *>
            map + ("mutated-key" to "mutated-value")
        }
        else -> fail("no mutation defined for form field type $type — extend the mutators")
    }

    private companion object {
        val COMPONENT_NAME = Regex("component\\d+")
    }
}
