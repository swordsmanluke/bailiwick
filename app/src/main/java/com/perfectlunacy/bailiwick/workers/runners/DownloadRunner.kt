package com.perfectlunacy.bailiwick.workers.runners

import android.util.Log
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ValidatorFactory
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.workers.runners.downloaders.FeedDownloader
import java.io.ByteArrayInputStream
import java.lang.Exception
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.pathString

class DownloadRunner(val filesDir: Path, val db: BailiwickDatabase, val ipfs: IPFS, val feedDownloader: FeedDownloader) {
    companion object {
        const val TAG = "DownloadRunner"
    }

    fun run() {
        // Wait for IPFS connection
        while(!ipfs.isConnected()) {
            Log.i(TAG, "Waiting for ipfs to connect")
            Thread.sleep(500)
        }

        Log.i(TAG, "Refreshing ${peers.count()} peers")
        peers.forEach { peerId ->
            Log.i(TAG, "Refreshing peer: $peerId")
            iteration(peerId)
        }
    }

    val peers: List<PeerId>
        get() = db.userDao().all().map { it.peerId }

    private fun iteration(peerId: PeerId) {
        if(downloadRequired(peerId)) {
            downloadIdentity(peerId)
            downloadManifest(peerId)
        } else {
            Log.i(TAG, "No download required - we're up to date")
        }
    }

    private fun downloadRequired(peerId: PeerId): Boolean {
        // If we found no record... we're done
        val record = ipfs.resolveName(peerId, db.sequenceDao(), IpfsDeserializer.ShortTimeout) ?: return false

        // If our current record is less than the new sequence - we need to update!
        val lastSeq = db.sequenceDao().find(peerId)?.sequence ?: -1
        return lastSeq < record.sequence
    }

    private fun downloadIdentity(peerId: PeerId) {
        if(db.identityDao().identitiesFor(peerId).isEmpty()) {
            Log.i(TAG, "Looking for identity for $peerId")
            val cipher = Keyring.encryptorForPeer(db.keyDao(), peerId, ValidatorFactory.jsonValidator())
            val pair = IpfsDeserializer.fromBailiwickFile(
                cipher,
                ipfs,
                peerId,
                db.sequenceDao(),
                "identity.json",
                IpfsIdentity::class.java)

            if (pair == null) {
                Log.w(TAG, "Could not download public identity for $peerId")
                return
            }

            val pubId = pair.first
            val cid = pair.second

            db.identityDao().insert(Identity(cid, peerId, pubId.name, pubId.profilePicCid))
        }

        val bw = Bailiwick.getInstance().bailiwick
        db.identityDao().identitiesFor(peerId).filterNot {
            Path(filesDir.pathString, "bwcache", it.profilePicCid?:"nonexist").toFile().exists()
        }.mapNotNull { it.profilePicCid }
        .forEach { cid ->
            try {
                while(!ipfs.isConnected()) {
                    Thread.sleep(500)
                }
                Log.i(TAG, "Downloading profile pic $cid")
                bw.storeFile(cid, ByteArrayInputStream(ipfs.getData(cid, 30)))
            }catch (e: Exception) {
                Log.e(TAG, "Failed to download profile pic for cid $cid", e)
            }
        }
    }

    private fun downloadManifest(peerId: PeerId) {
        val manifestCipher = Keyring.encryptorForPeer(db.keyDao(), peerId, ValidatorFactory.jsonValidator())
        val maniPair = IpfsDeserializer.fromBailiwickFile(
            manifestCipher,
            ipfs,
            peerId,
            db.sequenceDao(),
            "manifest.json",
            IpfsManifest::class.java)

        if (maniPair == null) {
            Log.w(TAG, "Failed to locate Manifest for peer $peerId")
            return
        }

        val validator = ValidatorFactory.jsonValidator()
        val cipher = Keyring.encryptorForPeer(db.keyDao(), peerId, validator)
        val rsa = MultiCipher(listOf(RsaWithAesEncryptor(ipfs.privateKey, ipfs.publicKey), cipher), validator)
        val manifest = maniPair.first
        Log.i(TAG, "Trying ${manifest.actions.count()} Actions")
        manifest.actions.forEach { actionCid ->
            downloadAction(actionCid, peerId, rsa)
        }

        Log.i(TAG, "Trying ${manifest.feeds.count()} feeds")
        manifest.feeds.forEach { feedCid ->
            feedDownloader.download(feedCid, peerId, cipher)
        }
    }

    private fun downloadAction(actionCid: ContentId, peerId: PeerId, cipher: Encryptor) {
        if(db.actionDao().actionExists(actionCid)) {
            Log.i(TAG, "Already processed Action $actionCid")
            return
        }
        val actionPair = IpfsDeserializer.fromCid(cipher, ipfs, actionCid, IpfsAction::class.java) ?: return
        val action = actionPair.first

        Log.i(TAG, "Downloaded action! Processing")

        when(ActionType.valueOf(action.type)) {
            ActionType.Delete -> TODO()
            ActionType.UpdateKey -> {
                Log.i(TAG, "Processing UpdateKey action from $peerId")
                Keyring.storeAesKey(db.keyDao(), peerId, action.metadata["key"]!!)
                val cipher = Keyring.encryptorForPeer(db.keyDao(), peerId, {_ -> true}) as MultiCipher
                Log.i(TAG, "I have ${cipher.ciphers.count()} keys for $peerId")
            }
            ActionType.Introduce -> TODO()
        }

        // Track that we have processed this Action
        db.actionDao().insert(Action(
            Calendar.getInstance().timeInMillis,
            actionCid,
            ipfs.peerID,
            action.type,
            "",
            true
        ))
    }
}