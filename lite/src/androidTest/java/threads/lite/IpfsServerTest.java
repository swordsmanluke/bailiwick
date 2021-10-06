package threads.lite;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.luminis.quic.QuicConnection;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import threads.lite.bitswap.BitSwapMessage;
import threads.lite.cid.Cid;
import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;
import threads.lite.format.Block;
import threads.lite.host.PeerInfo;


@RunWith(AndroidJUnit4.class)
public class IpfsServerTest {

    private static final String TAG = IpfsServerTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void server_test() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Multiaddr multiaddr = new Multiaddr("/ip4/127.0.0.1" + "/udp/" + ipfs.getPort() + "/quic");


        PeerId host = ipfs.getPeerID();
        assertNotNull(host);

        // make a connection to yourself
        ipfs.getHost().addToAddressBook(host, Collections.singletonList(multiaddr), true);


        QuicConnection conn = ipfs.connect(host, IPFS.MAX_STREAMS, true, false);
        Objects.requireNonNull(conn);

        PeerInfo info = ipfs.getPeerInfo(host, conn);
        assertNotNull(info);
        assertEquals(info.getAgent(), IPFS.AGENT);
        assertNotNull(info.getObserved());

        String data = "moin";

        AtomicBoolean success = new AtomicBoolean(false);

        ipfs.setPusher((connection, content) -> success.set(content.equals(data)));

        ipfs.notify(conn, data);
        Thread.sleep(1000);

        assertTrue(success.get());

        Cid cid1 = ipfs.storeText("Hallo das ist ein Test 1");
        assertNotNull(cid1);
        Cid cid2 = ipfs.storeText("Hallo das ist ein Test 2");
        assertNotNull(cid2);

        List<BitSwapMessage> list = new ArrayList<>();
        ipfs.getHost().getBitSwap().sendWants(conn,
                Arrays.asList(cid1, cid2), list::add);

        assertEquals(list.size(), 1);
        List<Block> wanted = list.get(0).Blocks();
        assertEquals(wanted.size(), 2);
        Block block = wanted.get(0);
        Cid cmp = block.getCid();
        assertEquals(cid1, cmp);
        block = wanted.get(1);
        cmp = block.getCid();
        assertEquals(cid2, cmp);


    }

    private byte[] getRandomBytes(int number) {
        return RandomStringUtils.randomAlphabetic(number).getBytes();
    }

    @NonNull
    public File createCacheFile() throws IOException {
        return File.createTempFile("temp", ".io.ipfs.cid", context.getCacheDir());
    }

    @Test
    public void server_stress_test() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        Multiaddr multiaddr = new Multiaddr("/ip4/127.0.0.1" + "/udp/" + ipfs.getPort() + "/quic");


        PeerId host = ipfs.getPeerID();
        assertNotNull(host);

        // make a connection to yourself
        ipfs.getHost().addToAddressBook(host, Collections.singletonList(multiaddr), true);


        int packetSize = 10000;
        long maxData = 10000;
        File inputFile = createCacheFile();
        for (int i = 0; i < maxData; i++) {
            byte[] randomBytes = getRandomBytes(packetSize);
            FileServer.insertRecord(inputFile, i, packetSize, randomBytes);
        }

        LogUtils.error(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        Cid hash58Base = ipfs.storeFile(inputFile);
        assertNotNull(hash58Base);


        List<Cid> cids = ipfs.getBlocks(hash58Base);

        LogUtils.error(TAG, "Links " + cids.size());


        QuicConnection conn = ipfs.connect(host, IPFS.MAX_STREAMS,
                true, false);
        Objects.requireNonNull(conn);

        long time = System.currentTimeMillis();


        int counter = 0;

        for (Cid link : cids) {


            LogUtils.debug(TAG, "Link " + counter++ + " " + link.String());

            CompletableFuture<BitSwapMessage> done = new CompletableFuture<>();
            ipfs.getHost().getBitSwap().sendWants(conn,
                    Collections.singletonList(link), done::complete);

            BitSwapMessage msg = done.get(10, TimeUnit.SECONDS);

            List<Block> wanted = msg.Blocks();
            assertEquals(wanted.size(), 1);
            Block block = wanted.get(0);
            assertNotNull(block);
            assertEquals(link, block.getCid());

        }

        LogUtils.error(TAG, "Bytes : " + inputFile.length() / 1000 + "[kb]");

        LogUtils.error(TAG, "Time via " + (System.currentTimeMillis() - time) + " [ms]");

        inputFile.deleteOnExit();

    }


}