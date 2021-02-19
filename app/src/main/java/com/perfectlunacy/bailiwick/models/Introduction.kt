package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.models.db.User

data class Introduction(val introFor: String, val introTo: String, val message: String) {
    companion object {
        fun introduce(u: User, to: User): Introduction {
            return Introduction(u.uid, to.uid, "${u.name}, meet ${to.name}. ${to.name}")
        }
    }
}