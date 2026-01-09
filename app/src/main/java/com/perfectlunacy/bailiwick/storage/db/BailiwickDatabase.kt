package com.perfectlunacy.bailiwick.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.perfectlunacy.bailiwick.models.db.*

@TypeConverters(ActionTypeConverters::class)
@Database(
    entities = [
        Account::class,
        Identity::class,
        Post::class,
        PostFile::class,
        Circle::class,
        CircleMember::class,
        CirclePost::class,
        Key::class,
        PeerDoc::class,
        Action::class,
        User::class,
        Subscription::class
    ],
    version = 3,  // Added docTicket field to PeerDoc entity
    exportSchema = true
)
abstract class BailiwickDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun identityDao(): IdentityDao
    abstract fun postDao(): PostDao
    abstract fun postFileDao(): PostFileDao
    abstract fun circleDao(): CircleDao
    abstract fun circleMemberDao(): CircleMemberDao
    abstract fun circlePostDao(): CirclePostDao
    abstract fun keyDao(): KeyDao
    abstract fun peerDocDao(): PeerDocDao
    abstract fun actionDao(): ActionDao
    abstract fun userDao(): UserDao
    abstract fun subscriptionDao(): SubscriptionDao
}

fun getBailiwickDb(context: Context): BailiwickDatabase {
    return Room.databaseBuilder(context, BailiwickDatabase::class.java, "bailiwick-db")
        .fallbackToDestructiveMigration()  // OK since no users to migrate
        .build()
}
