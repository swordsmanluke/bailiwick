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

    @Insert
    fun insert(circleMember: CircleMember)
}