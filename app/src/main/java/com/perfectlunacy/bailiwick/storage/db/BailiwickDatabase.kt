package com.perfectlunacy.bailiwick.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.perfectlunacy.bailiwick.models.db.*

@TypeConverters(ActionTypeConverters::class, CommonConverters::class)
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
        Action::class,
        User::class,
        Subscription::class,
        Reaction::class,
        Tag::class,
        // Gossip-related entities
        PeerTopic::class,
        ManifestCache::class
    ],
    version = 6,  // Removed PeerDoc (migrated to Gossip-based PeerTopic)
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
    abstract fun actionDao(): ActionDao
    abstract fun userDao(): UserDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun reactionDao(): ReactionDao
    abstract fun tagDao(): TagDao

    // Gossip-related DAOs (Phase 1)
    abstract fun peerTopicDao(): PeerTopicDao
    abstract fun manifestCacheDao(): ManifestCacheDao
}

fun getBailiwickDb(context: Context): BailiwickDatabase {
    return Room.databaseBuilder(context, BailiwickDatabase::class.java, "bailiwick-db")
        .fallbackToDestructiveMigration()  // OK since no users to migrate
        .build()
}
