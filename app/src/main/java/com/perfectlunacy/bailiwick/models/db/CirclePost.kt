package com.perfectlunacy.bailiwick.models.db

import androidx.room.*

@Entity
data class CirclePost(val circleId: Long, val postId: Long) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface CirclePostDao {
    @Query("SELECT postId FROM circlepost WHERE circleId = :circleId")
    fun postsIn(circleId: Long): List<Long>

    @Insert
    fun insert(circlePost: CirclePost)
}