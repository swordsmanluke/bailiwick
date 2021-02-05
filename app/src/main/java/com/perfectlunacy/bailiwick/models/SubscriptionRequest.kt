package com.perfectlunacy.bailiwick.models

import com.google.gson.Gson

data class SubscriptionRequest(val name: String, val cid: String) {
    fun toJson(): String {
        val gson = Gson()
        val reqMap = HashMap<String, String>().also { it.put(name, cid) }
        return gson.toJson(reqMap)
    }
}