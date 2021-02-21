package com.perfectlunacy.bailiwick.controllers

import com.perfectlunacy.bailiwick.models.Introduction
import com.perfectlunacy.bailiwick.models.SubscriptionRequest
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.models.db.UserDao
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork

class Subscriptions(private val users: UserDao, private val dht: BailiwickNetwork) {

    fun add(cid: String, name: String) {
        users.insert(User(0, cid, name))
    }

    fun request(myName: String) : SubscriptionRequest {
        val cid = dht.myId()
        return SubscriptionRequest(myName, cid)
    }

    fun generateIntroductions(user1: User, user2: User) : List<Introduction> {
        return listOf(
            Introduction.introduce(user1, user2),
            Introduction.introduce(user2, user1))
    }
}