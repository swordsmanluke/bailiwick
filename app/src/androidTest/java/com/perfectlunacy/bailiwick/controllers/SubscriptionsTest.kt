package com.perfectlunacy.bailiwick.controllers

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.perfectlunacy.bailiwick.models.db.BailiwickDatabase
import com.perfectlunacy.bailiwick.models.db.User
import com.perfectlunacy.bailiwick.storage.DistHashTable
import org.junit.Test

import org.junit.Assert.*

class SubscriptionsTest {

    val db = buildMemDb()
    val dht: DistHashTable = InMemoryStore("mycid")

    /***
     * Adding New Subscription
     */

    @Test
    fun addingNewSubCreatesUser() {
        val users = db.getUserDao()
        val beforeCnt = users.all().count()
        subject().add("cid", "new cid")
        val afterCnt = users.all().count()

        assertEquals("Failed to add new User!",beforeCnt + 1, afterCnt)
    }

    @Test
    fun addingSameCidDoesNotCreateNewUser() {
        val users = db.getUserDao()
        val beforeCnt = users.all().count()
        var caughtExpectedException = false

        try {
            subject().add("cid", "new cid")
            subject().add("cid", "same cid")
        } catch(ex: SQLiteConstraintException) {
            caughtExpectedException = true
        }
        val afterCnt = users.all().count()

        assertNotEquals("Added two Users for same CID!",beforeCnt + 2, afterCnt)
        assertEquals("Failed to add any User!",beforeCnt + 1, afterCnt)
        assertEquals("Did not catch constraint exception!", true, caughtExpectedException)
    }

    /***
     * Generating new Subscription Request
     */
    @Test
    fun requestingASubscriptionReturnsCIDandName() {
        val requestStr = subject().request("myname")
        assertEquals("Request name did not match expected name", "myname", requestStr.name)
        assertEquals("Request cid did not match expected cid", "mycid", requestStr.cid)
    }

    /***
     * Generate introduction messages
     */
    @Test
    fun introduce() {
        val alice = User(1, "cid1", "alice")
        val bob = User(2, "cid2", "bob")
        val (aliceIntro, bobIntro) = subject().generateIntroductions(alice, bob)

        assertEquals("Alice's intro has wrong cid", "cid2", aliceIntro.introTo)
        assertEquals("Bob's intro has wrong cid", "cid1", bobIntro.introTo)
    }

    fun subject(): Subscriptions {
        return Subscriptions(db.getUserDao(), dht)
    }

    fun buildMemDb(): BailiwickDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java).build()
    }
}