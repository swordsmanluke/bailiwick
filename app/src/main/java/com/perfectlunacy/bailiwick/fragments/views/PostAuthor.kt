package com.perfectlunacy.bailiwick.fragments.views

import com.perfectlunacy.bailiwick.models.db.User
import com.stfalcon.chatkit.commons.models.IUser

/***
 * Wraps [User] model for display in ChatKit
 */
class PostAuthor(private val u: User): IUser {
    override fun getId(): String {
        return u.uid
    }

    override fun getName(): String {
        return u.name
    }

    override fun getAvatar(): String {
        return ""
    }

}