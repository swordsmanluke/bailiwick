package com.perfectlunacy.bailiwick.fragments.views

import com.perfectlunacy.bailiwick.models.Post
import java.time.Instant
import java.util.*

/***
 * Wraps [Post] model for display
 */
class PostMessage(private val p: Post) {
    fun getId(): String {
        return p.signature!!
    }

    fun text()= p.text

    fun postAuthor(): PostAuthor {
        return PostAuthor(p.author)
    }

    fun createdAt(): Date {
        return Date.from(Instant.ofEpochMilli(p.timestamp))
    }

    fun imageUrl(): String? {
        return p.files.firstOrNull()?.path
    }

}