package com.perfectlunacy.bailiwick.fragments.views

import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.models.db.User
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.commons.models.MessageContentType
import java.time.Instant
import java.util.*

/***
 * Wraps [Post] model for display in ChatKit
 */
class PostMessage(private val p: Post):IMessage, MessageContentType.Image {
    override fun getId(): String {
        return p.signature!!
    }

    override fun getText()= p.text

    override fun getUser(): IUser {
        return PostAuthor(p.author)
    }

    override fun getCreatedAt(): Date {
        return Date.from(Instant.ofEpochMilli(p.timestamp))
    }

    override fun getImageUrl(): String? {
        return p.files.firstOrNull()?.cid
    }

}