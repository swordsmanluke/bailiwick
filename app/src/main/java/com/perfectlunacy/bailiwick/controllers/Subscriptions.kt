package com.perfectlunacy.bailiwick.controllers

import com.perfectlunacy.bailiwick.models.SubscriptionRequest
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.models.db.UserDao
import com.perfectlunacy.bailiwick.storage.DistHashTable

class Subscriptions(private val users: UserDao, private val dht: DistHashTable) {

    fun add(cid: String, name: String) {
        users.insert(User(0, cid, name))
    }

    fun request(myName: String) : SubscriptionRequest {
        val cid = dht.myId()
        return SubscriptionRequest(myName, cid)
    }

    // TODO: Create an Introduction class
    fun generateIntroductions(user1: User, user2: User) : List<String> {
        TODO("Generate 'intro' messages for each User")
    }
}