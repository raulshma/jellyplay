package com.raulshma.jellyplay.core.database

import androidx.room.TypeConverter
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import kotlinx.serialization.json.Json

object Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return json.encodeToString(value)
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return null
        val trimmed = value.trim()
        return if (trimmed.startsWith("[")) {
            runCatching { json.decodeFromString<List<String>>(trimmed) }.getOrNull()
        } else {
            trimmed.split(",").filter { it.isNotEmpty() }
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromIntList(value: List<Int>?): String? = value?.joinToString(",")

    @TypeConverter
    @JvmStatic
    fun toIntList(value: String?): List<Int>? {
        if (value.isNullOrEmpty()) return null
        val trimmed = value.trim()
        return if (trimmed.startsWith("[")) {
            runCatching {
                json.decodeFromString<List<Int>>(trimmed)
            }.getOrNull()
        } else {
            trimmed.split(",").mapNotNull { it.toIntOrNull() }
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromEnum(value: Enum<*>?): String? = value?.name

    // Home-screen SWR snapshot. These are plain encode/decode helpers, NOT
    // @TypeConverter methods: HomeSectionCacheEntity stores the payload as a
    // raw String column (payloadJson) and resolves the typed value itself,
    // so Room never needs to convert a HomeSectionsResult column. Annotating
    // them as @TypeConverter would be cargo-cult — Room would register a
    // converter for a type no column uses.
    @JvmStatic
    fun encodeHomeSectionsResult(value: HomeSectionsResult): String =
        json.encodeToString(value)

    @JvmStatic
    fun decodeHomeSectionsResult(value: String): HomeSectionsResult? =
        runCatching { json.decodeFromString<HomeSectionsResult>(value) }.getOrNull()
}
