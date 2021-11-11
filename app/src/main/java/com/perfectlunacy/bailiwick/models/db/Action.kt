package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId
import com.perfectlunacy.bailiwick.storage.PeerId
import java.util.*

enum class ActionType { Delete, UpdateKey, Introduce }

@Entity
data class Action(
    val timestamp: Long,
    val cid: ContentId?,
    val toPeerId: String,
    val actionType: String,
    val data: String,
    val processed: Boolean
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    companion object {
        @JvmStatic
        fun updateKeyAction(toPeerId: PeerId, key: String): Action {
            val now = Calendar.getInstance().timeInMillis
            return Action(
                now,
                null,
                toPeerId,
                ActionType.UpdateKey.toString(),
                key,
                true) // I don't need to process this Action, but someone else will
        }
    }
}

@Dao
interface ActionDao {
    @Query("SELECT * FROM `action`")
    fun all(): List<Action>

    @Query("SELECT * FROM `action` WHERE cid IS NULL")
    fun inNeedOfSync(): List<Action>

    @Query("SELECT * FROM `action` WHERE processed IS 0")
    fun inNeedOfProcessing(): List<Action>

    @Query("SELECT * FROM `action` WHERE id = :id LIMIT 1")
    fun find(id: Long): Action

    @Query("SELECT * FROM `action` WHERE cid = :cid")
    fun findByCid(cid: ContentId): Action?

    @Query("SELECT * FROM `action` WHERE toPeerId = :peerId")
    fun actionsFor(peerId: String): List<Action>

    @Query("SELECT * FROM `action` WHERE toPeerId IN (:peerIds)")
    fun actionsFor(peerIds: List<String>): List<Action>

    @Query("SELECT EXISTS( SELECT 1 FROM `action` WHERE cid = :cid)")
    fun actionExists(cid: ContentId): Boolean

    @Query("UPDATE `action` SET cid = :cid WHERE id = :id")
    fun updateCid(id: Long, cid: ContentId?)

    @Insert
    fun insert(action: Action): Long

}