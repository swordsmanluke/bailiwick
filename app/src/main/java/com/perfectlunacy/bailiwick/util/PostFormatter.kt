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

    /**
     * Format a timestamp as relative time (e.g., "2 hours ago").
     * @param timestamp Unix timestamp in milliseconds
     * @return Relative time string
     */
    @JvmStatic
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> {
                val date = Date(timestamp)
                val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
                formatter.format(date)
            }
        }
    }
}
