package com.perfectlunacy.bailiwick.models.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Post::class, PostFile::class, User::class], version = 1)
abstract class BailiwickDatabase: RoomDatabase() {
    abstract fun getUserDao(): UserDao
    abstract fun getPostDao(): PostDao
    abstract fun getPostFileDao(): PostFileDao

    companion object {
        @Volatile
        private var INSTANCE: BailiwickDatabase? = null

        fun getInstance(context: Context): BailiwickDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(context.applicationContext,
                        BailiwickDatabase::class.java,
                        "bailiwick_db").build()
                }

                return instance
            }
        }

    }
}