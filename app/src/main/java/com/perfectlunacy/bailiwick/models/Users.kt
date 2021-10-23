package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.BailiwickImpl
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.security.PublicKey
import java.util.*

class Users(private val bw: Bailiwick) {

    companion object {
        @JvmStatic
        fun create(bw: Bailiwick): ContentId {
            return bw.store(UserFile(emptySet()), bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
        }
    }

    data class UserFile(val users: Set<UserRecord>)
    data class UserRecord(val peerId: PeerId, val publicKey: String) {
        override fun hashCode(): Int {
            return peerId.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UserRecord

            if (peerId != other.peerId) return false

            return true
        }
    }

    val userFile: UserFile?
        get() = bw.retrieve(bw.bailiwickAccount.usersCid, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE), UserFile::class.java)

    /***
     * Add a new User record to our data store
     *
     * Side Effect: Updates BailiwickAccount
     */
    fun add(peerId: PeerId, publicKey: PublicKey) {
        val newUsers = userFile?.users ?: mutableSetOf()
        val newUserFile = UserFile(
            newUsers.plus(UserRecord(peerId, Base64.getEncoder().encodeToString(publicKey.encoded)))
        )

        bw.bailiwickAccount.usersCid = bw.store(newUserFile, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
    }

}
