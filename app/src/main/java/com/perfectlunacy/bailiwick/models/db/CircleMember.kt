package com.perfectlunacy.bailiwick.models.db

import androidx.room.*

@Entity
data class CircleMember(val circleId: Long, val userId: Long) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface CircleMemberDao {
    @Query("SELECT userId FROM circlemember WHERE circleId = :circleId")
    fun membersFor(circleId: Long): List<Long>

    @Query("SELECT circleId FROM circlemember WHERE userId = :userId")
    fun circlesFor(userId: Long): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM circlemember WHERE circleId = :circleId AND userId = :userId)")
    fun isMember(circleId: Long, userId: Long): Boolean

    @Insert
    fun insert(circleMember: CircleMember)

    @Query("DELETE FROM circlemember WHERE circleId = :circleId AND userId = :userId")
    fun delete(circleId: Long, userId: Long)

    @Query("DELETE FROM circlemember WHERE userId = :userId")
    fun deleteByIdentity(userId: Long)
}