package com.perfectlunacy.bailiwick.storage.ipfs.lite
import android.app.Activity
import android.content.Context
import android.os.storage.StorageManager
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import lite.*
import lite.Reader
import java.io.*
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference


class IPFS private constructor(context: Context) : Listener {
    private var baseDir: File? = null
    private val node: Node
    private val locker = Any()
    var location = 0
    private val swarm = HashSet<String>()
    private val gson: Gson = Gson()
    var seeding = 0L
        private set
    var leeching = 0L
        private set
    private var pusher: Pusher? = null
    var reachable: Reachable = Reachable.UNKNOWN
        private set(reachable) {
            field = reachable
        }

    fun setPusher(pusher: Pusher?) {
        node.pushing = pusher != null
        this.pusher = pusher
    }

    override fun push(data: ByteArray, pid: String) {
        val text = String(Base64.decode(data, Base64.DEFAULT))
        if (pusher != null) {
            val executor = Executors.newSingleThreadExecutor()
            executor.submit { pusher!!.push(text, pid) }
        }
    }

    fun swarmReduce(pid: String) {
        swarm.remove(pid)
    }

    override fun shouldConnect(pid: String): Boolean {
        return swarm.contains(pid)
    }

    override fun shouldGate(pid: String): Boolean {
        return !swarm.contains(pid)
    }

    fun push(pid: String, map: HashMap<String?, String?>): Boolean {
        return push(pid, gson.toJson(map))
    }

    private fun push(pid: String, push: String): Boolean {
        if (!isDaemonRunning) {
            return false
        }
        try {
            val bytes = Base64.encode(push.toByteArray(), Base64.DEFAULT)
            return node.push(pid, bytes) == bytes.size.toLong()
        } catch (throwable: Throwable) {
            Log.e(TAG, throwable.message ?: throwable.javaClass.name)
        }
        return false
    }

    fun swarmEnhance(pid: String) {
        swarm.add(pid)
    }

    fun swarmEnhance(users: List<String>) {
        swarm.addAll(users)
    }

    val host: String
        get() = base32(node.peerID)

    @Throws(Exception::class)
    fun checkSwarmKey(key: String) {
        node.checkSwarmKey(key)
    }

    fun shutdown() {
        try {
            setPusher(null)
            node.shutdown = true
        } catch (throwable: Throwable) {
            Log.e(TAG, throwable.message ?: throwable.javaClass.name)
        }
    }

    @Synchronized
    fun startDaemon(privateSharing: Boolean) {
        if (!node.running) {
            synchronized(locker) {
                if (!node.running) {
                    val failure =
                        AtomicBoolean(false)
                    val executor =
                        Executors.newSingleThreadExecutor()
                    val exception =
                        AtomicReference("")
                    executor.submit {
                        try {
                            val port = node.port
                            if (!isLocalPortFree(port.toInt())) {
                                node.port = nextFreePort().toLong()
                            }
                            Log.e(TAG, "start daemon...")
                            node.daemon(privateSharing)
                            Log.e(TAG, "stop daemon...")
                        } catch (e: Throwable) {
                            failure.set(true)
                            exception.set("" + e.localizedMessage)
                            Log.e(TAG, e.message ?: e.javaClass.name)
                        }
                    }
                    while (!node.running) {
                        if (failure.get()) {
                            break
                        }
                    }
                    if (failure.get()) {
                        throw RuntimeException(exception.get())
                    }
                }
            }
        }
    }

    val totalSpace: Long
        get() = baseDir!!.totalSpace
    val freeSpace: Long
        get() = baseDir!!.freeSpace

    fun bootstrap(minPeers: Int, refresh: Boolean, timeout: Int) {
        if (isDaemonRunning) {
            if (numSwarmPeers() < minPeers || refresh) {
                val bootstrap = getBootstrap(refresh)
                for (address in bootstrap) {
                    val result = swarmConnect(address, null, timeout)
                    Log.i(TAG, " \nBootstrap : $address $result")
                }
            }
        }
    }

