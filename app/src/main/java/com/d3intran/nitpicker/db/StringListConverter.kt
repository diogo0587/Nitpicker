package com.d3intran.nitpicker.db

import androidx.room.TypeConverter

/**
 * Room 类型转换器，用于将 List<String> 标签列表存入数据库（以逗号分隔的字符串形式）。
 */
class StringListConverter {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",").filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toString(list: List<String>?): String {
        if (list == null) return ""
        return list.joinToString(",")
    }
}
