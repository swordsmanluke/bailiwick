package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
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
        const val TAG = "IpfsDeserializer"
        const val ShortTimeout = 60L // Times are in seconds
        const val LongTimeout = 600L

        @JvmStatic
        fun <T> fromBailiwickFile(cipher: Encryptor, ipfs: IPFS, peerId: PeerId, filename: String, clazz: Class<T>): T? {
            // TODO: Remember and use the largest sequence we've seen for this peerId
            val record = ipfs.resolveName(peerId, 0, ShortTimeout)
            if(record == null) {
                Log.w(TAG, "Failed to locate IPNS record for $peerId")
                return null
            }

            val cid = ipfs.resolveNode(record.hash,"bw/${Bailiwick.VERSION}/$filename", ShortTimeout)
            if(cid != null) {
                return fromCid(cipher, ipfs, cid, clazz)
            }
            Log.w(TAG, "Failed to locate file $filename for $peerId. Trying links")

            ipfs.getLinks(record.hash, true, ShortTimeout)?.
            find {it.name == "bw"}?.
            let { bw ->
                Log.i(TAG, "Found Bailiwick dir @ ${bw.cid}")
                ipfs.getLinks(bw.cid, true, ShortTimeout)?.
                        find{ it.name == Bailiwick.VERSION}?.
                        let { ver ->
                            Log.i(TAG, "Found version dir @ ${ver.cid}")
                            val fileCid = ipfs.getLinks(ver.cid, true, ShortTimeout)?.find {
                                it.name == filename
                            }?.cid

                            if(fileCid != null) {
                                Log.i(TAG, "Success! Found $filename @ $fileCid!")
                                return fromCid(cipher, ipfs, fileCid, clazz)
                            }
                        }
            }

            Log.w(TAG, "Failure to locate $filename!")
            return null
        }

        @JvmStatic
        fun <T> fromCid(cipher: Encryptor, ipfs: IPFS, contentId: ContentId, clazz: Class<T>): T? {
            val data = ipfs.getData(contentId, LongTimeout)
            val rawJson = String(cipher.decrypt(data))
            return Gson().fromJson(rawJson, clazz)
        }
    }
}