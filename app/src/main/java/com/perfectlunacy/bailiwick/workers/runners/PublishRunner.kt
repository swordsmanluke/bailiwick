package com.perfectlunacy.bailiwick.workers.runners

import android.content.Context
import android.util.Log
import com.perfectlunacy.bailiwick.Keyring
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.ciphers.RsaWithAesEncryptor
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IpfsDeserializer
import com.perfectlunacy.bailiwick.workers.runners.publishers.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

class PublishRunner(val context: Context, val db: BailiwickDatabase, val ipfs: IPFS) {
    companion object {
        const val TAG = "PublishRunner"
    }

    // TODO: LOTS of N+1 queries in here. Update our DAOs so we don't need so many.
    fun run() {
        // Wait for IPFS connection
        while (!ipfs.isConnected()) {
            Log.i(TAG, "Waiting for ipfs to connect")
            Thread.sleep(500)
        }

        val postsToSync = db.postDao().inNeedOfSync()
        val actionsToSync = db.actionDao().inNeedOfSync()

        if (actionsToSync.isNotEmpty()) {
            Log.i(TAG, "Uploading ${actionsToSync.count()} new action(s)")
            publishActions(actionsToSync)
        }

        // TODO: Separate publishing and providing _all_the_things_
        db.identityDao().identitiesFor(ipfs.peerID).mapNotNull { it.profilePicCid }
            .forEach { cid ->
                Log.i(TAG, "Providing profile pic $cid")
                ipfs.provide(cid, IpfsDeserializer.ShortTimeout)
            }

        val idPub = IdentityPublisher(db.identityDao(), ipfs)
        val postPub = PostPublisher(db.postDao(), db.postFileDao(), ipfs)
        val circPub = CirclePublisher(db.circleDao(), ipfs)

        db.circleDao().all().forEach { circle ->
            val cipher = Keyring.encryptorForCircle(db.keyDao(), circle.id)
            val postIds = db.circlePostDao().postsIn(circle.id)
            val postsForCircle = postsToSync.filter { postIds.contains(it.id) }

            idPub.publish(circle.identityId, cipher)
            if (postsForCircle.isNotEmpty()) {
                db.circleDao().clearCid(circle.id)
                postsForCircle.forEach { post -> postPub.publish(post, cipher) }

                // TODO: All of this lives in this 'if' block only because 'postsForCircle ! empty'
                //       is the only check we currently need to determine if a circle needs updating.
                //       Later, we'll need to check if there are any newly synced Interactions or
                //       Actions. Possibly, we should just check if the Circle's DB CID is null.
                val idCid = db.identityDao().find(circle.identityId).cid
                if (idCid != null) {
                    // TODO: AAAAH! N+1 Query - add a mass query for Posts
                    val postCids = db.circlePostDao().postsIn(circle.id).mapNotNull {
                        db.postDao().find(it).cid
                    }
                    circPub.publish(circle, idCid, postCids, emptyList(), emptyList(), cipher)
                }
            }
        }

        // Now publish a new manifest if we changed anything
        if (postsToSync.isNotEmpty() || actionsToSync.isNotEmpty()) {
            Log.i(TAG, "Publishing a new manifest")

            val publicIdentity = db.identityDao().identitiesFor(ipfs.peerID).first()
            val identityCid = publicIdentity.cid ?: idPub.publish(publicIdentity, NoopEncryptor())

            ManifestPublisher(db.manifestDao(), db.ipnsCacheDao(), CoroutineScope(Dispatchers.Default), ipfs).publish(
                db.circleDao().all().mapNotNull { it.cid },
                db.actionDao().all().mapNotNull { it.cid },
                identityCid
            )
        } else {
            Log.i(TAG, "Nothing new to upload. Sleeping")
        }
    }

    fun refresh() {
        Log.i(TAG, "Refreshing existing manifest")
        db.ipnsCacheDao().getPath(ipfs.peerID, "")?.let { root ->
            ManifestPublisher(
                db.manifestDao(),
                db.ipnsCacheDao(),
                CoroutineScope(Dispatchers.Default),
                ipfs).
            provideRoot(root.cid, root.sequence)
        }
        val cids = mutableListOf<ContentId>()
        cids.addAll(db.postDao().all().mapNotNull{ it.cid })
        cids.addAll(db.circleDao().all().mapNotNull { it.cid })
        cids.addAll(db.actionDao().all().mapNotNull { it.cid })
        cids.addAll(db.postFileDao().all().map { it.fileCid })

        cids.forEach { GlobalScope.launch { ipfs.provide(it, 30) } }
    }

    private fun publishActions(syncActions: List<Action>) {
        val actPub = ActionPublisher(db.actionDao(), ipfs)
        syncActions.forEach { action ->
            // Actions are encrypted with the key of their target User
            val pubkey = Keyring.pubKeyFor(db.userDao(), action.toPeerId)
            val cipher = RsaWithAesEncryptor(null, pubkey)

            actPub.publish(action, cipher)
        }
        Log.i(TAG, "All actions stored")
    }


}