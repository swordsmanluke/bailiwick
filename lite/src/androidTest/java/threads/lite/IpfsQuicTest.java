package threads.lite;

import static junit.framework.TestCase.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.luminis.quic.QuicConnection;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import threads.lite.cid.Multiaddr;
import threads.lite.cid.PeerId;

@SuppressWarnings("SpellCheckingInspection")
@RunWith(AndroidJUnit4.class)
public class IpfsQuicTest {


    private static final String TAG = IpfsQuicTest.class.getSimpleName();
    private static Context context;

    @BeforeClass
    public static void setup() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void test_1() throws Exception {

        IPFS ipfs = TestEnv.getTestInstance(context);

        PeerId peerId = PeerId.fromBase58("QmNnooDu7bfjPFoTZYxMNLWUQJyrVwtbZg5gBMjTezGAJN");
        Multiaddr multiaddr = new Multiaddr("/ip4/147.75.109.213/udp/4001/quic");
        ipfs.addMultiAddress(peerId, multiaddr);

        QuicConnection conn = ipfs.connect(peerId, IPFS.MIN_STREAMS,
                false, false);
        assertNotNull(conn);

        conn.close();

    }

}