    fun dhtFindProviders(cid: CID, numProvs: Int, timeout: Int): List<String> {
        if (!isDaemonRunning) {
            return emptyList()
        }
        val providers: MutableList<String> = ArrayList()
        try {
            node.dhtFindProvsTimeout(
                cid.cid,
                { e: String -> providers.add(e) }, numProvs.toLong(), timeout
            )
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return providers
    }

    fun dhtPublish(cid: CID, closable: DhtClose) {
        if (!isDaemonRunning) {
            return
        }
        try {
            node.dhtProvide(cid.cid, closable)
        } catch (ignore: Throwable) {
        }
    }

    fun pidInfo(pid: String): PeerInfo? {
        if (!isDaemonRunning) {
            return null
        }
        try {
            return node.pidInfo(pid)
        } catch (throwable: Throwable) {
            Log.e(TAG, throwable.message ?: throwable.javaClass.name)
        }
        return null
    }

    fun id(): PeerInfo? {
        return try {
            node.id()
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }

    fun swarmConnect(pid: String, timeout: Int): Boolean {
        return if (!isDaemonRunning) {
            false
        } else swarmConnect("/p2p/$pid", pid, timeout)
    }

    fun swarmConnect(multiAddress: String, pid: String?, timeout: Int): Boolean {
        if (!isDaemonRunning) {
            return false
        }
        try {
            pid?.let { swarmEnhance(it) }
            return node.swarmConnect(multiAddress, timeout)
        } catch (e: Throwable) {
            Log.e(TAG, multiAddress + " " + e.localizedMessage)
        }
        return false
    }

    val isPrivateNetwork: Boolean
        get() = node.privateNetwork

    fun isConnected(pid: String): Boolean {
        if (!isDaemonRunning) {
            return false
        }
        try {
            return node.isConnected(pid)
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return false
    }

    fun swarmPeer(pid: String): Peer? {
        if (!isDaemonRunning) {
            return null
        }
        try {
            return node.swarmPeer(pid)
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return null
    }

    fun swarmPeers(): List<String> {
        return if (!isDaemonRunning) {
            emptyList()
        } else swarm_peers()
    }

    private fun swarm_peers(): List<String> {
        val peers: MutableList<String> = ArrayList()
        if (isDaemonRunning) {
            try {
                node.swarmPeers { e: String -> peers.add(e) }
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }
        return peers
    }

    fun publishName(cid: CID, closeable: Closeable, sequence: Sequence) {
        if (!isDaemonRunning) {
            return
        }
        try {
            node.publishName(cid.cid, closeable::isClosed, sequence)
        } catch (ignore: Throwable) {
        }
    }

    fun base32(pid: String): String {
        return try {
            node.base32(pid)
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }

    fun base58(pid: String): String {
        return try {
            node.base58(pid)
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }

    fun resolveName(
        name: String, sequence: Long,
        closeable: Closeable
    ): ResolvedName? {
        if (!isDaemonRunning) {
            return null
        }
        val time = System.currentTimeMillis()
        val resolvedName = AtomicReference<ResolvedName?>(null)
        try {
            val counter = AtomicInteger(0)
            val close = AtomicBoolean(false)
            node.resolveName(object : ResolveInfo {
                override fun close(): Boolean {
                    return close.get() || closeable.isClosed
                }

                override fun resolved(hash: String, seq: Long) {
                    Log.e(TAG, "$seq $hash")
                    if (seq < sequence) {
                        close.set(true)
                        return  // newest value already available
                    }
                    if (hash.startsWith("/ipfs/")) {
                        if (seq > sequence || counter.get() < 2) {
                            close.set(true)
                        } else {
                            counter.incrementAndGet()
                        }
                        resolvedName.set(
                            ResolvedName(
                                seq, hash.replaceFirst("/ipfs/".toRegex(), "")
                            )
                        )
                    }
                }
            }, name, false, 8)
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        Log.e(
            TAG, "Finished resolve name " + name + " " +
                    (System.currentTimeMillis() - time)
        )
        return resolvedName.get()
    }

    fun rm(cid: String, recursively: Boolean) {
        try {
            node.rm(cid, recursively)
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
    }

    val swarmPort: Long
        get() = node.port

    fun storeData(data: ByteArray): CID? {
        try {
            ByteArrayInputStream(data).use { inputStream ->
                return storeInputStream(
                    inputStream
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return null
    }

    fun storeText(content: String): CID? {
        try {
            ByteArrayInputStream(content.toByteArray()).use { inputStream ->
                return storeInputStream(
                    inputStream
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return null
    }

    fun rmLinkFromDir(dir: CID, name: String?): CID? {
        try {
            return CID(node.removeLinkFromDir(dir.cid, name))
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return null
    }

    fun addLinkToDir(dir: CID, name: String, link: CID): CID? {
        try {
            return CID(node.addLinkToDir(dir.cid, name, link.cid))
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return null
    }

    fun createEmptyDir(): CID? {
        try {
            return CID(node.createEmptyDir())
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return null
    }

    fun getLinkInfo(dir: CID, path: List<String>, closeable: Closeable): LinkInfo? {
        var linkInfo: LinkInfo? = null
        var root: CID = dir
        for (name in path) {
            linkInfo = getLinkInfo(root, name, closeable)
            root = if (linkInfo != null) {
                linkInfo.cid
            } else {
                break
            }
        }
        return linkInfo
    }

    fun getLinkInfo(dir: CID, name: String, closeable: Closeable): LinkInfo? {
        val links: List<LinkInfo>? = ls(dir, closeable)
        if (links != null) {
            for (info in links) {
                if (info.name == name) {
                    return info
                }
            }
        }
        return null
    }

    fun getLinks(cid: CID, closeable: Closeable): List<LinkInfo>? {
        Log.i(TAG, "Lookup CID : " + cid.cid)
        val links: List<LinkInfo>? = ls(cid, closeable)
        if (links == null) {
            Log.i(TAG, "no links or stopped")
            return null
        }
        val result: MutableList<LinkInfo> = ArrayList<LinkInfo>()
        for (link in links) {
            Log.i(TAG, "Link : " + link.toString())
            if (!link.name.isEmpty()) {
                result.add(link)
            }
        }
        return result
    }

    fun ls(cid: CID, closeable: Closeable): List<LinkInfo>? {
        if (!isDaemonRunning) {
            return emptyList<LinkInfo>()
        }
        val infoList: MutableList<LinkInfo> = ArrayList<LinkInfo>()
        try {
            node.ls(cid.cid, object : LsInfoClose {
                override fun close(): Boolean {
                    return closeable.isClosed
                }

                override fun lsInfo(name: String, hash: String, size: Long, type: Int) {
                    val info: LinkInfo = LinkInfo.create(name, hash, size, type)
                    infoList.add(info)
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
            return null
        }
        return if (closeable.isClosed) {
            null
        } else infoList
    }

    fun storeFile(target: File): CID? {
        try {
            return CID(node.addFile(target.absolutePath))
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
        return null
    }

    @Throws(Exception::class)
    fun getReader(cid: CID): Reader {
        return node.getReader(cid.cid)
    }

    private fun loadToOutputStream(
        outputStream: OutputStream, cid: CID,
        progress: Progress
    ): Boolean {
        try {
            getLoaderStream(cid, progress).use { inputStream ->
                copy(
                    inputStream,
                    outputStream
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
            return false
        }
        return true
    }

    @Throws(Exception::class)
    private fun getToOutputStream(outputStream: OutputStream, cid: CID) {
        getInputStream(cid).use { inputStream -> copy(inputStream, outputStream) }
    }

    fun loadToFile(file: File, cid: CID, progress: Progress): Boolean {
        if (!isDaemonRunning) {
            return false
        }
        try {
            FileOutputStream(file).use { outputStream ->
                return loadToOutputStream(
                    outputStream,
                    cid,
                    progress
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
            return false
        }
    }

    @Throws(Exception::class)
    fun storeToOutputStream(
        os: OutputStream, progress: Progress,
        cid: CID, size: Long
    ) {
        var totalRead = 0L
        var remember = 0
        val reader = getReader(cid)
        try {
            reader.load(4096)
            var read = reader.read
            while (read > 0) {
                if (progress.isClosed) {
                    throw RuntimeException("Progress closed")
                }

                // calculate progress
                totalRead += read
                if (progress.doProgress()) {
                    if (size > 0) {
                        val percent = (totalRead * 100.0f / size).toInt()
                        if (remember < percent) {
                            remember = percent
                            progress.setProgress(percent)
                        }
                    }
                }
                val bytes = reader.data
                os.write(bytes, 0, bytes.size)
                reader.load(4096)
                read = reader.read
            }
        } finally {
            reader.close()
        }
    }

    @Throws(Exception::class)
    fun storeToOutputStream(os: OutputStream, cid: CID, blockSize: Int) {
        val reader = getReader(cid)
        try {
            reader.load(blockSize.toLong())
            var read = reader.read
            while (read > 0) {
                val bytes = reader.data
                os.write(bytes, 0, bytes.size)
                reader.load(blockSize.toLong())
                read = reader.read
            }
        } finally {
            reader.close()
        }
    }

    @Throws(Exception::class)
    private fun getLoader(cid: CID, closeable: Closeable): Loader {
        return node.getLoader(cid.cid, closeable::isClosed)
    }

    @Throws(Exception::class)
    fun getLoaderStream(cid: CID, closeable: Closeable, readTimeoutMillis: Long): InputStream {
        val loader = getLoader(cid, closeable)
        return CloseableInputStream(loader, readTimeoutMillis)
    }

    @Throws(Exception::class)
    private fun getLoaderStream(cid: CID, progress: Progress): InputStream {
        val loader = getLoader(cid, progress)
        return LoaderInputStream(loader, progress)
    }

    @Throws(Exception::class)
    fun storeToFile(file: File, cid: CID, blockSize: Int) {
        FileOutputStream(file).use { fileOutputStream ->
            storeToOutputStream(
                fileOutputStream,
                cid,
                blockSize
            )
        }
    }

    fun storeInputStream(
        inputStream: InputStream,
        progress: Progress, size: Long
    ): CID? {
        var res = ""
        try {
            res = node.stream(WriterStream(inputStream, progress, size))
        } catch (e: Throwable) {
            if (!progress.isClosed) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }
        return if (!res.isEmpty()) {
            CID(res)
        } else null
    }

    fun storeInputStream(inputStream: InputStream): CID? {
        return storeInputStream(inputStream, object : Progress {
            override val isClosed: Boolean
                get() = false

            override fun setProgress(progress: Int) {}
            override fun doProgress(): Boolean {
                return false
            }
        }, 0)
    }

    fun getText(cid: CID): String? {
        try {
            ByteArrayOutputStream().use { outputStream ->
                getToOutputStream(outputStream, cid)
                return String(outputStream.toByteArray())
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
            return null
        }
    }

    fun getData(cid: CID): ByteArray? {
        try {
            ByteArrayOutputStream().use { outputStream ->
                getToOutputStream(outputStream, cid)
                return outputStream.toByteArray()
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
            return null
        }
    }

    fun loadData(cid: CID, progress: Progress): ByteArray? {
        if (!isDaemonRunning) {
            return null
        }
        try {
            ByteArrayOutputStream().use { outputStream ->
                val success = loadToOutputStream(outputStream, cid, progress)
                return if (success) {
                    outputStream.toByteArray()
                } else {
                    null
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
            return null
        }
    }

    fun gc() {
        try {
            node.repoGC()
        } catch (e: Throwable) {
            Log.e(TAG, e.message ?: e.javaClass.name)
        }
    }

    override fun error(message: String) {
        if (message != null && !message.isEmpty()) {
            Log.e(TAG, message)
        }
    }

    override fun info(message: String) {
        if (message != null && !message.isEmpty()) {
            Log.i(TAG, "" + message)
        }
    }

    override fun reachablePrivate() {
        reachable = Reachable.PRIVATE
    }

    override fun reachablePublic() {
        reachable = Reachable.PUBLIC
    }

    override fun reachableUnknown() {
        reachable = Reachable.UNKNOWN
    }

    override fun verbose(s: String) {
        Log.v(TAG, "" + s)
    }

    @Throws(Exception::class)
    fun getInputStream(cid: CID): InputStream {
        val reader = getReader(cid)
        return ReaderInputStream(reader)
    }

    val isDaemonRunning: Boolean
        get() = node.running

    fun isValidCID(multihash: String?): Boolean {
        return try {
            node.cidCheck(multihash)
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun isValidPID(multihash: String?): Boolean {
        return try {
            node.pidCheck(multihash)
            true
        } catch (e: Throwable) {
            false
        }
    }

    override fun seeding(amount: Long) {
        seeding += amount
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
//            events.seeding(seeding)
            Log.i(TAG, "Seeding Amount : $amount")
        }
    }

    override fun leeching(amount: Long) {
        leeching += amount
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
//            events.leeching(leeching)
            Log.i(TAG, "Leeching Amount : $amount")
        }
    }

    fun isEmptyDir(cid: CID): Boolean {
        return cid.cid == EMPTY_DIR_32 || cid.cid == EMPTY_DIR_58
    }

    fun isDir(doc: CID, closeable: Closeable): Boolean {
        val links: List<LinkInfo>? = getLinks(doc, closeable)
        return links != null && !links.isEmpty()
    }

    fun getSize(cid: CID, closeable: Closeable): Long {
        val links: List<LinkInfo>? = ls(cid, closeable)
        var size:Long = -1
        if (links != null) {
            for (info in links) {
                size += info.size
            }
        }
        return size.toLong()
    }

    fun numSwarmPeers(): Long {
        return if (!isDaemonRunning) {
            0
        } else node.numSwarmPeers()
    }

    interface Pusher {
        fun push(text: String, pid: String)
    }

    class ResolvedName(val sequence: Long, val hash: String)
    private class LoaderInputStream internal constructor(
        private val mLoader: Loader,
        progress: Progress
    ) :
        InputStream(), AutoCloseable {
        private val mProgress: Progress
        private val size: Long
        private var position = 0
        private var data: ByteArray? = null
        private var remember = 0
        private var totalRead = 0L
        override fun available(): Int {
            val size = mLoader.size
            return size.toInt()
        }

        @Throws(IOException::class)
        override fun read(): Int {
            return try {
                if (data == null) {
                    invalidate()
                    preLoad()
                }
                if (data == null) {
                    return -1
                }
                if (position < data!!.size) {
                    val value = data!![position].toInt()
                    position++
                    value and 0xff
                } else {
                    invalidate()
                    if (preLoad()) {
                        val value = data!![position].toInt()
                        position++
                        value and 0xff
                    } else {
                        -1
                    }
                }
            } catch (e: Throwable) {
                throw IOException(e)
            }
        }

        private fun invalidate() {
            position = 0
            data = null
        }

        @Throws(Exception::class)
        private fun preLoad(): Boolean {
            mLoader.load(4096, mProgress::isClosed)
            val read = mLoader.read.toInt()
            if (read > 0) {
                data = ByteArray(read)
                val values = mLoader.data
                System.arraycopy(values, 0, data, 0, read)
                totalRead += read.toLong()
                if (mProgress.doProgress()) {
                    if (size > 0) {
                        val percent = (totalRead * 100.0f / size).toInt()
                        if (remember < percent) {
                            remember = percent
                            mProgress.setProgress(percent)
                        }
                    }
                }
                return true
            }
            return false
        }

        override fun close() {
            try {
                mLoader.close()
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }

        init {
            mProgress = progress
            size = mLoader.size
        }
    }

    private class ReaderInputStream internal constructor(private val mReader: Reader) :
        InputStream(),
        AutoCloseable {
        private var position = 0
        private var data: ByteArray? = null
        override fun available(): Int {
            val size = mReader.size
            return size.toInt()
        }

        @Throws(IOException::class)
        override fun read(): Int {
            return try {
                if (data == null) {
                    invalidate()
                    preLoad()
                }
                if (data == null) {
                    return -1
                }
                if (position < data!!.size) {
                    val value = data!![position].toInt()
                    position++
                    value and 0xff
                } else {
                    invalidate()
                    if (preLoad()) {
                        val value = data!![position].toInt()
                        position++
                        value and 0xff
                    } else {
                        -1
                    }
                }
            } catch (e: Throwable) {
                throw IOException(e)
            }
        }

        private fun invalidate() {
            position = 0
            data = null
        }

        @Throws(Exception::class)
        private fun preLoad(): Boolean {
            mReader.load(4096)
            val read = mReader.read.toInt()
            if (read > 0) {
                data = ByteArray(read)
                val values = mReader.data
                System.arraycopy(values, 0, data, 0, read)
                return true
            }
            return false
        }

        override fun close() {
            try {
                mReader.close()
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }
    }

    private class CloseableInputStream internal constructor(
        private val mLoader: Loader,
        private val readTimeoutMillis: Long
    ) :
        InputStream(), AutoCloseable {
        private var position = 0
        private var data: ByteArray? = null
        override fun available(): Int {
            val size = mLoader.size
            return size.toInt()
        }

        @Throws(IOException::class)
        override fun read(): Int {
            return try {
                if (data == null) {
                    invalidate()
                    preLoad()
                }
                if (data == null) {
                    return -1
                }
                if (position < data!!.size) {
                    val value = data!![position].toInt()
                    position++
                    value and 0xff
                } else {
                    invalidate()
                    if (preLoad()) {
                        val value = data!![position].toInt()
                        position++
                        value and 0xff
                    } else {
                        -1
                    }
                }
            } catch (e: Throwable) {
                throw IOException(e)
            }
        }

        private fun invalidate() {
            position = 0
            data = null
        }

        @Throws(Exception::class)
        private fun preLoad(): Boolean {
            val start = System.currentTimeMillis()
            mLoader.load(
                4096
            ) { System.currentTimeMillis() - start > readTimeoutMillis }
            val read = mLoader.read.toInt()
            if (read > 0) {
                data = ByteArray(read)
                val values = mLoader.data
                System.arraycopy(values, 0, data, 0, read)
                return true
            }
            return false
        }

        override fun close() {
            try {
                mLoader.close()
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }
    }

    private class WriterStream(
        private val mInputStream: InputStream,
        private var mProgress: Progress,
        private val size: Long
    ) :
        lite.WriterStream {
        private val SIZE = 262144
        private val data = ByteArray(SIZE)
        private var progress = 0
        private var totalRead: Long = 0
        override fun data(): ByteArray {
            return data
        }

        @Throws(java.lang.Exception::class)
        override fun read(): Long {
            if (mProgress.isClosed) {
                throw java.lang.Exception("progress closed")
            }
            val read = mInputStream.read(data)
            totalRead += read.toLong()
            if (mProgress.doProgress()) {
                if (size > 0) {
                    val percent = (totalRead * 100.0f / size).toInt()
                    if (progress < percent) {
                        progress = percent
                        mProgress.setProgress(percent)
                    }
                }
            }
            return read.toLong()
        }

        override fun close(): Boolean {
            return mProgress.isClosed
        }
    }


    companion object {
        private val DNS_ADDRS: MutableList<String> = ArrayList()
        private val Bootstrap: List<String> = ArrayList(
            Arrays.asList(
                "/ip4/147.75.80.110/tcp/4001/p2p/QmbFgm5zan8P6eWWmeyfncR5feYEMPbht5b1FW1C37aQ7y",  // default relay  libp2p
                "/ip4/147.75.195.153/tcp/4001/p2p/QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei",  // default relay  libp2p
                "/ip4/147.75.70.221/tcp/4001/p2p/Qme8g49gm3q4Acp7xWBKg3nAa9fxZ1YmyDJdyGgoG6LsXh",  // default relay  libp2p
                "/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ" // mars.i.ipfs.io
            )
        )
        private const val EMPTY_DIR_58 = "QmUNLLsPACCz1vLxQVkXqqLX5R1X345qqfHbsf67hvA3Nn"
        private const val EMPTY_DIR_32 =
            "bafybeiczsscdsbs7ffqz55asqdf3smv6klcw3gofszvwlyarci47bgf354"
        private const val PREF_KEY = "prefKey"
        private const val PID_KEY = "pidKey"
        private const val PRIVATE_NETWORK_KEY = "privateNetworkKey"
        private const val PRIVATE_SHARING_KEY = "privateSharingKey"
        private const val HIGH_WATER_KEY = "highWaterKey"
        private const val LOW_WATER_KEY = "lowWaterKey"
        private const val GRACE_PERIOD_KEY = "gracePeriodKey"
        private const val STORAGE_DIRECTORY = "storageDirectoryKey"
        private const val SWARM_KEY = "swarmKey"
        private const val SWARM_PORT_KEY = "swarmPortKey"
        private const val PUBLIC_KEY = "publicKey"
        private const val AGENT_KEY = "agentKey"
        private const val PRIVATE_KEY = "privateKey"
        private val TAG = IPFS::class.java.simpleName
        private var INSTANCE: IPFS? = null
        fun getSwarmPort(context: Context): Int {
            val sharedPref = context.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
            return sharedPref.getInt(SWARM_PORT_KEY, 5001)
        }

        @Throws(IOException::class)
        fun copy(source: InputStream, sink: OutputStream): Long {
            var nread = 0L
            val buf = ByteArray(4096)
            var n: Int
            while (source.read(buf).also { n = it } > 0) {
                sink.write(buf, 0, n)
                nread += n.toLong()
            }
            return nread
        }

        fun getExternalStorageDirectory(context: Context): File? {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val dir = sharedPref.getString(STORAGE_DIRECTORY, "")
            Objects.requireNonNull(dir)
            return if (dir!!.isEmpty()) {
                null
            } else File(dir)
        }

        fun setExternalStorageDirectory(context: Context, file: File?) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            if (file == null) {
                editor.putString(STORAGE_DIRECTORY, "")
            } else {
                editor.putString(STORAGE_DIRECTORY, file.absolutePath)
            }
            editor.apply()
        }

        private fun setPublicKey(context: Context, key: String) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putString(PUBLIC_KEY, key)
            editor.apply()
        }

        private fun getStoredAgent(context: Context): String? {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getString(AGENT_KEY, "go-ipfs/0.8.0-dev/lite")
        }

        private fun setPrivateKey(context: Context, key: String) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putString(PRIVATE_KEY, key)
            editor.apply()
        }

        fun getSwarmKey(context: Context): String {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getString(SWARM_KEY, "")!!
        }

        fun setSwarmKey(context: Context, key: String) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putString(SWARM_KEY, key)
            editor.apply()
        }

        private fun getPublicKey(context: Context): String {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getString(PUBLIC_KEY, "")!!
        }

        private fun getPrivateKey(context: Context): String {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getString(PRIVATE_KEY, "")!!
        }

        private fun setPeerID(context: Context, peerID: String) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putString(PID_KEY, peerID)
            editor.apply()
        }

        fun getPeerID(context: Context): String? {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getString(PID_KEY, null)
        }

        fun setLowWater(context: Context, lowWater: Int) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putInt(LOW_WATER_KEY, lowWater)
            editor.apply()
        }

        private fun getLowWater(context: Context): Int {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getInt(LOW_WATER_KEY, 20)
        }

        fun setPrivateNetworkEnabled(context: Context, privateNetwork: Boolean) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putBoolean(PRIVATE_NETWORK_KEY, privateNetwork)
            editor.apply()
        }

        fun setPrivateSharingEnabled(context: Context, privateSharing: Boolean) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putBoolean(PRIVATE_SHARING_KEY, privateSharing)
            editor.apply()
        }

        fun isPrivateNetworkEnabled(context: Context): Boolean {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getBoolean(PRIVATE_NETWORK_KEY, false)
        }

        fun isPrivateSharingEnabled(context: Context): Boolean {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getBoolean(PRIVATE_SHARING_KEY, false)
        }

        private fun getGracePeriod(context: Context): String {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getString(GRACE_PERIOD_KEY, "30s")!!
        }

        fun setGracePeriod(context: Context, gracePeriod: String) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putString(GRACE_PERIOD_KEY, gracePeriod)
            editor.apply()
        }

        fun setHighWater(context: Context, highWater: Int) {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            val editor = sharedPref.edit()
            editor.putInt(HIGH_WATER_KEY, highWater)
            editor.apply()
        }

        private fun getHighWater(context: Context): Int {
            val sharedPref = context.getSharedPreferences(
                PREF_KEY, Context.MODE_PRIVATE
            )
            return sharedPref.getInt(HIGH_WATER_KEY, 40)
        }

        fun getInstance(context: Context): IPFS {
            if (INSTANCE == null) {
                synchronized(IPFS::class.java) {
                    if (INSTANCE == null) {
                        try {
                            INSTANCE = IPFS(context)
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    }
                }
            }
            return INSTANCE!!
        }

        private fun nextFreePort(): Int {
            var port = ThreadLocalRandom.current().nextInt(4001, 65535)
            while (true) {
                port = if (isLocalPortFree(port)) {
                    return port
                } else {
                    ThreadLocalRandom.current().nextInt(4001, 65535)
                }
            }
        }

        private fun isLocalPortFree(port: Int): Boolean {
            return try {
                ServerSocket(port).close()
                true
            } catch (e: IOException) {
                false
            }
        }

        fun logCacheDir(context: Context) {
            try {
                val files = context.cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        Log.e(TAG, "" + file.length() + " " + file.absolutePath)
                        if (file.isDirectory) {
                            val children = file.listFiles()
                            if (children != null) {
                                for (child in children) {
                                    Log.e(
                                        TAG,
                                        "" + child.length() + " " + child.absolutePath
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }

        fun logBaseDir(context: Context) {
            try {
                val files = context.filesDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        Log.w(TAG, "" + file.length() + " " + file.absolutePath)
                        if (file.isDirectory) {
                            logDir(file)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }

        fun logDir(file: File) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    Log.w(TAG, "" + child.length() + " " + child.absolutePath)
                    if (child.isDirectory) {
                        logDir(child)
                    }
                }
            }
        }

        private fun deleteFile(root: File) {
            try {
                if (root.isDirectory) {
                    val files = root.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (file.isDirectory) {
                                deleteFile(file)
                                val result = file.delete()
                                if (!result) {
                                    Log.e(TAG, "File " + file.name + " not deleted")
                                }
                            } else {
                                val result = file.delete()
                                if (!result) {
                                    Log.e(TAG, "File " + file.name + " not deleted")
                                }
                            }
                        }
                    }
                    val result = root.delete()
                    if (!result) {
                        Log.e(TAG, "File " + root.name + " not deleted")
                    }
                } else {
                    val result = root.delete()
                    if (!result) {
                        Log.e(TAG, "File " + root.name + " not deleted")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }

        fun cleanCacheDir(context: Context) {
            try {
                val files = context.cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory) {
                            deleteFile(file)
                            val result = file.delete()
                            if (!result) {
                                Log.e(TAG, "File not deleted.")
                            }
                        } else {
                            val result = file.delete()
                            if (!result) {
                                Log.e(TAG, "File not deleted.")
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, e.message ?: e.javaClass.name)
            }
        }

        private fun getBootstrap(refresh: Boolean): CopyOnWriteArrayList<String> {
            val bootstrap = CopyOnWriteArrayList(Bootstrap)
            if (refresh) {
                DNS_ADDRS.clear()
            }
            if (DNS_ADDRS.isEmpty()) {
                DNS_ADDRS.addAll(DnsAddrResolver.multiAddresses)
            }
            val dnsAddrs = CopyOnWriteArrayList(DNS_ADDRS)
            bootstrap.addAll(dnsAddrs)
            return bootstrap
        }
    }

    init {
        val dir = getExternalStorageDirectory(context)
//        events = Units.EVENTS.getInstance(context)
        if (dir == null) {
            baseDir = context.filesDir
            location = 0
        } else {
            val manager = context.getSystemService(Activity.STORAGE_SERVICE) as StorageManager
            Objects.requireNonNull(manager)
            val volume = manager.getStorageVolume(dir)
            Objects.requireNonNull(volume)
            location = volume.hashCode()
            baseDir = dir
        }
        val host = getPeerID(context)
        val init = host == null
        node = Node(this, baseDir!!.absolutePath)
        if (init) {
            node.identity()
            setPeerID(context, node.peerID)
            setPublicKey(context, node.publicKey)
            setPrivateKey(context, node.privateKey)
        } else {
            node.peerID = host
            node.privateKey = getPrivateKey(context)
            node.publicKey = getPublicKey(context)
        }

        /* addNoAnnounce
         "/ip4/10.0.0.0/ipcidr/8",
                "/ip4/100.64.0.0/ipcidr/10",
                "/ip4/169.254.0.0/ipcidr/16",
                "/ip4/172.16.0.0/ipcidr/12",
                "/ip4/192.0.0.0/ipcidr/24",
                "/ip4/192.0.0.0/ipcidr/29",
                "/ip4/192.0.0.8/ipcidr/32",
                "/ip4/192.0.0.170/ipcidr/32",
                "/ip4/192.0.0.171/ipcidr/32",
                "/ip4/192.0.2.0/ipcidr/24",
                "/ip4/192.168.0.0/ipcidr/16",
                "/ip4/198.18.0.0/ipcidr/15",
                "/ip4/198.51.100.0/ipcidr/24",
                "/ip4/203.0.113.0/ipcidr/24",
                "/ip4/240.0.0.0/ipcidr/4"
         */
        val swarmKey = getSwarmKey(context)
        if (!swarmKey.isEmpty()) {
            node.swarmKey = swarmKey.toByteArray()
            node.enablePrivateNetwork = isPrivateNetworkEnabled(context)
        }
        node.agent = getStoredAgent(context)
        node.pushing = false
        node.port = getSwarmPort(context).toLong()
        node.gracePeriod = getGracePeriod(context)
        node.highWater = getHighWater(context).toLong()
        node.lowWater = getLowWater(context).toLong()
        node.openDatabase()
    }
}