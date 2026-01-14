package com.perfectlunacy.bailiwick.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages user preferences for content filtering.
 * Persists filter state across app restarts.
 */
class FilterPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bailiwick_filter_prefs"
        private const val KEY_FILTER_MODE = "filter_mode"
        private const val KEY_SELECTED_CIRCLE_ID = "selected_circle_id"
        private const val KEY_SELECTED_USER_ID = "selected_user_id"
        private const val KEY_SELECTED_TAG = "selected_tag"
    }

    /**
     * The current filter mode.
     */
    enum class FilterMode {
        ALL,        // Show all posts
        CIRCLE,     // Filter by circle
        PERSON,     // Filter by person
        TAG         // Filter by tag
    }

    /**
     * Get the current filter mode.
     */
    var filterMode: FilterMode
        get() {
            val ordinal = prefs.getInt(KEY_FILTER_MODE, FilterMode.ALL.ordinal)
            return FilterMode.values().getOrElse(ordinal) { FilterMode.ALL }
        }
        set(value) {
            prefs.edit { putInt(KEY_FILTER_MODE, value.ordinal) }
        }

    /**
     * Get/set the selected circle ID for circle filtering.
     * Returns -1 if no circle is selected.
     */
    var selectedCircleId: Long
        get() = prefs.getLong(KEY_SELECTED_CIRCLE_ID, -1L)
        set(value) {
            prefs.edit { putLong(KEY_SELECTED_CIRCLE_ID, value) }
        }

    /**
     * Get/set the selected user ID for person filtering.
     * Returns -1 if no user is selected.
     */
    var selectedUserId: Long
        get() = prefs.getLong(KEY_SELECTED_USER_ID, -1L)
        set(value) {
            prefs.edit { putLong(KEY_SELECTED_USER_ID, value) }
        }

    /**
     * Get/set the selected tag for tag filtering.
     * Returns null if no tag is selected.
     */
    var selectedTag: String?
        get() = prefs.getString(KEY_SELECTED_TAG, null)
        set(value) {
            prefs.edit { putString(KEY_SELECTED_TAG, value) }
        }

    /**
     * Clear all filter selections and reset to ALL mode.
     */
    fun clearFilters() {
        prefs.edit {
            putInt(KEY_FILTER_MODE, FilterMode.ALL.ordinal)
            remove(KEY_SELECTED_CIRCLE_ID)
            remove(KEY_SELECTED_USER_ID)
            remove(KEY_SELECTED_TAG)
        }
    }

    /**
     * Set circle filter.
     */
    fun setCircleFilter(circleId: Long) {
        filterMode = FilterMode.CIRCLE
        selectedCircleId = circleId
    }

    /**
     * Set person filter.
     */
    fun setPersonFilter(userId: Long) {
        filterMode = FilterMode.PERSON
        selectedUserId = userId
    }

    /**
     * Set tag filter.
     */
    fun setTagFilter(tag: String) {
        filterMode = FilterMode.TAG
        selectedTag = tag
    }
}
