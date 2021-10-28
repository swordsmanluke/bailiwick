package com.perfectlunacy.bailiwick.storage.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.perfectlunacy.bailiwick.models.db.Account
import com.perfectlunacy.bailiwick.models.db.AccountDao
import com.perfectlunacy.bailiwick.models.db.IpnsCache
import com.perfectlunacy.bailiwick.models.db.IpnsCacheDao

@Database(
    entities = [Account::class, IpnsCache::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class BailiwickDatabase: RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun ipnsCacheDao(): IpnsCacheDao
}

fun getBailiwickDb(context: Context): BailiwickDatabase {
    return Room.databaseBuilder(context, BailiwickDatabase::class.java, "bailiwick-db").build()
}