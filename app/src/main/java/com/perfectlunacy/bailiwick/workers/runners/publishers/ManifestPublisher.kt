package com.perfectlunacy.bailiwick.workers.runners.publishers

import android.util.Log
import com.perfectlunacy.bailiwick.Bailiwick
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.models.db.*
import com.perfectlunacy.bailiwick.models.ipfs.IpfsManifest
import com.perfectlunacy.bailiwick.models.ipfs.Link
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.storage.ipfs.IPFSWrapper
import com.perfectlunacy.bailiwick.workers.runners.PublishRunner
import kotlinx.coroutines.*

class ManifestPublisher(private val manifestDao: ManifestDao,
                        private val ipnsCacheDao: IpnsCacheDao,
                        private val scope: CoroutineScope,
                        private val ipfs: IPFS) {

    companion object {
        const val TAG = "ManifestPublisher"
    }

    private enum class FileType {
        File,
        Directory
    }

    private class BailiwickFile(val name: String, val type: FileType, private var cid: ContentId) {
        private val children = mutableListOf<BailiwickFile>()

        fun add(file: BailiwickFile) {
            children.add(file)
        }

        fun cid(ipfs: IPFS): ContentId {
            return when(type) {
                FileType.File -> cid
                FileType.Directory -> {
                    cid = ipfs.createEmptyDir()!!
                    children.forEach { child ->
                        cid = ipfs.addLinkToDir(cid, child.name, child.cid(ipfs))!!
                    }

                    cid
                }
            }
        }
    }

    fun publish(circles: List<ContentId>, actions: List<ContentId>, identityCid: ContentId): ContentId {
        val manCid = manifestCid(circles, actions)

        if(manCid == manifestDao.current()?.cid) {
            Log.i(TAG, "Manifest already up to date, not re-publishing.")
            return manCid
        }

        val seq = 1 + (manifestDao.currentSequence() ?: 0)

        // Create the directory structure
        val root = BailiwickFile("", FileType.Directory, ipfs.createEmptyDir()!!)
        val bw = BailiwickFile("bw", FileType.Directory, ipfs.createEmptyDir()!!)
        val version = BailiwickFile(Bailiwick.VERSION, FileType.Directory, ipfs.createEmptyDir()!!)
        val manifestFile = BailiwickFile("manifest.json", FileType.File, manCid)
        val identityFile = BailiwickFile("identity.json", FileType.File, identityCid)

        root.add(bw)
        bw.add(version)
        version.add(manifestFile)
        version.add(identityFile)

        Log.i(PublishRunner.TAG, "IPNS record sequence: $seq")
        val rootCid = root.cid(ipfs)

        ipnsCacheDao.insert(IpnsCache(ipfs.peerID, "", rootCid, seq))
        manifestDao.insert(Manifest(manCid, seq))

        provideRoot(rootCid, seq)

        Log.i(PublishRunner.TAG, "New manifest: $manCid")
        return manCid
    }

    fun provideRoot(rootCid: ContentId, seq: Long) {
        ipfs.publishName(rootCid, seq, 30)
        ipfs.provide(rootCid, 30)
        val timeoutSeconds = 30L
        Log.i(IPFSWrapper.TAG, "Providing links from root")
        ipfs.getLinks(rootCid, true, timeoutSeconds).let { links ->
            if (links == null) {
                Log.e(IPFSWrapper.TAG, "Failed to locate links for root!")
                return@let
            }

            provideLinks(links, timeoutSeconds)
        }
    }

    private fun manifestCid(circles: List<ContentId>, actions: List<ContentId>): ContentId {
        return IpfsManifest(circles, actions).toIpfs(NoopEncryptor(), ipfs)
    }

    private fun provideLinks(links: List<Link>, timeoutSeconds: Long) {
        links.forEach { link ->
            scope.launch {
                withContext(Dispatchers.Default) {
                    ipfs.provide(link.cid, timeoutSeconds)
                    Log.i(IPFSWrapper.TAG, "Providing link ${link.name}")
                }
            }

            scope.launch(Dispatchers.Default) {
                // Recursively add our children as well
                ipfs.getLinks(link.cid, true, timeoutSeconds).let { childLinks ->
                    if(childLinks == null || childLinks.isEmpty()) {
                        Log.w(PublishRunner.TAG, "No links found for ${link.name}")
                        return@let
                    }
                    provideLinks(childLinks, timeoutSeconds)
                }
            }
        }
    }
}