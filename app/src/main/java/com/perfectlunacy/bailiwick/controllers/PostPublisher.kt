package com.perfectlunacy.bailiwick.controllers

import com.perfectlunacy.bailiwick.models.Post
import com.perfectlunacy.bailiwick.storage.BailiwickNetwork

class PostPublisher(private val dht: BailiwickNetwork, private val peerId: String) {

    fun publish(post: Post) {
        // Store new Post
        val newKey = dht.store(post.toJson())
        // Update the list of all posts
        var allPosts = dht.retrieve_posts(peerId)
        // TODO: Parse all posts

        // Add new Post
        allPosts += "$newKey\n"

        // Update Post listing
        dht.publish_posts(allPosts)
    }
}