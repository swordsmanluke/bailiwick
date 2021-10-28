package com.perfectlunacy.bailiwick.models.db

import androidx.room.*
import com.perfectlunacy.bailiwick.storage.PeerId
import java.security.PublicKey

@Entity
data class IpnsCache(@PrimaryKey(autoGenerate = true) val id: Int=0,
                     var peerId: String,
                     var path: String,
                     var cid: String,
                     var sequence: Long
)

@Dao
interface IpnsCacheDao {
    @Query("SELECT * FROM ipnscache WHERE peerId = :peerId AND path = :path ORDER BY sequence DESC LIMIT 1")
    fun getPath(peerId: String, path: String): IpnsCache?

    @Query("SELECT sequence FROM ipnscache where peerId = :peerId ORDER BY sequence DESC LIMIT 1")
    fun sequenceFor(peerId: String): Long?

    @Query("SELECT * FROM ipnscache WHERE peerId = :peerId and sequence = :sequence")
    fun pathsForPeer(peerId: String, sequence: Long): List<IpnsCache>

    @Insert
    fun insert(account: IpnsCache)
}