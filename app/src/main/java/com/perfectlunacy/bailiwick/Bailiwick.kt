package com.perfectlunacy.bailiwick

import com.perfectlunacy.bailiwick.ciphers.AESEncryptor
import com.perfectlunacy.bailiwick.ciphers.Encryptor
import com.perfectlunacy.bailiwick.ciphers.MultiCipher
import com.perfectlunacy.bailiwick.ciphers.NoopEncryptor
import com.perfectlunacy.bailiwick.storage.PeerId
import com.perfectlunacy.bailiwick.storage.db.BailiwickDatabase

class Bailiwick {
    companion object {
        const val VERSION = "0.2"
    }
}