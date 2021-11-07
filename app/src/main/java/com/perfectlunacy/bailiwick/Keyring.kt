package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase

class Keyring {
    companion object {
        @JvmStatic
        fun encryptorForPeer(db: BailiwickDatabase, peerId: PeerId, validator: (ByteArray) -> Boolean): Encryptor {
            val ciphers: MutableList<Encryptor> = db.keyDao().keysFor(peerId).mapNotNull { key ->
                key.secretKey?.let{ k -> AESEncryptor(k) }
            }.toMutableList()
            ciphers.add(NoopEncryptor())

            return MultiCipher(ciphers, validator)
        }

        @JvmStatic
        fun encryptorForCircle(db: BailiwickDatabase, circleId: Long): Encryptor {
            val curKey = db.keyDao().keysFor("circle:${circleId}").first()
            return AESEncryptor(curKey.secretKey!!)
        }
    }
}