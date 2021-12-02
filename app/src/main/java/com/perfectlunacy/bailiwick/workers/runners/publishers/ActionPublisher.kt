package com.perfectlunacy.bailiwick.workers.runners.publishers

import android.util.Log
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.models.db.Action
import com.perfectlunacy.bailiwick.models.db.ActionDao
import com.perfectlunacy.bailiwick.models.db.ActionType
import com.perfectlunacy.bailiwick.models.ipfs.IpfsAction
import com.perfectlunacy.bailiwick.storage.ipfs.IPFS
import com.perfectlunacy.bailiwick.workers.runners.PublishRunner

class ActionPublisher(private val actionDao: ActionDao, private val ipfs: IPFS) {
    fun publish(action: Action, cipher: Encryptor) {
        val metadata = mutableMapOf<String, String>()
        when(ActionType.valueOf(action.actionType)) {
            ActionType.Delete -> TODO()
            ActionType.UpdateKey -> { metadata["key"] = action.data }
            ActionType.Introduce -> TODO()
        }

        val ipfsAction = IpfsAction(action.actionType, metadata)
        val actionCid = ipfsAction.toIpfs(cipher, ipfs)
        Log.i("ActionPublisher", "Uploaded Action to CID $actionCid")
        actionDao.updateCid(action.id, actionCid)
    }
}