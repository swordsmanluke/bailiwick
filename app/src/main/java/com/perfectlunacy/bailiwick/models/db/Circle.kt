package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.ContentId
import threads.lite.cid.PeerId
import java.util.*

@Entity
data class Circle(var name: String,
                  val identityId: Long,
                  var cid: ContentId?) {

    @PrimaryKey(autoGenerate = true) var id: Long = 0
}

@Dao
interface CircleDao{
    @Query("SELECT * FROM circle")
    fun all(): List<Circle>

    @Query("SELECT * FROM circle WHERE id = :id LIMIT 1")
    fun find(id: Long): Circle

    @Insert
    fun insert(circle: Circle): Long
}