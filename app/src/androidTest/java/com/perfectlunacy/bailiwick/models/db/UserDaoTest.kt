package com.perfectlunacy.bailiwick.models.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for UserDao, especially the mute and delete functionality
 * used by the Contact screen.
 */
@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var context: Context
    private lateinit var db: BailiwickDatabase
    private lateinit var userDao: UserDao

    private val testNodeId1 = "test-node-id-1"
    private val testNodeId2 = "test-node-id-2"
    private val testPublicKey1 = "public-key-1"
    private val testPublicKey2 = "public-key-2"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, BailiwickDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =====================
    // Insert and Find Tests
    // =====================

    @Test
    fun insert_and_find_by_id() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        userDao.insert(user)

        val users = userDao.all()
        assertEquals(1, users.size)

        val foundUser = userDao.find(users[0].id)
        assertNotNull(foundUser)
        assertEquals(testNodeId1, foundUser!!.nodeId)
        assertEquals(testPublicKey1, foundUser.publicKey)
        assertFalse(foundUser.isMuted)
    }

    @Test
    fun findByNodeId_returns_correct_user() {
        val user1 = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        val user2 = User(nodeId = testNodeId2, publicKey = testPublicKey2)
        userDao.insert(user1)
        userDao.insert(user2)

        val foundUser = userDao.findByNodeId(testNodeId2)
        assertNotNull(foundUser)
        assertEquals(testNodeId2, foundUser!!.nodeId)
        assertEquals(testPublicKey2, foundUser.publicKey)
    }

    @Test
    fun findByNodeId_returns_null_for_nonexistent_node() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        userDao.insert(user)

        val foundUser = userDao.findByNodeId("nonexistent-node-id")
        assertNull(foundUser)
    }

    @Test
    fun find_returns_null_for_nonexistent_id() {
        val foundUser = userDao.find(999L)
        assertNull(foundUser)
    }

    // =====================
    // Mute Functionality Tests
    // =====================

    @Test
    fun new_user_is_not_muted_by_default() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        userDao.insert(user)

        val users = userDao.all()
        assertFalse(users[0].isMuted)
    }

    @Test
    fun setMuted_mutes_user() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        userDao.insert(user)
        val userId = userDao.all()[0].id

        userDao.setMuted(userId, true)

        val mutedUser = userDao.find(userId)
        assertTrue(mutedUser!!.isMuted)
    }

    @Test
    fun setMuted_unmutes_user() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1, isMuted = true)
        userDao.insert(user)
        val userId = userDao.all()[0].id

        // Verify initially muted
        assertTrue(userDao.isMuted(userId))

        // Unmute
        userDao.setMuted(userId, false)

        val unmutedUser = userDao.find(userId)
        assertFalse(unmutedUser!!.isMuted)
    }

    @Test
    fun isMuted_returns_correct_status() {
        val mutedUser = User(nodeId = testNodeId1, publicKey = testPublicKey1, isMuted = true)
        val unmutedUser = User(nodeId = testNodeId2, publicKey = testPublicKey2, isMuted = false)
        userDao.insert(mutedUser)
        userDao.insert(unmutedUser)

        val users = userDao.all()
        val mutedId = users.find { it.nodeId == testNodeId1 }!!.id
        val unmutedId = users.find { it.nodeId == testNodeId2 }!!.id

        assertTrue(userDao.isMuted(mutedId))
        assertFalse(userDao.isMuted(unmutedId))
    }

    @Test
    fun setMuted_only_affects_target_user() {
        val user1 = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        val user2 = User(nodeId = testNodeId2, publicKey = testPublicKey2)
        userDao.insert(user1)
        userDao.insert(user2)

        val users = userDao.all()
        val user1Id = users.find { it.nodeId == testNodeId1 }!!.id
        val user2Id = users.find { it.nodeId == testNodeId2 }!!.id

        userDao.setMuted(user1Id, true)

        assertTrue(userDao.isMuted(user1Id))
        assertFalse(userDao.isMuted(user2Id))
    }

    // =====================
    // Delete Functionality Tests
    // =====================

    @Test
    fun delete_removes_user() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        userDao.insert(user)
        val userId = userDao.all()[0].id

        userDao.delete(userId)

        val deletedUser = userDao.find(userId)
        assertNull(deletedUser)
        assertTrue(userDao.all().isEmpty())
    }

    @Test
    fun delete_only_affects_target_user() {
        val user1 = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        val user2 = User(nodeId = testNodeId2, publicKey = testPublicKey2)
        userDao.insert(user1)
        userDao.insert(user2)

        val users = userDao.all()
        val user1Id = users.find { it.nodeId == testNodeId1 }!!.id

        userDao.delete(user1Id)

        assertEquals(1, userDao.all().size)
        assertNotNull(userDao.findByNodeId(testNodeId2))
        assertNull(userDao.findByNodeId(testNodeId1))
    }

    @Test
    fun delete_nonexistent_user_does_nothing() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        userDao.insert(user)

        userDao.delete(999L)

        assertEquals(1, userDao.all().size)
    }

    // =====================
    // publicKeyFor Tests
    // =====================

    @Test
    fun publicKeyFor_returns_correct_key() {
        val user = User(nodeId = testNodeId1, publicKey = testPublicKey1)
        userDao.insert(user)

        val publicKey = userDao.publicKeyFor(testNodeId1)
        assertEquals(testPublicKey1, publicKey)
    }

    @Test
    fun publicKeyFor_returns_null_for_nonexistent_node() {
        val publicKey = userDao.publicKeyFor("nonexistent-node-id")
        assertNull(publicKey)
    }

    // =====================
    // All Users Tests
    // =====================

    @Test
    fun all_returns_empty_list_when_no_users() {
        val users = userDao.all()
        assertTrue(users.isEmpty())
    }

    @Test
    fun all_returns_all_users() {
        userDao.insert(User(nodeId = testNodeId1, publicKey = testPublicKey1))
        userDao.insert(User(nodeId = testNodeId2, publicKey = testPublicKey2))

        val users = userDao.all()
        assertEquals(2, users.size)
    }
}
