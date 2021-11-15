package com.perfectlunacy.bailiwick

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.ciphers.*
import com.perfectlunacy.bailiwick.models.db.Key
import com.perfectlunacy.bailiwick.models.db.KeyDao
import com.perfectlunacy.bailiwick.models.db.KeyType
import com.perfectlunacy.bailiwick.models.db.UserDao
import com.perfectlunacy.bailiwick.storage.PeerId
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.Path
import kotlin.io.path.pathString

class Keyring {
    companion object {
        const val TAG = "Keyring"

        @JvmStatic
        fun encryptorForPeer(keyDao: KeyDao, peerId: PeerId, validator: (ByteArray) -> Boolean): Encryptor {
            val ciphers: MutableList<Encryptor> = keyDao.keysFor(peerId).mapNotNull { key ->
                key.secretKey?.let{ k -> AESEncryptor(k) }
            }.toMutableList()
            ciphers.add(NoopEncryptor())

            return MultiCipher(ciphers, validator)
        }

        @JvmStatic
        fun encryptorForCircle(keyDao: KeyDao, circleId: Long): Encryptor {
            val curKey = keyDao.keysFor("circle:$circleId").first()
            return AESEncryptor(curKey.secretKey!!)
        }

        @JvmStatic
        fun keyForCircle(keyDao: KeyDao, filesDir: Path, circleId: Int, cipher: Encryptor): ByteArray {
            val alias = keyDao.keysFor("circle:$circleId").last().alias

            val f = Path(filesDir.pathString, "bwcache", "keystore.json").toFile()
            val input = BufferedInputStream(FileInputStream(f))
            val rawJson = String(cipher.decrypt(input.readBytes()))
            val store = Gson().fromJson(rawJson, KeyFile::class.java)

            val keyRec = store.keys.find { it.alias == alias }
            return Base64.getDecoder().decode(keyRec!!.encKey)
        }

        @JvmStatic
        fun generateAesKey(keyDao: KeyDao, filesDir: Path, circleId: Long, cipher: Encryptor): String {
            val alias = UUID.randomUUID().toString()
            val keyGen = KeyGenerator.getInstance("AES")
            val key = keyGen.generateKey()

            val keyFile = loadKeyFile(filesDir, cipher)
            keyFile.keys.add(KeyStoreRecord(alias, Base64.getEncoder().encodeToString(key.encoded), "Secret"))
            saveKeyFile(filesDir, keyFile, cipher)

            // Add key to AndroidKeyStore
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            val entry = KeyStore.SecretKeyEntry(key)
            val protection = KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build()
            ks.setEntry(alias, entry, protection)

            keyDao.insert(Key("circle:$circleId", alias, "AES", KeyType.Secret))

            return alias
        }

        @JvmStatic
        fun storeAesKey(keyDao: KeyDao, peerId: PeerId, key: String) {
            val pk = SecretKeySpec(Base64.getDecoder().decode(key), "AES")
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            val entry = KeyStore.SecretKeyEntry(pk)
            val alias = UUID.randomUUID().toString()
            val protection = KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build()
            ks.setEntry(alias, entry, protection)

            keyDao.insert(Key(peerId, alias, "AES", KeyType.Secret))
        }

        @JvmStatic
        fun storePubKey(filesDir: Path, peerId: PeerId, publicKey: String, cipher: Encryptor) {
            val keyFile = loadKeyFile(filesDir, cipher)
            keyFile.keys.find { it.alias == "$peerId:public" }.also {
                if(it != null) {
                    Log.w(TAG,"We already have a public key for peer $peerId")
                    return
                }
            }

            keyFile.keys.add(KeyStoreRecord("$peerId:public", publicKey, KeyType.Public.toString()))
            saveKeyFile(filesDir, keyFile, cipher)
        }

        private fun loadKeyFile(filesDir: Path, cipher: Encryptor): KeyFile {
            val f = Path(filesDir.pathString, "bwcache", "keystore.json").toFile()
            val rawJson = if (f.exists()) {
                val input = BufferedInputStream(FileInputStream(f))
                val readString = String(cipher.decrypt(input.readBytes()))
                if(readString.isBlank()) { "{keys: []}" } else { readString }
            } else {
                "{keys: []}"
            }

            return Gson().fromJson(rawJson, KeyFile::class.java)
        }

        private fun saveKeyFile(filesDir: Path, keyFile: KeyFile, cipher: Encryptor) {
            val f = Path(filesDir.pathString, "bwcache", "keystore.json").toFile()
            f.parentFile?.mkdirs()

            val encryptedFile = cipher.encrypt(Gson().toJson(keyFile).toByteArray())
            BufferedOutputStream(FileOutputStream(f)).use { file ->
                file.write(encryptedFile)
            }
        }

        fun pubKeyFor(userDao: UserDao, peerId: PeerId): PublicKey? {
            val key = userDao.publicKeyFor(peerId) ?: return null
            val publicKeyData = Base64.getDecoder().decode(key)
            return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyData))
        }

    }
    data class KeyFile(val keys: MutableList<KeyStoreRecord>)
    data class KeyStoreRecord(val alias: String, val encKey: String, val type: String)
}

