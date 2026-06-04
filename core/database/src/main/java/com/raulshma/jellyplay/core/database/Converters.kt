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
        if (value == null) return null
        val array = JSONArray(value)
        return (0 until array.length()).map { array.getString(it) }
    }

    @TypeConverter
    @JvmStatic
    fun fromIntList(value: List<Int>?): String? {
        if (value == null) return null
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    @JvmStatic
    fun toIntList(value: String?): List<Int>? {
        if (value == null) return null
        val array = JSONArray(value)
        return (0 until array.length()).map { array.getInt(it) }
    }

    @TypeConverter
    @JvmStatic
    fun fromEnum(value: Enum<*>?): String? = value?.name
}
