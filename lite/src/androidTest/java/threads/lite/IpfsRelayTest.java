package threads.lite;


import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import identify.pb.IdentifyOuterClass;
import threads.lite.bitswap.BitSwapMessage;
import threads.lite.cid.Cid;
import threads.lite.cid.PeerId;
import threads.lite.core.TimeoutCloseable;
import threads.lite.format.Block;
import threads.lite.host.PeerInfo;
import threads.lite.ident.IdentityService;
import threads.lite.relay.RelayConnection;
import threads.lite.utils.DataHandler;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsRelayTest {
    private static final String TAG = IpfsRelayTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }


    @Test
    public void test_relay_connect() throws InterruptedException {
        IPFS ipfs = TestEnv.getTestInstance(context);

        Thread.sleep(5000);
        PeerId relay = PeerId.fromBase58("QmW9m57aiBDHAkKj9nmFSEn7ZqrcF1fZS4bipsTCHburei");
        QuicConnection conn;

        ConcurrentHashMap<PeerId, QuicConnection> relays = ipfs.getRelays();

        conn = relays.get(relay);
        assertNotNull(conn);
        assertNotNull(conn.getRemoteAddress());

    }


    // @Test (not working, waiting for new relay + punchhole concept)
    public void test_findRelays() {
        IPFS ipfs = TestEnv.getTestInstance(context);


        long start = System.currentTimeMillis();

        try {
            AtomicBoolean find = new AtomicBoolean(false);

            ipfs.findProviders(peerId -> find.set(true), Cid.nsToCid(IPFS.RELAY_RENDEZVOUS),
                    new TimeoutCloseable(120));

            assertTrue(find.get());
        } catch (Throwable throwable) {
            fail();
        } finally {
            LogUtils.info(TAG, "Time " + (System.currentTimeMillis() - start));
        }
    }

    @Test
    public void test_relay_dialPeer() throws InterruptedException {
        IPFS ipfs = TestEnv.getTestInstance(context);

        Thread.sleep(5000);

        PeerId peerId = ipfs.getPeerID();// PeerId.fromBase58("12D3KooWLfmzMdAje4F6F6q68jYRatu1JQaz2KB4j8ambYahd1xh");

        RelayConnection conn = RelayConnection.createRandomRelayConnection(peerId, ipfs.getHost());
        try {
            QuicStream stream = conn.createStream(true);
            Objects.requireNonNull(stream);

            OutputStream outputStream = stream.getOutputStream();

            outputStream.write(DataHandler.writeToken(
                    IPFS.STREAM_PROTOCOL, IPFS.IDENTITY_PROTOCOL));
            outputStream.close();

            AtomicBoolean success = new AtomicBoolean(false);

            InputStream inputStream = stream.getInputStream(
                    TimeUnit.SECONDS.toMillis(IPFS.CONNECT_TIMEOUT));
            DataHandler.reading(inputStream, new HashSet<>(
                    Arrays.asList(IPFS.STREAM_PROTOCOL, IPFS.RELAY_PROTOCOL,
                            IPFS.IDENTITY_PROTOCOL)
            ), (content) -> {
                try {
                    success.set(true);
                    IdentifyOuterClass.Identify identify =
                            IdentifyOuterClass.Identify.parseFrom(content);
                    PeerInfo peerInfo = IdentityService.getPeerInfo(peerId, identify);
                    assertNotNull(peerInfo);

                    LogUtils.error(TAG, peerInfo.toString());
                } catch (Throwable throwable) {
                    fail();
                }

            }, IPFS.IDENTITY_STREAM_SIZE_LIMIT);

            Assert.assertTrue(success.get());

            success.set(false);


            stream = conn.createStream(true);
            Objects.requireNonNull(stream);

            outputStream = stream.getOutputStream();
            String data = "moin";


            ipfs.setPusher((connection, ctx) -> success.set(ctx.equals(data)));

            outputStream.write(DataHandler.writeToken(
                    IPFS.STREAM_PROTOCOL, IPFS.PUSH_PROTOCOL));
            outputStream.write(DataHandler.encode(data.getBytes()));
            outputStream.close();

            Thread.sleep(1000);
            Assert.assertTrue(success.get());


            Cid cid1 = ipfs.storeText("Hallo das ist ein relay Test 1");
            assertNotNull(cid1);
            Cid cid2 = ipfs.storeText("Hallo das ist ein relay Test 2");
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

        } catch (Throwable throwable) {
            LogUtils.error(TAG, throwable);
            fail();
        } finally {
            conn.close();
        }

    }
}
