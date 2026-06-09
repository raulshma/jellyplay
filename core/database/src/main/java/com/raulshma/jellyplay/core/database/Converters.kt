package com.raulshma.jellyplay.core.database

import androidx.room.TypeConverter
import org.json.JSONArray

object Converters {

    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return null
        val trimmed = value.trim()
        return if (trimmed.startsWith("[")) {
            runCatching {
                val array = JSONArray(trimmed)
                (0 until array.length()).map { array.getString(it) }
            }.getOrNull()
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
                val array = JSONArray(trimmed)
                (0 until array.length()).map { array.getInt(it) }
            }.getOrNull()
        } else {
            trimmed.split(",").mapNotNull { it.toIntOrNull() }
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromEnum(value: Enum<*>?): String? = value?.name
}
