package com.perfectlunacy.bailiwick.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.ipfs.Identity
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.ContentId

class UserIdentity(val avatar: Bitmap?, val name: String, val cid: ContentId) {
    companion object {
        @JvmStatic
        fun fromIPFS(bw: Bailiwick, cipher: Encryptor, identityCid: ContentId): UserIdentity {
            // Public identities and avatars may not be encrypted. Use a multicipher to check
            val idCiphers = MultiCipher(listOf(cipher, NoopEncryptor())) { data ->
                try {
                    String(data)
                    true
                } catch (e: Exception) {
                    false
                }
            }

            val picCiphers = MultiCipher(listOf(cipher, NoopEncryptor())) { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size) != null
            }

            val identity = bw.retrieve(identityCid, idCiphers, Identity::class.java)!!
            val profilePicBytes = bw.download(identity.profilePicCid)

            return if (profilePicBytes == null) {
                UserIdentity(null, identity.name, identityCid)
            } else {
                val picBytes = picCiphers.decrypt(profilePicBytes)
                val avatar = BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size)

                UserIdentity(avatar, identity.name, identityCid)
            }
        }
    }
}