package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSWrapper
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.workers.runners.publishers.CirclePublisher
import com.perfectlunacy.bailiwick.workers.runners.publishers.IdentityPublisher
import com.perfectlunacy.bailiwick.workers.runners.publishers.PostPublisher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

class PublishRunner(val context: Context, val db: BailiwickDatabase, val ipfs: IPFS) {
    companion object {
        const val TAG = "UploadRunner"
    }

    // TODO: LOTS of N+1 queries in here. Update our DAOs so we don't need so many.
    fun run() {
        // Wait for IPFS connection
        while(!ipfs.isConnected()) {
            Log.i(TAG, "Waiting for ipfs to connect")
            Thread.sleep(500)
        }

        val postsToSync = db.postDao().inNeedOfSync()
        val syncActions = db.actionDao().inNeedOfSync()

        if(syncActions.isNotEmpty()) {
            Log.i(TAG, "Uploading ${syncActions.count()} new action(s)")
            publishActions(syncActions)
        }

        // TODO: Separate publishing and providing _all_the_things_
        db.identityDao().identitiesFor(ipfs.peerID).mapNotNull {it.profilePicCid}
            .forEach { cid ->
                Log.i(TAG, "Providing profile pic $cid")
                ipfs.provide(cid, IpfsDeserializer.ShortTimeout)
            }

        db.circleDao().all().forEach { circle ->
            val cipher = Keyring.encryptorForCircle(db.keyDao(), circle.id)
            val postIds = db.circlePostDao().postsIn(circle.id)
            val postsForCircle = postsToSync.filter { postIds.contains(it.id) }

            CirclePublisher(
                db.circleDao(),
                db.identityDao(),
                IdentityPublisher(db.identityDao(), ipfs),
                PostPublisher(db.postDao(), db.postFileDao(), ipfs),
                ipfs
            ).publish(circle, postsForCircle, cipher)
        }

        // Now publish a new manifest if we changed anything
        if(postsToSync.isNotEmpty() || syncActions.isNotEmpty()) {
            Log.i(TAG, "Publishing a new manifest")
            publishNewManifest(false)
        } else {
            Log.i(TAG, "Nothing new to upload. Sleeping")
        }
    }

    fun refresh() {
        Log.i(TAG, "Refreshing existing manifest")
        publishNewManifest(true)
    }

    private fun publishActions(syncActions: List<Action>) {
        syncActions.forEach { action ->
            // Actions are encrypted with the key of their User
            val pubkey = Keyring.pubKeyFor(db.userDao(), action.toPeerId)
            val cipher = RsaWithAesEncryptor(null, pubkey)

            val metadata = mutableMapOf<String, String>()
            when(ActionType.valueOf(action.actionType)) {
                ActionType.Delete -> TODO()
                ActionType.UpdateKey -> { metadata.put("key", action.data) }
                ActionType.Introduce -> TODO()
            }

            val ipfsAction = IpfsAction(action.actionType, metadata)
            val actionCid = ipfsAction.toIpfs(cipher, ipfs)
            Log.i(TAG, "Uploaded Action to CID $actionCid")
            db.actionDao().updateCid(action.id, actionCid)
        }
        Log.i(TAG, "All actions stored")
    }

    private fun publishNewManifest(isRefresh: Boolean) {
        val feeds = db.circleDao().all().mapNotNull { it.cid }
        val actions = db.actionDao().all().mapNotNull { it.cid }
        val manCid = manifestCid(isRefresh, feeds, actions)
        var seq = (db.sequenceDao().find(ipfs.peerID)?.sequence ?: 0)
        var verCid = cidForDir("bw/${Bailiwick.VERSION}", seq)
        var bwCid = cidForDir("bw", seq)
        var rootCid = cidForDir("", seq)

        verCid = ipfs.addLinkToDir(verCid, "manifest.json", manCid)!!
        val idCid = db.identityDao().identitiesFor(ipfs.peerID).firstOrNull()?.cid
        if(idCid != null) {
            verCid = ipfs.addLinkToDir(verCid, "identity.json", idCid)!!
        }
        bwCid = ipfs.addLinkToDir(bwCid, Bailiwick.VERSION, verCid)!!
        rootCid = ipfs.addLinkToDir(rootCid, "bw", bwCid)!!

        val peerId = ipfs.peerID
        if(!isRefresh) {
            // We don't need to increment the sequence number during a refresh
            seq += 1
        }
        Log.i(TAG, "IPNS record sequence: $seq")
        db.ipnsCacheDao().insert(IpnsCache(peerId, "", rootCid, seq))
        db.ipnsCacheDao().insert(IpnsCache(peerId, "bw", bwCid, seq))
        db.ipnsCacheDao().insert(IpnsCache(peerId, "bw/${Bailiwick.VERSION}", verCid, seq))
        db.ipnsCacheDao().insert(IpnsCache(peerId, "bw/${Bailiwick.VERSION}/manifest.json", manCid, seq))

        ipfs.publishName(rootCid, seq, 30)
        if((db.manifestDao().currentSequence() ?: 0) < seq) {
            db.manifestDao().insert(Manifest(manCid, seq))
        }

        ipfs.provide(rootCid, 30)
        if(seq > 1) {
            db.sequenceDao().updateSequence(peerId, seq)
        } else {
            db.sequenceDao().insert(Sequence(peerId, seq))
        }
        val timeoutSeconds = 30L
        Log.i(IPFSWrapper.TAG, "Providing links from root")
        ipfs.getLinks(rootCid, true, timeoutSeconds).let { links ->
            if(links == null) {
                Log.e(IPFSWrapper.TAG,"Failed to locate links for root!")
                return@let
            }

            provideLinks(links, timeoutSeconds)
        }

        Log.i(TAG, "New manifest: $manCid")
    }

    private fun manifestCid(isRefresh: Boolean, feeds: List<ContentId>, actions: List<ContentId>): ContentId {
        return if(isRefresh){
            val sequence = db.manifestDao().currentSequence() ?: 0
            db.manifestDao().find(sequence)?.cid
                ?: IpfsManifest(feeds, actions).toIpfs(NoopEncryptor(), ipfs)
        } else {
            IpfsManifest(feeds, actions).toIpfs(NoopEncryptor(), ipfs)
        }
    }

    private fun provideLinks(links: List<Link>, timeoutSeconds: Long) {
        links.forEach { link ->
            GlobalScope.launch {
                ipfs.provide(link.cid, timeoutSeconds)
                Log.i(IPFSWrapper.TAG, "Providing link ${link.name}")
            }
            GlobalScope.launch {
                // Recursively add our children as well
                ipfs.getLinks(link.cid, true, timeoutSeconds).let { childLinks ->
                    if(childLinks == null || childLinks.isEmpty()) {
                        Log.w(TAG, "No links found for ${link.name}")
                        return@let
                    }
                    provideLinks(childLinks, timeoutSeconds)
                }
            }
        }
    }

    private fun cidForDir(path: String, seq: Long): ContentId {
        val paths = db.ipnsCacheDao().pathsForPeer(ipfs.peerID, seq)
        val cid = paths.filter { p -> p.path == path }.firstOrNull()?.cid ?: ipfs.createEmptyDir()
        return cid!!
    }
}