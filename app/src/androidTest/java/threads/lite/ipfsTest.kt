package threads.lite

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import threads.lite.core.TimeoutCloseable
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class IpfsTest {

    private val TAG = IpfsTest::class.java.simpleName
    private var context: Context = ApplicationProvider.getApplicationContext()


    @Throws(IOException::class)
    fun createCacheFile(): File {
        return File.createTempFile("temp", ".io.ipfs.cid", context.cacheDir)
    }

    @Test
    fun peerIdIsACid() {
        val ipfs = TestEnv.getTestInstance(context)

        val pid = ipfs.peerID
        assertThat("Pid ${pid} is not a CID", ipfs.isValidCID(pid.toBase32()))
    }

    @Test
    fun publishStoresStuffAtPid() {
        val timeout = 30L
        val ipfs = TestEnv.getTestInstance(context)
        val pid = ipfs.peerID
        val content = "Some text"

        // Store our stuff.
        val cid = ipfs.storeText(content)
        Log.i(TAG, "Stored content at ${cid.String()}")

        // Create / update the BW directory structure
        val versionDir = ipfs.createEmptyDir()!!
        Log.i(TAG, "Version dir cid: ${versionDir.String()}")
        val versionCid = ipfs.addLinkToDir(versionDir, "identity", cid)!!
        Log.i(TAG, "Linked identity -> version dir")

        val bwDir = ipfs.createEmptyDir()!!
        Log.i(TAG, "BW dir cid: ${bwDir.String()}")

        val bwCid = ipfs.addLinkToDir(bwDir, "0.2", versionCid)!!
        Log.i(TAG, "Linked bw -> version")

        val root = ipfs.createEmptyDir()!!
        Log.i(TAG, "Root dir cid: ${root.String()}")
        val rootCid = ipfs.addLinkToDir(root, "bw", bwCid)!!
        Log.i(TAG, "Linked root -> bw")
        Log.i(TAG, "Created Bailiwick structure. Publishing...")

        // Finally, publish the baseDir to IPNS
        ipfs.publishName(rootCid, 1, TimeoutCloseable(timeout))
        Log.i(TAG, "Published Name")

        // Now, try to get back to ipns/peerid/bw/0.1/identity
        val ipnsRecord = ipfs.resolveName(pid.toBase32(), 0, TimeoutCloseable(timeout))!!
        val link = IPFS.IPFS_PATH + ipnsRecord.hash + "/bw/0.2/identity"
        Log.i(TAG, "Looking for node at: $link")

        val node = ipfs.resolveNode(link, TimeoutCloseable(timeout))!!
        Log.i(TAG, "Resolved node to ${node.cid.String()}")

        val output = ipfs.getText(node.cid, TimeoutCloseable(timeout))
        Log.i(TAG, "output: $output")
        assertThat("output was not equal to content: $output != $content ", output == content)
    }
}

internal object TestEnv {
    private val bootstrap = AtomicBoolean(false)
    fun isConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return (capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    }

    fun getTestInstance(context: Context): IPFS {
        val ipfs = IPFS.getInstance(context)
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val linkProperties = connectivityManager.getLinkProperties(network)
        if (linkProperties != null) {
            val interfaceName = linkProperties.interfaceName
            ipfs.updateNetwork(interfaceName!!)
        }
        ipfs.clearDatabase()
        ipfs.relays(10)
        if (!bootstrap.getAndSet(true)) {
            ipfs.bootstrap()
        }
        ipfs.reset()
        System.gc()
        return ipfs
    }
}
