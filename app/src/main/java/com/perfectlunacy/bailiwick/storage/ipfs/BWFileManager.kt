package com.perfectlunacy.bailiwick.storage.ipfs

import android.util.Log
import com.google.gson.Gson
import com.perfectlunacy.bailiwick.models.Identity
import threads.lite.IPFS
import threads.lite.cid.Cid
import threads.lite.cid.PeerId
import threads.lite.core.TimeoutCloseable

class BWFileManager(private val ipfs: IPFS) {

    fun manifestFor(peerId: PeerId): Manifest? {
        return xFor<Manifest>(peerId,"/bw/0.1/manifest.json")
    }

    fun identityFor(peerId: PeerId): Identity? {
        return xFor<Identity>(peerId,"/bw/0.1/identity.json")
    }

    private inline fun <reified T> xFor(peerId: PeerId, path: String): T? {
        val cid = cidForPath(peerId, path) ?: return null
        val manifestJson = ipfs.getText(cid, TimeoutCloseable(30))
        return Gson().fromJson(manifestJson, T::class.java)
    }

    fun storeIdentity(myPeerId: PeerId, identity: Identity) {
        val cid = ipfs.storeText(Gson().toJson(identity))
        val newRootCid = addFileToDir(myPeerId, BASE_PATH, "identity.json", cid)
        Log.i(TAG, "Publishing new root cid: ${newRootCid.String()}")
        ipfs.publishName(newRootCid, 2, TimeoutCloseable(30))
    }

    fun initBase(myPeerId: PeerId) {
        Log.w(TAG, "Initializing Bailiwick structure. This will delete any existing data!!!")
        val versionCid = ipfs.createEmptyDir()!!
        val bwDir = ipfs.createEmptyDir()!!
        val bwCid = ipfs.addLinkToDir(bwDir, VERSION, versionCid)!!
        val root = ipfs.createEmptyDir()!!
        val rootCid = ipfs.addLinkToDir(root, "bw", bwCid)!!
        Log.i(TAG, "Created Bailiwick structure. Publishing...")

        // Finally, publish the baseDir to IPNS
        // TODO: Is this doing what I think it's doing?
        ipfs.publishName(rootCid, 1, TimeoutCloseable(30))
        Log.i(TAG, "Published empty dirs with root: ${rootCid.String()}")
    }

    fun addFileToDir(peerId: PeerId, path: String, filename: String, content: Cid): Cid {
        Log.i(TAG,"Adding $filename to $path")
        val dirs = path.split("/")
        var dir = ""
        val root = PathNode("/",null, cidForPath(peerId, "")!!)
        var parent = root

        // collect the list of dirs to cids
        dirs.forEach{ d ->
            dir += "/$d"
            val cid = cidForPath(peerId, dir)!!
            val newNode = PathNode(d, parent, cid)
            parent = newNode
        }

        // add a file to its parent... and then update every one of its parents
        var cidToAdd = content
        var name = filename
        while(true) {
            val newParentCid = ipfs.addLinkToDir(parent.cid, name, cidToAdd)!!
            Log.i(TAG, "Linking $name -> ${parent.name} (${newParentCid.String()})")
            name = parent.name
            parent.cid = newParentCid
            cidToAdd = newParentCid
            if(parent.parent == null) { break }
            parent = parent.parent!!
        }

        return parent.cid
    }

    fun cidForPath(pid: PeerId, path: String): Cid? {
        val ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, TimeoutCloseable(10))!!
        val link = IPFS.IPFS_PATH + ipnsRecord.hash + path
        val node = ipfs.resolveNode(link, TimeoutCloseable(30))
        return node?.cid
    }

    data class PathNode(val name: String, val parent: PathNode?, var cid: Cid);

    companion object {
        const val TAG = "BWFileManager"
        const val VERSION = "0.1" // TODO use this value everywhere
        const val BASE_PATH = "bw/$VERSION"
    }
}