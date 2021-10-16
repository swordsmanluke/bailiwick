package com.perfectlunacy.bailiwick.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.db.AccountDao

@Database(entities = [Account::class], version = 1)
abstract class BailiwickDatabase: RoomDatabase() {
    abstract fun accountDao(): AccountDao
}

fun getBailiwickDb(context: Context): BailiwickDatabase {
    return Room.databaseBuilder(context, BailiwickDatabase::class.java, "bailiwick-db").build()
}