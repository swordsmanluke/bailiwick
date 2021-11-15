package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.SequenceDao
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.lang.Exception

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
        fun <T> fromBailiwickFile(cipher: Encryptor, ipfs: IPFS, peerId: PeerId, sequenceDao: SequenceDao, filename: String, clazz: Class<T>): Pair<T, ContentId>? {
            try {
                val record = ipfs.resolveName(peerId, sequenceDao, ShortTimeout)
                if (record == null) {
                    Log.w(TAG, "Failed to locate IPNS record for $peerId")
                    return null
                }

                val cid = ipfs.resolveBailiwickFile(record.hash, filename, LongTimeout)
                if (cid != null) {
                    return fromCid(cipher, ipfs, cid, clazz)
                }
                Log.w(TAG, "Failed to locate file $filename for $peerId")
                return null
            } catch(e: Exception) {
                Log.e(TAG, "Failed to find bailiwick file $filename", e)
                return null
            }
        }

        @JvmStatic
        fun <T> fromCid(cipher: Encryptor, ipfs: IPFS, contentId: ContentId, clazz: Class<T>): Pair<T, ContentId>? {
            var rawJson = ""
            try{
                val data = ipfs.getData(contentId, ShortTimeout)
                rawJson = String(cipher.decrypt(data))

                if(rawJson.isBlank() && data.size > 2) {
                    Log.e(TAG, "Failed to decrypt data for ${clazz.simpleName} : $contentId")
                    return null
                }

                val retVal = Gson().fromJson(rawJson, clazz)
                if(retVal == null) {
                    Log.e(TAG, "Failed to convert rawJson to ${clazz.simpleName}. Json: $rawJson")
                    return null
                }

                return Pair(retVal, contentId)
            } catch(e: Exception) {
                Log.e(TAG, "Failed to parse JSON for $clazz\n'$rawJson'", e)
                return null
            }
        }
    }
}