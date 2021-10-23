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
            val rec = CircleFileRecord(mutableSetOf(
                CircleRecord(
                    UUID.randomUUID(),
                    "Everyone",
                    mutableListOf(bw.peerId),
                    "${bw.peerId}:everyone"
                )
            ))
            return bw.store(rec, bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
        }
    }

    private data class CircleFileRecord(val circles: MutableSet<CircleRecord>)

    private var _circleFile = bw.retrieve(bw.bailiwickAccount.circlesCid,
        bw.encryptorForKey(BailiwickImpl.USER_PRIVATE),
        CircleFileRecord::class.java)!!

    fun rm(uuid: UUID) {
        _circleFile.circles.removeAll { it.uuid == uuid }
        store()
    }

    fun add(circle: Circle) {
        _circleFile.circles.add(circle.toRecord())
        store()
    }

    fun all(): List<Circle> {
        return _circleFile.circles.map{ it.toCircle(bw) }
    }

    fun store() {
        bw.bailiwickAccount.circlesCid = bw.store(
            CircleFileRecord(_circleFile.circles), // Store the updated `circles`
            bw.encryptorForKey(BailiwickImpl.USER_PRIVATE))
    }
}
data class CircleRecord(val uuid: UUID, var name: String, var peers: MutableList<PeerId>, val keyId: String) {
    fun toCircle(bw: Bailiwick): Circle {
        return Circle(bw, uuid, name, peers, keyId)
    }
}

class Circle(private val bw: Bailiwick, uuid: UUID, name: String, peers: MutableList<PeerId>, keyId: String) {

    private var _record = CircleRecord(uuid, name,  peers, keyId)

    val uuid: UUID
        get() = _record.uuid

    var name
        get() = _record.name
        set(value) {
            _record.name = value
            bw.circles.store()
        }

    fun add(peerId: PeerId) {
        _record.peers.add(peerId)
        bw.circles.store()
        // TODO: Send existing key to new peer
    }

    fun rm(peerId: PeerId) {
        _record.peers.remove(peerId)
        bw.circles.store()
        // TODO: Generate and Send new key to all remaining peers
    }

    val peers: List<PeerId>
        get() = _record.peers

    fun toRecord(): CircleRecord {
        return _record
    }
}