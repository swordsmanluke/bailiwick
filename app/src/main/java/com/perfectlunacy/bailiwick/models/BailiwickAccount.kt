package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.BailiwickImpl
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

class BailiwickAccount(val bw: Bailiwick) {
    companion object {
        @JvmStatic
        fun create(bw: Bailiwick, peerId: PeerId, keyFileCid: ContentId, subscriptionsCid: ContentId, usersCid: ContentId): ContentId {
            val newAcct = AccountRecord(peerId, keyFileCid, subscriptionsCid, usersCid)
            val newAcctCid = bw.store(newAcct, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
            return newAcctCid
        }
    }

    data class AccountRecord(
        val peerId: PeerId,
        val keyFileCid: ContentId,
        val subscriptionsCid: ContentId,
        val usersCid: ContentId
    )

    val account: AccountRecord?
        get() {
            val cid = bw.cidForPath("bw/${BailiwickImpl.VERSION}/account.json")
            return if (cid == null) {
                null
            } else {
                bw.retrieve(cid, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE), AccountRecord::class.java)
            }
        }

    val peerId: PeerId? = account?.peerId

    var keyFileCid
        get() = account!!.keyFileCid
        set(value) {
            val acct = account!!
            val newAcct = AccountRecord(acct.peerId, value, acct.subscriptionsCid, acct.usersCid)
            val newAcctCid = bw.store(newAcct, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
            bw.addBailiwickFile("account.json", newAcctCid)
        }

    var subscriptionsCid
        get() = account!!.subscriptionsCid
        set(value) {
            val acct = account!!
            val newAcct = AccountRecord(acct.peerId, acct.keyFileCid, value, acct.usersCid)
            val newAcctCid = bw.store(newAcct, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
            bw.addBailiwickFile("account.json", newAcctCid)
        }

    val users = Users(bw)

    var usersCid
        get() = account!!.usersCid
        set(value) {
            val acct = account!!
            val newAcct = AccountRecord(acct.peerId, acct.keyFileCid, acct.subscriptionsCid, value)
            val newAcctCid = bw.store(newAcct, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
            bw.addBailiwickFile("account.json", newAcctCid)
        }
}