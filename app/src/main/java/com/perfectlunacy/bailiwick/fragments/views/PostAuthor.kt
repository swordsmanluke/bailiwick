package com.perfectlunacy.bailiwick.fragments.views

import com.perfectlunacy.bailiwick.models.db.User

/***
 * Wraps [User] model for display in ChatKit
 */
class PostAuthor(private val u: User) {
    fun getId(): String {
        return u.uid
    }

    fun getName(): String {
        return u.name
    }

    fun getAvatar(): String {
        return ""
    }

}