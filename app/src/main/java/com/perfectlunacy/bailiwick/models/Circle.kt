package com.perfectlunacy.bailiwick.models

import com.perfectlunacy.bailiwick.storage.Bailiwick
import com.perfectlunacy.bailiwick.storage.BailiwickImpl
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.util.*

class Circles(private val bw: Bailiwick) {
    companion object {
        @JvmStatic
        fun create(bw: Bailiwick): ContentId {
            val rec = CircleFileRecord(mutableSetOf(Circle(bw, UUID.randomUUID(), "Everyone", mutableListOf(bw.peerId), "${bw.peerId}:everyone")))
            return bw.store(rec, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
        }
    }

    private data class CircleFileRecord(val circles: MutableSet<Circle>)

    private var _circleFile = bw.retrieve(bw.bailiwickAccount.circlesCid,
        bw.encryptorForKey(BailiwickImpl.USER_PRIVATE),
        CircleFileRecord::class.java)!!

    private val circles
        get() = _circleFile.circles

    fun rm(uuid: UUID) {
        circles.removeAll { it.uuid == uuid }
        store()
    }

    fun add(circle: Circle) {
        circles.add(circle)
        store()
    }

    fun store() {
        bw.bailiwickAccount.circlesCid = bw.store(
            CircleFileRecord(circles), // Store the updated `circles`
            bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
    }
}

data class Circle(private val bw: Bailiwick, val uuid: UUID, private var _name: String, private var _peers: MutableList<PeerId>, private val keyId: String) {
    var name
        get() = _name
        set(value) {
            _name = value
            bw.circles.store()
        }

    fun add(peerId: PeerId) {
        _peers.add(peerId)
        bw.circles.store()
        // TODO: Send existing key to new peer
    }

    fun rm(peerId: PeerId) {
        _peers.remove(peerId)
        bw.circles.store()
        // TODO: Generate and Send new key to all remaining peers
    }

    val peers: List<PeerId>
        get() = _peers

}