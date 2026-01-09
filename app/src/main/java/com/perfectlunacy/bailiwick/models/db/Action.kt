package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.BlobHash
import com.perfectlunacy.bailiwick.storage.NodeId
import java.util.*

enum class ActionType { Delete, UpdateKey, Introduce }

/**
 * Room TypeConverters for ActionType enum.
 */
class ActionTypeConverters {
    @TypeConverter
    fun fromActionType(value: ActionType): String = value.name

    @TypeConverter
    fun toActionType(value: String): ActionType = ActionType.valueOf(value)
}

@Entity
@TypeConverters(ActionTypeConverters::class)
data class Action(
    val timestamp: Long,
    val blobHash: BlobHash?,
    val fromPeerId: String?,  // Source peer who created this action (null for our own actions)
    val toPeerId: String,      // Target peer who should process this action
    val actionType: ActionType,
    val data: String,
    val processed: Boolean
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    companion object {
        @JvmStatic
        fun updateKeyAction(toNodeId: NodeId, key: String): Action {
            val now = Calendar.getInstance().timeInMillis
            return Action(
                timestamp = now,
                blobHash = null,
                fromPeerId = null,  // Our own action, fromPeerId will be set by receiver
                toPeerId = toNodeId,
                actionType = ActionType.UpdateKey,
                data = key,
                processed = true  // We don't need to process this, recipient will
            )
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

    @Update
    fun update(action: Action)

    @Query("UPDATE `action` SET processed = 1 WHERE id = :id")
    fun markProcessed(id: Long)
}