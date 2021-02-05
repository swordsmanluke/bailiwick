package com.perfectlunacy.bailiwick.models.db

import android.database.Cursor
import androidx.room.*

@Entity
data class User (
    @PrimaryKey val id: Int,
    @ColumnInfo val uid: String,
    @ColumnInfo val name: String
)

// TODO: Link an "Identity" to a User, where Identity stores the biographical info that we know

@Dao
interface UserDao {
    @Insert
    fun insert(user: User)

    @Update
    fun update(user: User)

    @Delete
    fun delete(user: User)

    @Query("SELECT * FROM user")
    fun all(): List<User>
}