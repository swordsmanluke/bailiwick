package com.perfectlunacy.bailiwick.models

import android.util.Log
import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.BailiwickImpl
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

class Keyring(private val bw: Bailiwick) {

    companion object {
        const val TAG = "Keyring"
        @JvmStatic
        fun create(bw: Bailiwick, peerId: PeerId, everyoneCircleKey: String): ContentId {
            val secretKeys = mapOf(Pair("$peerId:everyone", listOf(everyoneCircleKey)), Pair(peerId, listOf(everyoneCircleKey)))
            Log.i(TAG, "keys: $secretKeys")
            val pubKeys = mapOf(Pair(peerId, Base64.getEncoder().encodeToString(bw.keyPair.public.encoded)))
            val record = KeyRecord(secretKeys, pubKeys)

            val cipher = bw.encryptorForKey(BailiwickImpl.USER_PRIVATE)

            return bw.store(record, cipher)
        }
    }

    private var _record: KeyRecord? = null
    private val record: KeyRecord?
        get() {
            if(_record == null) {
                val cipher = bw.encryptorForKey(BailiwickImpl.USER_PRIVATE)
                val kfCid = bw.bailiwickAccount.keyFileCid
                if(kfCid != null) {
                    _record = bw.retrieve(kfCid, cipher, KeyRecord::class.java)
                    _record!!.secretKeys.forEach { circle, keys ->
                        keys.forEach { k ->
                            addSecretKey(circle, k)
                        }
                    }

                    _record!!.publicKeys.forEach { peerId, key ->
                        addPublicKey(peerId, key)
                    }
                }
            }

            return _record
        }

    private val secretKeys = mutableMapOf<String, MutableList<String>>()
    private val publicKeys = mutableMapOf<String, String>()

    fun publicKeyFor(peerId: PeerId): PublicKey? {
        val keyBytes = publicKeys.get(peerId) ?: record?.publicKeys?.get(peerId) ?: return null

        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(keyBytes)))
    }

    fun secretKeys(id: String): List<String>? {
        return secretKeys.get(id) ?: record?.secretKeys?.get(id)
    }

    fun addSecretKey(circle: String, key: String){
        secretKeys.getOrPut(circle, { mutableListOf() }).add(key)
    }

    fun addPublicKey(peer: PeerId, pubkey: String) {
        publicKeys[peer] = pubkey
    }

    fun save() {
        val record = KeyRecord(secretKeys, publicKeys)
        bw.bailiwickAccount.keyFileCid = bw.store(record, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
    }

    data class KeyRecord(val secretKeys: Map<String, List<String>>, val publicKeys: Map<String, String>)


}