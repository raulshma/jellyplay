package com.raulshma.jellyplay.core.database

import androidx.room.TypeConverter
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
}
