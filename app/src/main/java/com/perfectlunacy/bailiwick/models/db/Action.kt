package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import java.util.*

enum class ActionType { Delete, UpdateKey, Introduce }

@Entity
data class Action(
    val timestamp: Long,
    val blobHash: BlobHash?,
    val toPeerId: String,
    val actionType: String,
    val data: String,
    val processed: Boolean
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    companion object {
        @JvmStatic
        fun updateKeyAction(toNodeId: NodeId, key: String): Action {
            val now = Calendar.getInstance().timeInMillis
            return Action(
                now,
                null,
                toNodeId,
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

    @Query("SELECT * FROM `action` WHERE blobHash IS NULL")
    fun inNeedOfSync(): List<Action>

    @Query("SELECT * FROM `action` WHERE processed IS 0")
    fun inNeedOfProcessing(): List<Action>

    @Query("SELECT * FROM `action` WHERE id = :id LIMIT 1")
    fun find(id: Long): Action

    @Query("SELECT * FROM `action` WHERE blobHash = :hash")
    fun findByHash(hash: BlobHash): Action?

    @Query("SELECT * FROM `action` WHERE toPeerId = :nodeId")
    fun actionsFor(nodeId: String): List<Action>

    @Query("SELECT * FROM `action` WHERE toPeerId IN (:nodeIds)")
    fun actionsFor(nodeIds: List<String>): List<Action>

    @Query("SELECT EXISTS( SELECT 1 FROM `action` WHERE blobHash = :hash)")
    fun actionExists(hash: BlobHash): Boolean

    @Query("UPDATE `action` SET blobHash = :hash WHERE id = :id")
    fun updateHash(id: Long, hash: BlobHash?)

    @Insert
    fun insert(action: Action): Long

}