package com.raulshma.jellyplay.feature.admin.tasks

import com.raulshma.jellyplay.core.model.ScheduledTaskInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Unit tests for the pure [groupScheduledTasksByCategory] transform that backs the
 * `ScheduledTasksScreen` LazyColumn grouping. Mirrors the data-class/copy-semantics
 * style of `feature/home`'s `HomeUiStateTest` (plain JUnit4, no MockK).
 */
class ScheduledTaskGroupingTest {

    private fun task(
        id: String,
        name: String,
        category: String?,
    ) = ScheduledTaskInfo(id = id, name = name, category = category)

    @Test
    fun emptyList_returnsEmpty() {
        assertTrue(groupScheduledTasksByCategory(emptyList()).isEmpty())
    }

    @Test
    fun nullCategory_dropped() {
        val result = groupScheduledTasksByCategory(
            listOf(task("1", "Alpha", category = null)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun blankCategory_dropped() {
        val result = groupScheduledTasksByCategory(
            listOf(
                task("1", "Alpha", category = ""),
                task("2", "Bravo", category = "   "),
                task("3", "Charlie", category = "Maintenance"),
            ),
        )
        assertEquals(1, result.size)
        assertEquals("Maintenance", result.first().first)
    }

    @Test
    fun categories_sortedCaseInsensitively() {
        val result = groupScheduledTasksByCategory(
            listOf(
                task("1", "Z", category = "library"),
                task("2", "Z", category = "Maintenance"),
                task("3", "Z", category = "Admin"),
            ),
        )
        assertEquals(listOf("Admin", "library", "Maintenance"), result.map { it.first })
    }

    @Test
    fun tasksWithinCategory_sortedByLowercaseName() {
        val result = groupScheduledTasksByCategory(
            listOf(
                task("1", "Optimize", category = "Maintenance"),
                task("2", "clean cache", category = "Maintenance"),
                task("3", "Reindex", category = "Maintenance"),
            ),
        )
        val names = result.single().second.map { it.name }
        // Lowercase sort: "clean cache" < "Optimize" < "Reindex".
        assertEquals(listOf("clean cache", "Optimize", "Reindex"), names)
    }

    @Test
    fun tasks_partitionedIntoTheirOwnCategory() {
        val result = groupScheduledTasksByCategory(
            listOf(
                task("1", "A", category = "Library"),
                task("2", "B", category = "Admin"),
                task("3", "C", category = "Library"),
            ),
        )
        assertEquals(listOf("Admin", "Library"), result.map { it.first })
        val library = result.first { it.first == "Library" }.second.map { it.id }
        assertEquals(listOf("1", "3"), library)
    }

    @Test
    fun preservesAllTaskFields() {
        val input = listOf(
            ScheduledTaskInfo(id = "x", key = "k", name = "Name", category = "Cat"),
        )
        val output = groupScheduledTasksByCategory(input).single().second.single()
        assertEquals("x", output.id)
        assertEquals("k", output.key)
        assertEquals("Name", output.name)
        assertEquals("Cat", output.category)
    }
}
