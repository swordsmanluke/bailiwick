package com.perfectlunacy.bailiwick.models.db

import androidx.room.TypeConverter
import java.util.Base64

/**
 * Shared Room TypeConverters for common types used across entities.
 */
class CommonConverters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? = value?.let {
        Base64.getEncoder().encodeToString(it)
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? = value?.let {
        try {
            Base64.getDecoder().decode(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString("\u0000")

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.split("\u0000")?.filter { it.isNotEmpty() }
}
