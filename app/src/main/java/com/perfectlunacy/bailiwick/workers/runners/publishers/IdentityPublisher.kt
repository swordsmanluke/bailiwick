package com.perfectlunacy.bailiwick.workers.runners.publishers

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Identity
import com.perfectlunacy.bailiwick.models.db.IdentityDao
import com.perfectlunacy.bailiwick.models.ipfs.IpfsIdentity
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.workers.runners.PublishRunner

class IdentityPublisher(private val identityDao: IdentityDao, private val ipfs: IPFS) {

    fun publish(identity: Identity, cipher: Encryptor): ContentId {
        if (identity.cid != null) { return identity.cid!! } // ID is already published

        Log.i(PublishRunner.TAG, "Syncing identity ${identity.name}")
        val cid = IpfsIdentity(identity.name, identity.profilePicCid ?: "").toIpfs(cipher, ipfs)
        identityDao.updateCid(identity.id, cid)
        return cid
    }

    fun publish(identityId: Long, cipher: Encryptor): ContentId {
        val ident = identityDao.find(identityId)
        return publish(ident, cipher)
    }

}