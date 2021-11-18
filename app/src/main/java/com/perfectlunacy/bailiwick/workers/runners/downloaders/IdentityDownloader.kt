package com.perfectlunacy.bailiwick.workers.runners.downloaders

import android.util.Log
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.IpfsIdentity
import com.perfectlunacy.bailiwick.models.ipfs.IpfsPost
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.workers.runners.DownloadRunner
import java.lang.Exception

class IdentityDownloader(private val identityDao: IdentityDao, private val ipfs: IPFS) {
    fun download(cid: ContentId, peerId: PeerId, cipher: Encryptor): Identity {
        var identity = identityDao.findByCid(cid)
        if (identity == null) {
            val ipfsIdentity = IpfsDeserializer.fromCid(cipher, ipfs, cid, IpfsIdentity::class.java)!!.first

            identity = Identity(cid, peerId, ipfsIdentity.name, ipfsIdentity.profilePicCid)
            identity.id = identityDao.insert(identity)
        }

        return identity
    }
}