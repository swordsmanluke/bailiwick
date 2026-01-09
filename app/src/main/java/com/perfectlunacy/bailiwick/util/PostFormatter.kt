package com.perfectlunacy.bailiwick.util

import java.text.DateFormat
import java.util.Date

/**
 * Utility object for formatting Post data for display.
 * Used by data binding in layouts.
 */
object PostFormatter {
    /**
     * Format a timestamp for display.
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted date/time string
     */
    @JvmStatic
    fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        val formatter = DateFormat.getDateTimeInstance()
        return formatter.format(date)
    }
}
