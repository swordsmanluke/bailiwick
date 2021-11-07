package com.perfectlunacy.bailiwick.storage.ipfs

import com.google.gson.Gson
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId

abstract class IpfsSerializable {
    fun toIpfs(cipher: Encryptor, ipfs: IPFS): ContentId {
        val data = cipher.encrypt(Gson().toJson(this).toByteArray())
        return ipfs.storeData(data)
    }
}

class IpfsDeserializer {
    companion object {
        @JvmStatic
        fun <T> fromBailiwickFile(cipher: Encryptor, ipfs: IPFS, peerId: PeerId, filename: String, clazz: Class<T>): T? {
            val record = ipfs.resolveName(peerId, 0, 3000) ?: return null
            val cid = ipfs.resolveNode(record.hash,"bw/${Bailiwick.VERSION}/$filename", 1000) ?: return null

            return fromCid(cipher, ipfs, cid, clazz)
        }

        @JvmStatic
        fun <T> fromCid(cipher: Encryptor, ipfs: IPFS, contentId: ContentId, clazz: Class<T>): T? {
            val data = ipfs.getData(contentId, 1000)
            val rawJson = String(cipher.decrypt(data))
            return Gson().fromJson(rawJson, clazz)
        }
    }
}