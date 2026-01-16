package com.perfectlunacy.bailiwick.util

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import java.util.regex.Pattern

/**
 * Utility class for parsing and handling @mentions in post text.
 */
object MentionParser {

    // Pattern to match @username (letters, numbers, underscores)
    private val MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]+)")

    // Color for mention links
    private const val MENTION_COLOR = 0xFF1976D2.toInt()  // Material Blue 700
    private const val UNKNOWN_MENTION_COLOR = 0xFF9E9E9E.toInt()  // Gray for unknown users

    /**
     * Data class representing a parsed mention.
     */
    data class Mention(
        val username: String,
        val startIndex: Int,
        val endIndex: Int
    )

    /**
     * Extracts all @mentions from text.
     * @param text The text to parse
     * @return List of Mention objects found in the text
     */
    fun extractMentions(text: String): List<Mention> {
        val mentions = mutableListOf<Mention>()
        val matcher = MENTION_PATTERN.matcher(text)

        while (matcher.find()) {
            val username = matcher.group(1) ?: continue
            mentions.add(Mention(
                username = username,
                startIndex = matcher.start(),
                endIndex = matcher.end()
            ))
        }

        return mentions
    }

    /**
     * Creates a SpannableString with clickable mention links.
     *
     * @param text The text containing mentions
     * @param knownUsernames Set of usernames that are known/connected
     * @param onMentionClick Callback when a mention is clicked, receives username
     * @return SpannableString with clickable spans for mentions
     */
    fun createSpannableWithMentions(
        text: String,
        knownUsernames: Set<String>,
        onMentionClick: (String) -> Unit
    ): SpannableString {
        val spannable = SpannableString(text)
        val mentions = extractMentions(text)

        for (mention in mentions) {
            val isKnown = knownUsernames.contains(mention.username.lowercase())
            val color = if (isKnown) MENTION_COLOR else UNKNOWN_MENTION_COLOR

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (isKnown) {
                        onMentionClick(mention.username)
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = color
                    ds.isUnderlineText = false
                }
            }

            spannable.setSpan(
                clickableSpan,
                mention.startIndex,
                mention.endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    /**
     * Filters a list of usernames based on a prefix for autocomplete.
     *
     * @param prefix The prefix to filter by (without @)
     * @param usernames List of all available usernames
     * @param limit Maximum number of suggestions to return
     * @return List of matching usernames
     */
    fun getAutocompleteSuggestions(
        prefix: String,
        usernames: List<String>,
        limit: Int = 5
    ): List<String> {
        if (prefix.isEmpty()) {
            return usernames.take(limit)
        }

        val lowercasePrefix = prefix.lowercase()
        return usernames
            .filter { it.lowercase().startsWith(lowercasePrefix) }
            .sortedBy { it.lowercase() }
            .take(limit)
    }

    /**
     * Checks if the cursor is currently in a mention context (after @).
     *
     * @param text The current text
     * @param cursorPosition The cursor position
     * @return Pair of (isInMention, currentPrefix) or null if not in mention context
     */
    fun getMentionContext(text: String, cursorPosition: Int): Pair<Int, String>? {
        if (cursorPosition <= 0 || cursorPosition > text.length) {
            return null
        }

        // Look backwards from cursor to find @
        var atIndex = -1
        for (i in (cursorPosition - 1) downTo 0) {
            val char = text[i]
            if (char == '@') {
                // Found @, check if it's at start or after whitespace
                if (i == 0 || text[i - 1].isWhitespace()) {
                    atIndex = i
                }
                break
            } else if (char.isWhitespace()) {
                // Hit whitespace before finding @, not in mention context
                break
            } else if (!char.isLetterOrDigit() && char != '_') {
                // Hit invalid character
                break
            }
        }

        if (atIndex == -1) {
            return null
        }

        // Extract the prefix after @
        val prefix = text.substring(atIndex + 1, cursorPosition)
        return Pair(atIndex, prefix)
    }
}
