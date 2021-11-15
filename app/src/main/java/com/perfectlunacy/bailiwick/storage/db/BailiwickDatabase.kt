package com.perfectlunacy.bailiwick.storage.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.perfectlunacy.bailiwick.models.db.*

@Database(
    entities = [Account::class,
                IpnsCache::class,
                Identity::class,
                User::class,
                Circle::class,
                CircleMember::class,
                CirclePost::class,
                Post::class,
                PostFile::class,
                Action::class,
                Subscription::class,
                Sequence::class,
                Key::class],
    version = 6,
    autoMigrations = [AutoMigration(from = 1, to = 2),
                      AutoMigration(from = 2, to = 3),
                      AutoMigration(from = 3, to = 4),
                      AutoMigration(from = 4, to = 5),
                      AutoMigration(from = 5, to = 6)])
abstract class BailiwickDatabase: RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun ipnsCacheDao(): IpnsCacheDao
    abstract fun identityDao(): IdentityDao
    abstract fun userDao(): UserDao
    abstract fun circleDao(): CircleDao
    abstract fun circleMemberDao(): CircleMemberDao
    abstract fun circlePostDao(): CirclePostDao
    abstract fun postDao(): PostDao
    abstract fun postFileDao(): PostFileDao
    abstract fun actionDao(): ActionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun sequenceDao(): SequenceDao
    abstract fun keyDao(): KeyDao
}

fun getBailiwickDb(context: Context): BailiwickDatabase {
    return Room.databaseBuilder(context, BailiwickDatabase::class.java, "bailiwick-db").build()
}